# RedixConcurrentTree
A Thread Safe Sorted Tree using the Redix Trie Data Structure



## Features
- O(key_length) get/put operations
- Thread-safe with fine-grained locking
- Memory-efficient prefix compression
- Optimized for byte array keys

## Usage
```java
RadixTrieThreadSafeSortedTree tree = new RadixTrieThreadSafeSortedTree();
tree.put("hello".getBytes(), "world".getBytes());
byte[] value = tree.get("hello".getBytes());
```

## Building
```bash
mvn clean package
```

## Testing  
```bash
mvn test
```
