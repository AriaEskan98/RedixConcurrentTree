package com.ariae;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main application demonstrating the RadixTrieThreadSafeSortedTree implementation.
 *
 * This demo shows:
 * 1. Basic operations (get/put)
 * 2. Node splitting behavior with common prefixes
 * 3. Thread safety under concurrent load
 * 4. Performance characteristics
 * 5. Edge cases (empty keys, null values)
 */
public class App {

	public static void main(String[] args) {
		System.out.println("=== RadixTrie Thread-Safe Sorted Tree Demo ===\n");

		// Run all demonstrations
		demonstrateBasicOperations();
		demonstrateNodeSplitting();
		demonstrateEdgeCases();
		demonstrateConcurrentAccess();
		demonstratePerformance();

		System.out.println("\n=== Demo Complete ===");
	}

	/**
	 * Demonstrates basic get and put operations
	 */
	private static void demonstrateBasicOperations() {
		System.out.println("1. BASIC OPERATIONS");
		System.out.println("===================");

		RadixTrieThreadSafeSortedTree trie = new RadixTrieThreadSafeSortedTree();

		// Basic put/get
		trie.put("hello".getBytes(), "world".getBytes());
		trie.put("java".getBytes(), "programming".getBytes());
		trie.put("data".getBytes(), "structure".getBytes());

		System.out.println("Stored key-value pairs:");
		System.out.println("hello → " + getString(trie.get("hello".getBytes())));
		System.out.println("java → " + getString(trie.get("java".getBytes())));
		System.out.println("data → " + getString(trie.get("data".getBytes())));

		// Test non-existent key
		System.out.println("nonexistent → " + getString(trie.get("nonexistent".getBytes())));

		// Update existing key
		trie.put("hello".getBytes(), "updated_world".getBytes());
		System.out.println("hello (updated) → " + getString(trie.get("hello".getBytes())));

		System.out.println();
	}

	/**
	 * Demonstrates node splitting with common prefixes
	 */
	private static void demonstrateNodeSplitting() {
		System.out.println("2. NODE SPLITTING DEMONSTRATION");
		System.out.println("================================");

		RadixTrieThreadSafeSortedTree trie = new RadixTrieThreadSafeSortedTree();

		// Start with "hello"
		System.out.println("Step 1: Insert 'hello' → 'world'");
		trie.put("hello".getBytes(), "world".getBytes());
		System.out.println(trie.debugString());

		// Add "help" - this will cause node splitting
		System.out.println("Step 2: Insert 'help' → 'assistance' (causes split)");
		trie.put("help".getBytes(), "assistance".getBytes());
		System.out.println(trie.debugString());

		// Add "helicopter" - uses existing "hel" prefix
		System.out.println("Step 3: Insert 'helicopter' → 'aircraft' (uses existing prefix)");
		trie.put("helicopter".getBytes(), "aircraft".getBytes());
		System.out.println(trie.debugString());

		// Verify all keys work
		System.out.println("Verification:");
		System.out.println("hello → " + getString(trie.get("hello".getBytes())));
		System.out.println("help → " + getString(trie.get("help".getBytes())));
		System.out.println("helicopter → " + getString(trie.get("helicopter".getBytes())));

		System.out.println();
	}

	/**
	 * Demonstrates edge cases
	 */
	private static void demonstrateEdgeCases() {
		System.out.println("3. EDGE CASES");
		System.out.println("==============");

		RadixTrieThreadSafeSortedTree trie = new RadixTrieThreadSafeSortedTree();

		// Empty key
		System.out.println("Testing empty key:");
		byte[] emptyKey = new byte[0];
		trie.put(emptyKey, "empty_value".getBytes());
		System.out.println("'' → " + getString(trie.get(emptyKey)));

		// Null value
		System.out.println("\nTesting null value:");
		trie.put("null_key".getBytes(), null);
		byte[] nullResult = trie.get("null_key".getBytes());
		System.out.println("null_key → " + (nullResult == null ? "null" : "not null"));

		// Single character keys
		System.out.println("\nTesting single character keys:");
		trie.put("a".getBytes(), "alpha".getBytes());
		trie.put("b".getBytes(), "beta".getBytes());
		System.out.println("a → " + getString(trie.get("a".getBytes())));
		System.out.println("b → " + getString(trie.get("b".getBytes())));

		// Binary data (not just text)
		System.out.println("\nTesting binary data:");
		byte[] binaryKey = {0x01, 0x02, 0x03};
		byte[] binaryValue = {(byte)0xFF, (byte)0xFE, (byte)0xFD};
		trie.put(binaryKey, binaryValue);
		byte[] retrievedBinary = trie.get(binaryKey);
		System.out.println("Binary key → Binary value retrieved: " + (retrievedBinary != null));

		System.out.println();
	}

	/**
	 * Demonstrates thread safety under concurrent load
	 */
	private static void demonstrateConcurrentAccess() {
		System.out.println("4. CONCURRENT ACCESS TEST");
		System.out.println("==========================");

		RadixTrieThreadSafeSortedTree trie = new RadixTrieThreadSafeSortedTree();
		final int NUM_THREADS = 8;
		final int OPERATIONS_PER_THREAD = 1000;

		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch finishLatch = new CountDownLatch(NUM_THREADS);
		AtomicInteger successfulOps = new AtomicInteger(0);

		// Create writer threads
		for (int t = 0; t < NUM_THREADS / 2; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					startLatch.await(); // Wait for all threads to be ready

					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						String key = "thread_" + threadId + "_key_" + i;
						String value = "thread_" + threadId + "_value_" + i;
						trie.put(key.getBytes(), value.getBytes());
						successfulOps.incrementAndGet();
					}
				} catch (Exception e) {
					System.err.println("Writer thread " + threadId + " error: " + e.getMessage());
				} finally {
					finishLatch.countDown();
				}
			});
		}

		// Create reader threads
		for (int t = NUM_THREADS / 2; t < NUM_THREADS; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					startLatch.await(); // Wait for all threads to be ready

					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						// Read from any writer thread's data
						int readerThread = i % (NUM_THREADS / 2);
						String key = "thread_" + readerThread + "_key_" + (i % 100);
						trie.get(key.getBytes()); // Just perform the read
						successfulOps.incrementAndGet();
					}
				} catch (Exception e) {
					System.err.println("Reader thread " + threadId + " error: " + e.getMessage());
				} finally {
					finishLatch.countDown();
				}
			});
		}

		System.out.println("Starting " + NUM_THREADS + " threads (" + (NUM_THREADS/2) + " writers, " + (NUM_THREADS/2) + " readers)...");

		long startTime = System.currentTimeMillis();
		startLatch.countDown(); // Start all threads

		try {
			boolean completed = finishLatch.await(30, TimeUnit.SECONDS);
			long endTime = System.currentTimeMillis();

			if (completed) {
				System.out.println("✓ Concurrent test completed successfully!");
				System.out.println("Total operations: " + successfulOps.get());
				System.out.println("Time taken: " + (endTime - startTime) + "ms");
				System.out.println("Operations per second: " + (successfulOps.get() * 1000L / (endTime - startTime)));
			} else {
				System.out.println("✗ Concurrent test timed out!");
			}
		} catch (InterruptedException e) {
			System.out.println("✗ Concurrent test interrupted!");
		}

		executor.shutdown();
		System.out.println();
	}

	/**
	 * Demonstrates performance characteristics
	 */
	private static void demonstratePerformance() {
		System.out.println("5. PERFORMANCE DEMONSTRATION");
		System.out.println("=============================");

		RadixTrieThreadSafeSortedTree trie = new RadixTrieThreadSafeSortedTree();

		// Test with different data sizes
		int[] dataSizes = {1000, 10000, 50000};

		for (int dataSize : dataSizes) {
			System.out.println("Testing with " + dataSize + " entries:");

			// Insertion performance
			long startTime = System.currentTimeMillis();
			for (int i = 0; i < dataSize; i++) {
				String key = "performance_key_" + String.format("%010d", i);
				String value = "performance_value_" + i;
				trie.put(key.getBytes(), value.getBytes());
			}
			long insertTime = System.currentTimeMillis() - startTime;

			// Lookup performance
			startTime = System.currentTimeMillis();
			for (int i = 0; i < dataSize; i++) {
				String key = "performance_key_" + String.format("%010d", i);
				trie.get(key.getBytes());
			}
			long lookupTime = System.currentTimeMillis() - startTime;

			System.out.println("  Insert time: " + insertTime + "ms (" +
					(dataSize * 1000L / insertTime) + " ops/sec)");
			System.out.println("  Lookup time: " + lookupTime + "ms (" +
					(dataSize * 1000L / lookupTime) + " ops/sec)");
		}

		System.out.println();
	}

	/**
	 * Helper method to safely convert byte array to string
	 */
	private static String getString(byte[] bytes) {
		return bytes != null ? new String(bytes) : "null";
	}
}