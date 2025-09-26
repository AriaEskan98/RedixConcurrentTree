package com.ariae;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.Arrays;

class RadixTrieTest {

	private RadixTrieThreadSafeSortedTree trie;

	@BeforeEach
	void setUp() {
		trie = new RadixTrieThreadSafeSortedTree();
	}

	@Test
	@DisplayName("Basic put and get operations")
	void testBasicOperations() {
		byte[] key = "hello".getBytes();
		byte[] value = "world".getBytes();

		trie.put(key, value);
		byte[] result = trie.get(key);

		assertArrayEquals(value, result);
	}

	@Test
	@DisplayName("Node splitting with common prefixes")
	void testNodeSplitting() {
		trie.put("hello".getBytes(), "world".getBytes());
		trie.put("help".getBytes(), "assistance".getBytes());
		trie.put("helicopter".getBytes(), "aircraft".getBytes());

		assertArrayEquals("world".getBytes(), trie.get("hello".getBytes()));
		assertArrayEquals("assistance".getBytes(), trie.get("help".getBytes()));
		assertArrayEquals("aircraft".getBytes(), trie.get("helicopter".getBytes()));
	}

	@Test
	@DisplayName("Concurrent access test")
	void testConcurrentAccess() throws InterruptedException {
		final int NUM_THREADS = 10;
		final int OPS_PER_THREAD = 1000;

		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		CountDownLatch latch = new CountDownLatch(NUM_THREADS);

		// Writers
		for (int t = 0; t < NUM_THREADS / 2; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					for (int i = 0; i < OPS_PER_THREAD; i++) {
						String key = "key_" + threadId + "_" + i;
						String value = "value_" + threadId + "_" + i;
						trie.put(key.getBytes(), value.getBytes());
					}
				} finally {
					latch.countDown();
				}
			});
		}

		// Readers
		for (int t = NUM_THREADS / 2; t < NUM_THREADS; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					for (int i = 0; i < OPS_PER_THREAD; i++) {
						String key = "key_" + (threadId % (NUM_THREADS / 2)) + "_" + i;
						trie.get(key.getBytes()); // Just ensure no exceptions
					}
				} finally {
					latch.countDown();
				}
			});
		}

		assertTrue(latch.await(30, TimeUnit.SECONDS));
		executor.shutdown();
	}

	@Test
	@DisplayName("Null key handling")
	void testNullKey() {
		assertThrows(IllegalArgumentException.class, () -> trie.get(null));
		assertThrows(IllegalArgumentException.class, () -> trie.put(null, "value".getBytes()));
	}

	@Test
	@DisplayName("Empty key handling")
	void testEmptyKey() {
		byte[] emptyKey = new byte[0];
		byte[] value = "empty".getBytes();

		trie.put(emptyKey, value);
		assertArrayEquals(value, trie.get(emptyKey));
	}
}