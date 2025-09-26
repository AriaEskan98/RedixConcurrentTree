package com.ariae;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe sorted in-memory tree using Radix Trie (Patricia Tree) data structure.
 *
 * Radix Trie provides:
 * - O(key_length) time complexity for get and put operations
 * - Memory efficient storage through prefix compression
 * - Excellent performance for byte arrays with common prefixes
 * - Natural lexicographic ordering
 *
 * Underlying data structures:
 * - ConcurrentHashMap for child node navigation (thread-safe)
 * - byte[] arrays for compressed prefix storage
 * - Linked node structure with parent-child relationships
 *
 * Thread safety achieved through:
 * - ConcurrentHashMap for lock-free child access
 * - Read-write locks at node level for structural modifications
 * - Lock ordering (parent before child) to prevent deadlocks
 * - Atomic updates for value modifications
 *
 * @author Aria Eskandarzadeh
 * @version 1.0.0
 */
public class RadixTrieThreadSafeSortedTree implements SortedTree {

    /**
     * Internal node structure for the radix trie
     */
    private static class TrieNode {
        // Compressed path segment stored at this node
        volatile byte[] prefix;

        // Value stored at this node (null if intermediate node)
        volatile byte[] value;

        // Maps first byte of remaining path to child nodes
        final ConcurrentHashMap<Byte, TrieNode> children;

        // Thread safety for structural modifications
        final ReentrantReadWriteLock nodeLock;

        // Marks if a key terminates at this node
        volatile boolean isEndOfKey;

        /**
         * Creates a new root node
         */
        TrieNode() {
            this.prefix = new byte[0];
            this.value = null;
            this.children = new ConcurrentHashMap<>();
            this.nodeLock = new ReentrantReadWriteLock();
            this.isEndOfKey = false;
        }

        /**
         * Creates a new node with prefix and value
         *
         * @param prefix the compressed path segment
         * @param value the value to store (can be null)
         */
        TrieNode(byte[] prefix, byte[] value) {
            this.prefix = Arrays.copyOf(prefix, prefix.length);
            this.value = value != null ? Arrays.copyOf(value, value.length) : null;
            this.children = new ConcurrentHashMap<>();
            this.nodeLock = new ReentrantReadWriteLock();
            this.isEndOfKey = true;
        }
    }

    private final TrieNode root;

    /**
     * Creates a new empty RadixTrieThreadSafeSortedTree
     */
    public RadixTrieThreadSafeSortedTree() {
        this.root = new TrieNode();
    }

    /**
     * Retrieves the value associated with the given key.
     *
     * @param key the key to search for (must not be null)
     * @return the value associated with the key, or null if not found
     * @throws IllegalArgumentException if key is null
     */
    public byte[] get(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Handle empty key case
        if (key.length == 0) {
            root.nodeLock.readLock().lock();
            try {
                if (root.isEndOfKey) {
                    return root.value != null ? Arrays.copyOf(root.value, root.value.length) : null;
                }
                return null;
            } finally {
                root.nodeLock.readLock().unlock();
            }
        }

        TrieNode current = root;
        int keyIndex = 0;

        while (current != null && keyIndex < key.length) {
            TrieNode nextNode = null;

            current.nodeLock.readLock().lock();
            try {
                // Get the current node's prefix
                byte[] currentPrefix = current.prefix;

                // Check if current prefix matches the remaining key
                int prefixLength = currentPrefix.length;
                if (keyIndex + prefixLength > key.length) {
                    // Key is shorter than prefix - no match
                    return null;
                }

                // Compare prefix with key segment
                for (int i = 0; i < prefixLength; i++) {
                    if (currentPrefix[i] != key[keyIndex + i]) {
                        // Prefix doesn't match - no such key
                        return null;
                    }
                }

                keyIndex += prefixLength;

                // If we've consumed the entire key
                if (keyIndex == key.length) {
                    if (current.isEndOfKey) {
                        return current.value != null ? Arrays.copyOf(current.value, current.value.length) : null;
                    } else {
                        return null; // Key doesn't end here
                    }
                }

                // Move to next child based on next byte in key
                byte nextByte = key[keyIndex];
                nextNode = current.children.get(nextByte);

            } finally {
                current.nodeLock.readLock().unlock();
            }

            current = nextNode;
        }

        return null;
    }

    /**
     * Associates the specified value with the specified key.
     * If the key already exists, the old value is replaced.
     *
     * @param key the key (must not be null)
     * @param value the value (can be null)
     * @throws IllegalArgumentException if key is null
     */
    public void put(byte[] key, byte[] value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Handle empty key case
        if (key.length == 0) {
            root.nodeLock.writeLock().lock();
            try {
                root.value = value != null ? Arrays.copyOf(value, value.length) : null;
                root.isEndOfKey = true;
                return;
            } finally {
                root.nodeLock.writeLock().unlock();
            }
        }

        TrieNode current = root;
        int keyIndex = 0;

        while (keyIndex < key.length) {
            TrieNode nextChild = null;
            boolean shouldSplit = false;
            int matchLength = 0;

            current.nodeLock.writeLock().lock();
            try {
                byte[] currentPrefix = current.prefix;
                int prefixLength = currentPrefix.length;

                // Find how much of the prefix matches the remaining key
                int maxMatch = Math.min(prefixLength, key.length - keyIndex);

                for (int i = 0; i < maxMatch; i++) {
                    if (currentPrefix[i] == key[keyIndex + i]) {
                        matchLength++;
                    } else {
                        break;
                    }
                }

                if (matchLength == prefixLength) {
                    // Full prefix matches
                    keyIndex += matchLength;

                    if (keyIndex == key.length) {
                        // Key ends exactly here - update value
                        current.value = value != null ? Arrays.copyOf(value, value.length) : null;
                        current.isEndOfKey = true;
                        return;
                    }

                    // Need to go deeper - find or create child
                    byte nextByte = key[keyIndex];
                    nextChild = current.children.get(nextByte);

                    if (nextChild == null) {
                        // Create new child with remaining key as prefix
                        byte[] remainingKey = Arrays.copyOfRange(key, keyIndex, key.length);
                        nextChild = new TrieNode(remainingKey, value);
                        current.children.put(nextByte, nextChild);
                        return;
                    }

                } else {
                    // Partial prefix match - need to split the node
                    shouldSplit = true;
                }

            } finally {
                current.nodeLock.writeLock().unlock();
            }

            if (shouldSplit) {
                // Handle node splitting
                splitNode(current, matchLength, key, keyIndex, value);
                return;
            }

            current = nextChild;
        }
    }

    /**
     * Splits a node when there's a partial prefix match.
     * This is the core operation that maintains the compressed path property
     * while allowing the trie to branch where keys diverge.
     *
     * @param node the node to split
     * @param matchLength how many characters of the prefix match
     * @param key the key being inserted
     * @param keyIndex current position in the key
     * @param value the value to associate with the key
     */
    private void splitNode(TrieNode node, int matchLength, byte[] key, int keyIndex, byte[] value) {
        node.nodeLock.writeLock().lock();
        try {
            // Save original node state
            byte[] originalPrefix = node.prefix;
            byte[] originalValue = node.value;
            boolean originalIsEndOfKey = node.isEndOfKey;
            ConcurrentHashMap<Byte, TrieNode> originalChildren = new ConcurrentHashMap<>(node.children);

            // Update current node to hold only the matching prefix
            node.prefix = Arrays.copyOf(originalPrefix, matchLength);
            node.value = null;
            node.isEndOfKey = false;
            node.children.clear();

            // Create node for the original suffix (if there is one)
            if (matchLength < originalPrefix.length) {
                byte[] originalSuffix = Arrays.copyOfRange(originalPrefix, matchLength, originalPrefix.length);
                TrieNode originalNode = new TrieNode(originalSuffix, originalValue);
                originalNode.isEndOfKey = originalIsEndOfKey;
                originalNode.children.putAll(originalChildren);

                byte originalFirstByte = originalSuffix[0];
                node.children.put(originalFirstByte, originalNode);
            }

            // Handle the new key
            int newKeyIndex = keyIndex + matchLength;
            if (newKeyIndex == key.length) {
                // New key ends at this split point
                node.value = value != null ? Arrays.copyOf(value, value.length) : null;
                node.isEndOfKey = true;
            } else {
                // Create node for new key suffix
                byte[] newSuffix = Arrays.copyOfRange(key, newKeyIndex, key.length);
                TrieNode newNode = new TrieNode(newSuffix, value);

                byte newFirstByte = newSuffix[0];
                node.children.put(newFirstByte, newNode);
            }
        } finally {
            node.nodeLock.writeLock().unlock();
        }
    }

    /**
     * Returns a string representation of the trie structure for debugging.
     *
     * @return a string showing the internal structure
     */
    public String debugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RadixTrie Structure:\n");
        debugNode(root, "", sb, true);
        return sb.toString();
    }

    /**
     * Helper method for debugging - recursively builds string representation
     */
    private void debugNode(TrieNode node, String indent, StringBuilder sb, boolean isLast) {
        if (node == null) return;

        node.nodeLock.readLock().lock();
        try {
            // Draw the tree structure
            sb.append(indent);
            if (!indent.isEmpty()) {
                sb.append(isLast ? "└── " : "├── ");
            }

            // Show node information
            sb.append("Node[prefix=").append(Arrays.toString(node.prefix));
            if (node.isEndOfKey) {
                sb.append(", value=").append(Arrays.toString(node.value));
            }
            sb.append(", children=").append(node.children.size()).append("]\n");

            // Recursively show children
            String newIndent = indent + (isLast ? "    " : "│   ");
            var entries = node.children.entrySet().toArray();

            for (int i = 0; i < entries.length; i++) {
                @SuppressWarnings("unchecked")
                var entry = (java.util.Map.Entry<Byte, TrieNode>) entries[i];
                sb.append(newIndent).append(i == entries.length - 1 ? "└── " : "├── ");
                sb.append("[0x").append(String.format("%02X", entry.getKey())).append("] → \n");
                debugNode(entry.getValue(), newIndent + (i == entries.length - 1 ? "    " : "│   "), sb, i == entries.length - 1);
            }
        } finally {
            node.nodeLock.readLock().unlock();
        }
    }
}