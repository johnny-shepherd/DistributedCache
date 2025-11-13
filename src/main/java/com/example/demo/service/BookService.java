package com.example.demo.service;

import com.example.demo.annotation.DistributedCacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookService.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Counter for tracking actual service executions (for testing purposes)
    private final AtomicInteger executionCounter = new AtomicInteger(0);

    @DistributedCacheable(value = "books", key = "#isbn")
    public String getBookByIsbn(String isbn) {
        // Increment counter - this only happens when method actually executes (cache miss)
        int currentCount = executionCounter.incrementAndGet();
        
        String threadName = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(formatter);
        
        logger.info("========================================");
        logger.info("[{}] [{}] FETCHING book with ISBN: {} - CACHE MISS, calling external API... (Execution #{})", 
                    threadName, startTime, isbn, currentCount);
        logger.info("========================================");
        
        // Simulate slow external API call (5 seconds)
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
        
        String endTime = LocalDateTime.now().format(formatter);
        String bookData = "Book[ISBN: " + isbn + ", Title: Sample Book, Author: John Doe]";
        
        logger.info("========================================");
        logger.info("[{}] [{}] COMPLETED fetching book with ISBN: {} - Returning: {}", 
                    threadName, endTime, isbn, bookData);
        logger.info("========================================");
        
        return bookData;
    }
    
    /**
     * Example method using custom KeyGenerator instead of SpEL expression.
     * This demonstrates the keyGenerator attribute support.
     */
    @DistributedCacheable(value = "books", keyGenerator = "customKeyGenerator")
    public String getAllBooks() {
        int currentCount = executionCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        logger.info("[{}] FETCHING all books with custom KeyGenerator (Execution #{})", threadName, currentCount);
        
        try {
            Thread.sleep(3000); // Simulate API call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
        
        return "All Books Data (generated with custom key)";
    }
    
    /**
     * Test method with INVALID annotation - has both key and keyGenerator.
     * This should throw IllegalStateException when called.
     */
    @DistributedCacheable(value = "books", key = "#param", keyGenerator = "customKeyGenerator")
    public String invalidMethodBothKeyAndGenerator(String param) {
        return "Should not reach here";
    }
    
    /**
     * Test method with INVALID annotation - has neither key nor keyGenerator.
     * This should throw IllegalStateException when called.
     */
    @DistributedCacheable(value = "books")
    public String invalidMethodNoKeyOrGenerator(String param) {
        return "Should not reach here";
    }
    
    // Test helper methods
    public int getExecutionCount() {
        return executionCounter.get();
    }
    
    public void resetExecutionCounter() {
        executionCounter.set(0);
    }
}

