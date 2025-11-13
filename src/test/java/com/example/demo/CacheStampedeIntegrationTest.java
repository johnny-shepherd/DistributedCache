package com.example.demo;

import com.example.demo.config.TestRedisConfiguration;
import com.example.demo.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestRedisConfiguration.class)
class CacheStampedeIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        cacheManager.getCacheNames().forEach(cacheName -> 
            cacheManager.getCache(cacheName).clear()
        );
        
        // Reset execution counter
        bookService.resetExecutionCounter();
    }

    @Test
    void testCacheStampedePrevention_SameKey_OnlyOneExecution() throws Exception {
        // Given: Empty cache for a specific ISBN
        String isbn = "test-isbn-stampede-123";
        int numberOfThreads = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // When: 10 threads simultaneously request the same ISBN
        Instant startTime = Instant.now();
        
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // All threads hit the cache at the same time
                    String result = bookService.getBookByIsbn(isbn);
                    completionLatch.countDown();
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        // Release all threads at once
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertThat(completionLatch.await(15, TimeUnit.SECONDS))
            .as("All threads should complete within 15 seconds")
            .isTrue();
        
        Instant endTime = Instant.now();
        long totalTimeSeconds = Duration.between(startTime, endTime).getSeconds();

        // Then: Verify only ONE execution occurred
        assertThat(bookService.getExecutionCount())
            .as("Only one thread should have executed the service method")
            .isEqualTo(1);

        // Then: Verify all threads received the same result
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            results.add(future.get());
        }
        
        assertThat(results)
            .as("All threads should receive the same cached result")
            .hasSize(numberOfThreads)
            .allMatch(result -> result.contains(isbn));

        // Then: Verify timing - should be ~5 seconds (not 50 seconds)
        assertThat(totalTimeSeconds)
            .as("Total execution time should be close to 5 seconds (one execution), not 50 seconds (ten executions)")
            .isGreaterThanOrEqualTo(4)
            .isLessThanOrEqualTo(10);

        executor.shutdown();
    }

    @Test
    void testDifferentCacheKeys_ExecuteIndependently() throws Exception {
        // Given: Empty cache
        int numberOfThreads = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // When: 5 threads request different ISBNs simultaneously
        for (int i = 0; i < numberOfThreads; i++) {
            final String isbn = "test-isbn-different-" + i;
            
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    String result = bookService.getBookByIsbn(isbn);
                    completionLatch.countDown();
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        startLatch.countDown();
        
        assertThat(completionLatch.await(15, TimeUnit.SECONDS))
            .as("All threads should complete")
            .isTrue();

        // Then: Verify 5 executions occurred (one per unique ISBN)
        assertThat(bookService.getExecutionCount())
            .as("Each unique ISBN should result in one execution")
            .isEqualTo(numberOfThreads);

        // Then: Verify all threads received results
        for (CompletableFuture<String> future : futures) {
            assertThat(future.get()).isNotNull();
        }

        executor.shutdown();
    }

    @Test
    void testCacheHit_NoExecution() throws Exception {
        // Given: Cache is populated with a book
        String isbn = "test-isbn-cache-hit-456";
        String firstResult = bookService.getBookByIsbn(isbn);
        
        assertThat(bookService.getExecutionCount())
            .as("First call should execute the method")
            .isEqualTo(1);

        // When: Same ISBN is requested again
        Instant startTime = Instant.now();
        String secondResult = bookService.getBookByIsbn(isbn);
        Instant endTime = Instant.now();
        
        long durationMillis = Duration.between(startTime, endTime).toMillis();

        // Then: No additional execution should occur
        assertThat(bookService.getExecutionCount())
            .as("Second call should use cache, not execute method again")
            .isEqualTo(1);

        // Then: Results should be identical
        assertThat(secondResult)
            .as("Cached result should match original result")
            .isEqualTo(firstResult);

        // Then: Second call should be much faster (< 100ms vs 5000ms)
        assertThat(durationMillis)
            .as("Cache hit should be nearly instant (< 100ms)")
            .isLessThan(100);
    }

    @Test
    void testTimingVerification_ProvesSingleExecution() throws Exception {
        // Given: Empty cache
        String isbn = "test-isbn-timing-789";
        int numberOfThreads = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // When: Multiple threads request same ISBN
        Instant startTime = Instant.now();
        
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 
                bookService.getBookByIsbn(isbn), executor
            );
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        
        Instant endTime = Instant.now();
        long totalTimeSeconds = Duration.between(startTime, endTime).getSeconds();

        // Then: If all threads executed independently, it would take ~50 seconds
        // With cache stampede prevention, only ~5 seconds
        assertThat(totalTimeSeconds)
            .as("Total time should prove only one 5-second execution, not ten")
            .isLessThan(15); // Conservative upper bound

        assertThat(bookService.getExecutionCount())
            .as("Only one execution should have occurred")
            .isEqualTo(1);

        executor.shutdown();
    }

    @Test
    void testCacheStampedePrevention_WithKeyGenerator_OnlyOneExecution() throws Exception {
        // Given: Empty cache for getAllBooks() which uses customKeyGenerator
        int numberOfThreads = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // When: 10 threads simultaneously call getAllBooks() which uses KeyGenerator
        Instant startTime = Instant.now();
        
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // All threads hit the cache at the same time
                    String result = bookService.getAllBooks();
                    completionLatch.countDown();
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        // Release all threads at once
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertThat(completionLatch.await(15, TimeUnit.SECONDS))
            .as("All threads should complete within 15 seconds")
            .isTrue();
        
        Instant endTime = Instant.now();
        long totalTimeSeconds = Duration.between(startTime, endTime).getSeconds();

        // Then: Verify only ONE execution occurred (cache stampede was prevented)
        assertThat(bookService.getExecutionCount())
            .as("Only one thread should have executed getAllBooks() with KeyGenerator")
            .isEqualTo(1);

        // Then: Verify all threads received the same result
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            results.add(future.get());
        }
        
        assertThat(results)
            .as("All threads should receive the same cached result")
            .hasSize(numberOfThreads)
            .allMatch(result -> result.contains("All Books Data"));

        // Then: Verify timing - should be ~3 seconds (not 30 seconds)
        assertThat(totalTimeSeconds)
            .as("Total execution time should be close to 3 seconds (one execution), not 30 seconds (ten executions)")
            .isGreaterThanOrEqualTo(2)
            .isLessThanOrEqualTo(10);

        executor.shutdown();
    }

    @Test
    void testCacheHit_WithKeyGenerator_NoExecution() throws Exception {
        // Given: Cache is populated by calling getAllBooks() once
        String firstResult = bookService.getAllBooks();
        
        assertThat(bookService.getExecutionCount())
            .as("First call should execute the method")
            .isEqualTo(1);

        // When: getAllBooks() is called again
        Instant startTime = Instant.now();
        String secondResult = bookService.getAllBooks();
        Instant endTime = Instant.now();
        
        long durationMillis = Duration.between(startTime, endTime).toMillis();

        // Then: No additional execution should occur
        assertThat(bookService.getExecutionCount())
            .as("Second call should use cache with KeyGenerator, not execute method again")
            .isEqualTo(1);

        // Then: Results should be identical
        assertThat(secondResult)
            .as("Cached result should match original result")
            .isEqualTo(firstResult);

        // Then: Second call should be much faster (< 100ms vs 3000ms)
        assertThat(durationMillis)
            .as("Cache hit with KeyGenerator should be nearly instant (< 100ms)")
            .isLessThan(100);
    }

    @Test
    void testValidation_BothKeyAndKeyGenerator_ThrowsException() {
        // When: Method with both key and keyGenerator is called
        // Then: Should throw IllegalStateException with appropriate message
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> bookService.invalidMethodBothKeyAndGenerator("test")
        ))
            .hasMessageContaining("Cannot specify both 'key' and 'keyGenerator'");
    }

    @Test
    void testValidation_NeitherKeyNorKeyGenerator_ThrowsException() {
        // When: Method with neither key nor keyGenerator is called
        // Then: Should throw IllegalStateException with appropriate message
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> bookService.invalidMethodNoKeyOrGenerator("test")
        ))
            .hasMessageContaining("Must specify either 'key' (SpEL expression) or 'keyGenerator'");
    }
}

