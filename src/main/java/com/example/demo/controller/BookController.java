package com.example.demo.controller;

import com.example.demo.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Autowired
    private BookService bookService;

    /**
     * Simple endpoint to get a book by ISBN
     */
    @GetMapping("/book/{isbn}")
    public String getBook(@PathVariable String isbn) {
        String threadName = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(formatter);
        
        logger.info("[{}] [{}] Controller received request for ISBN: {}", threadName, startTime, isbn);
        String result = bookService.getBookByIsbn(isbn);
        
        String endTime = LocalDateTime.now().format(formatter);
        logger.info("[{}] [{}] Controller returning result for ISBN: {}", threadName, endTime, isbn);
        
        return result;
    }

    /**
     * Test endpoint that spawns 5 concurrent threads requesting the same ISBN
     * This demonstrates the cache stampede prevention
     */
    @GetMapping("/test-concurrent/{isbn}")
    public String testConcurrentAccess(@PathVariable String isbn) {
        logger.info("================================================================================");
        logger.info("STARTING CONCURRENT TEST - Spawning 5 threads to request ISBN: {}", isbn);
        logger.info("Expected behavior: Only ONE thread should execute the service method");
        logger.info("================================================================================");

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Spawn 5 threads that will all try to get the same book simultaneously
        for (int i = 0; i < 5; i++) {
            final int threadNum = i + 1;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String threadName = Thread.currentThread().getName();
                String startTime = LocalDateTime.now().format(formatter);
                
                logger.info(">>> Thread #{} [{}] [{}] - Starting request for ISBN: {}", 
                           threadNum, threadName, startTime, isbn);
                
                String result = bookService.getBookByIsbn(isbn);
                
                String endTime = LocalDateTime.now().format(formatter);
                logger.info("<<< Thread #{} [{}] [{}] - Received result for ISBN: {}", 
                           threadNum, threadName, endTime, isbn);
                
                return "Thread-" + threadNum + ": " + result;
            }, executor);
            
            futures.add(future);
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        StringBuilder response = new StringBuilder();
        response.append("Concurrent Test Results:\n\n");
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                response.append(futures.get(i).get()).append("\n");
            } catch (Exception e) {
                response.append("Thread-").append(i + 1).append(": Error - ").append(e.getMessage()).append("\n");
            }
        }

        executor.shutdown();
        
        logger.info("================================================================================");
        logger.info("CONCURRENT TEST COMPLETED for ISBN: {}", isbn);
        logger.info("Check logs above - you should see only ONE 'FETCHING' message");
        logger.info("================================================================================");

        return response.toString();
    }
}

