package com.example.demo.service;

import com.example.demo.annotation.DistributedCacheable;
import com.example.demo.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
     * Example: Don't cache null results using 'unless' condition.
     * This simulates an API that might return null for invalid ISBNs.
     */
    @DistributedCacheable(
        value = "books",
        key = "#isbn",
        unless = "#result == null"
    )
    public String getBookOrNull(String isbn) {
        int currentCount = executionCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        logger.info("[{}] FETCHING book with ISBN: {} (Execution #{})", threadName, isbn, currentCount);
        
        try {
            Thread.sleep(2000); // Simulate API call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
        
        // Return null for ISBNs that start with "invalid"
        if (isbn != null && isbn.startsWith("invalid")) {
            logger.info("[{}] Book not found for ISBN: {}, returning null", threadName, isbn);
            return null;
        }
        
        return "Book[ISBN: " + isbn + ", Title: Sample Book]";
    }
    
    /**
     * Example: Only cache when ISBN parameter is valid using 'condition'.
     * Caching is skipped entirely if condition is false (no cache lookup or storage).
     */
    @DistributedCacheable(
        value = "books",
        key = "#isbn",
        condition = "#isbn != null && #isbn.length() > 10"
    )
    public String getBookWithValidation(String isbn) {
        int currentCount = executionCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        logger.info("[{}] FETCHING book with validation, ISBN: {} (Execution #{})", threadName, isbn, currentCount);
        
        try {
            Thread.sleep(2000); // Simulate API call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
        
        return "Book[ISBN: " + isbn + ", Title: Validated Book]";
    }
    
    /**
     * Example: Combine both 'condition' and 'unless' for comprehensive control.
     * - condition: Only cache if ISBN is not null
     * - unless: Don't cache if result is null or empty
     */
    @DistributedCacheable(
        value = "books",
        key = "#isbn",
        condition = "#isbn != null",
        unless = "#result == null || #result.isEmpty()"
    )
    public String getBookWithBothConditions(String isbn) {
        int currentCount = executionCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        logger.info("[{}] FETCHING book with both conditions, ISBN: {} (Execution #{})", threadName, isbn, currentCount);
        
        try {
            Thread.sleep(2000); // Simulate API call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
        
        // Return empty string for ISBNs starting with "empty"
        if (isbn != null && isbn.startsWith("empty")) {
            logger.info("[{}] Book has empty content for ISBN: {}", threadName, isbn);
            return "";
        }
        
        // Return null for ISBNs starting with "null"
        if (isbn != null && isbn.startsWith("null")) {
            logger.info("[{}] Book not found for ISBN: {}", threadName, isbn);
            return null;
        }
        
        return "Book[ISBN: " + isbn + ", Title: Complete Book]";
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

    // ========== COMPLEX OBJECT CONDITIONAL METHODS ==========

    /**
     * Test condition with complex object property: #book.price > 50
     * Only caches expensive books (price > 50)
     */
    @DistributedCacheable(
        value = "books",
        key = "#book.isbn",
        condition = "#book.price != null && #book.price.compareTo(new java.math.BigDecimal('50')) > 0"
    )
    public Book getBookDetails(Book book) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Fetching details for book: {} (Execution #{})",
                    Thread.currentThread().getName(), book.getIsbn(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        // Enrich the book with additional details
        book.setTitle(book.getTitle() + " [Detailed Edition]");
        return book;
    }

    /**
     * Test condition with method call on object: #book.isExpensive()
     * Uses business logic method to determine caching
     */
    @DistributedCacheable(
        value = "books",
        key = "#book.isbn",
        condition = "#book.isExpensive()"
    )
    public Book getExpensiveBookData(Book book) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Fetching expensive book data: {} (Execution #{})",
                    Thread.currentThread().getName(), book.getIsbn(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        return book;
    }

    /**
     * Test condition with nested object property: #book.author.country == 'US'
     * Only caches books from US authors
     */
    @DistributedCacheable(
        value = "books",
        key = "#book.isbn",
        condition = "#book.author != null && #book.author.country == 'US'"
    )
    public Book getUSAuthorBook(Book book) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Fetching US author book: {} (Execution #{})",
                    Thread.currentThread().getName(), book.getIsbn(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        return book;
    }

    /**
     * Test condition with complex nested expression:
     * #book.author.country == 'US' && #book.author.bestseller
     */
    @DistributedCacheable(
        value = "books",
        key = "#book.isbn",
        condition = "#book.author != null && #book.author.country == 'US' && #book.author.bestseller == true"
    )
    public Book getBestsellingUSAuthorBook(Book book) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Fetching bestselling US author book: {} (Execution #{})",
                    Thread.currentThread().getName(), book.getIsbn(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        return book;
    }

    /**
     * Test condition with complex request: #request.isValidSearch()
     * Only caches valid search requests
     */
    @DistributedCacheable(
        value = "searches",
        key = "#request.query + '-' + #request.searchType",
        condition = "#request.isValidSearch()"
    )
    public SearchResult searchBooks(SearchRequest request) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Searching books: {} (Execution #{})",
                    Thread.currentThread().getName(), request.getQuery(), currentCount);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        // Simulate search logic
        SearchResult result = new SearchResult();
        if (request.getQuery() != null && request.getQuery().contains("java")) {
            Book book = new Book("978-0134685991", "Effective Java",
                                new BigDecimal("45.00"),
                                new Author("Joshua Bloch", "US", true));
            result.getItems().add(book);
            result.setTotalCount(1);
        }

        return result;
    }

    /**
     * Test condition with multiple request properties
     */
    @DistributedCacheable(
        value = "searches",
        key = "#request.query",
        condition = "#request.isValidSearch() && #request.pageSize <= 100"
    )
    public SearchResult searchBooksWithPagination(SearchRequest request) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Searching books with pagination: {} (Execution #{})",
                    Thread.currentThread().getName(), request.getQuery(), currentCount);

        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        return new SearchResult();
    }

    /**
     * Test unless with complex result: #result.isEmpty()
     * Don't cache empty search results
     */
    @DistributedCacheable(
        value = "searches",
        key = "#query",
        unless = "#result.isEmpty()"
    )
    public SearchResult searchBooksByTitle(String query) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Searching books by title: {} (Execution #{})",
                    Thread.currentThread().getName(), query, currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        SearchResult result = new SearchResult();

        // Only return results for specific queries
        if (query != null && query.toLowerCase().contains("java")) {
            Book book = new Book("978-0134685991", "Effective Java",
                                new BigDecimal("45.00"),
                                new Author("Joshua Bloch", "US", true));
            result.getItems().add(book);
            result.setTotalCount(1);
        }

        return result;
    }

    /**
     * Test unless with error checking: #result.hasErrors()
     * Don't cache results that contain errors
     */
    @DistributedCacheable(
        value = "searches",
        key = "#isbn",
        unless = "#result.hasErrors()"
    )
    public SearchResult findBookByIsbn(String isbn) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Finding book by ISBN: {} (Execution #{})",
                    Thread.currentThread().getName(), isbn, currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        SearchResult result = new SearchResult();

        // Simulate error for invalid ISBNs
        if (isbn == null || isbn.startsWith("invalid-")) {
            result.setErrorMessage("Invalid ISBN format");
        } else {
            Book book = new Book(isbn, "Sample Book",
                                new BigDecimal("29.99"),
                                new Author("John Doe", "US", false));
            result.getItems().add(book);
            result.setTotalCount(1);
        }

        return result;
    }

    /**
     * Test unless with complex condition: #result.isEmpty() || #result.hasErrors()
     * Don't cache empty or error results
     */
    @DistributedCacheable(
        value = "searches",
        key = "#request.query",
        unless = "#result.isEmpty() || #result.hasErrors()"
    )
    public SearchResult advancedSearch(SearchRequest request) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Advanced search: {} (Execution #{})",
                    Thread.currentThread().getName(), request.getQuery(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        SearchResult result = new SearchResult();

        // Simulate different scenarios
        if (request.getQuery() == null) {
            result.setErrorMessage("Query cannot be null");
        } else if (request.getQuery().equals("empty-result")) {
            // Return empty result (no items)
        } else {
            Book book = new Book("978-1234567890", request.getQuery(),
                                new BigDecimal("39.99"),
                                new Author("Jane Smith", "UK", false));
            result.getItems().add(book);
            result.setTotalCount(1);
        }

        return result;
    }

    /**
     * Test both condition and unless with complex objects
     * condition: Only cache valid searches
     * unless: Don't cache empty results
     */
    @DistributedCacheable(
        value = "searches",
        key = "#request.query + '-' + #request.searchType",
        condition = "#request.isValidSearch() && #request.searchType != null",
        unless = "#result.isEmpty() || #result.hasErrors()"
    )
    public SearchResult complexConditionalSearch(SearchRequest request) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Complex conditional search: {} (Execution #{})",
                    Thread.currentThread().getName(), request.getQuery(), currentCount);

        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        SearchResult result = new SearchResult();

        if ("error-query".equals(request.getQuery())) {
            result.setErrorMessage("Search failed");
        } else if ("empty-query".equals(request.getQuery())) {
            // Return empty
        } else {
            Book book = new Book("978-1111111111", "Search Result",
                                new BigDecimal("25.00"),
                                new Author("Test Author", "US", false));
            result.getItems().add(book);
            result.setTotalCount(1);
        }

        return result;
    }

    /**
     * Test condition with user role and unless with result validation
     */
    @DistributedCacheable(
        value = "books",
        key = "#user.userId + '-' + #isbn",
        condition = "#user.isActive() && #user.isPremium()",
        unless = "#result == null"
    )
    public Book getPremiumBookAccess(User user, String isbn) {
        int currentCount = executionCounter.incrementAndGet();
        logger.info("[{}] Getting premium book access for user: {} (Execution #{})",
                    Thread.currentThread().getName(), user.getUserId(), currentCount);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        if (isbn.startsWith("premium-")) {
            return new Book(isbn, "Premium Content",
                           new BigDecimal("99.99"),
                           new Author("Premium Author", "US", true));
        }

        return null;
    }

    // Test helper methods
    public int getExecutionCount() {
        return executionCounter.get();
    }
    
    public void resetExecutionCounter() {
        executionCounter.set(0);
    }
}

