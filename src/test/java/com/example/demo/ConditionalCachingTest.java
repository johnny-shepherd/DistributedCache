package com.example.demo;

import com.example.demo.config.TestRedisConfiguration;
import com.example.demo.model.*;
import com.example.demo.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for conditional/unless logic with complex objects.
 * These tests verify SpEL evaluation with complex objects, nested properties, and method calls.
 *
 * Separate from CacheStampedeIntegrationTest which focuses on concurrent cache stampede prevention.
 * All tests here are single-threaded to focus on conditional logic evaluation.
 */
@SpringBootTest
@Import(TestRedisConfiguration.class)
class ConditionalCachingTest {

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

    // ========== COMPLEX OBJECT PROPERTY TESTS ==========

    @Test
    void testCondition_ComplexObject_PriceProperty_WhenTrue_Caches() throws Exception {
        // Given: Book with price > 50 (condition will be TRUE)
        Book expensiveBook = new Book(
            "978-1234567890",
            "Expensive Book",
            new BigDecimal("75.00"),
            new Author("John Doe", "US", false)
        );

        // When: Call method twice with expensive book
        Book firstResult = bookService.getBookDetails(expensiveBook);
        int executionsAfterFirst = bookService.getExecutionCount();

        Book secondResult = bookService.getBookDetails(expensiveBook);
        int executionsAfterSecond = bookService.getExecutionCount();

        // Then: Should cache (condition=true)
        assertThat(executionsAfterFirst)
            .as("First call should execute when book is expensive")
            .isEqualTo(1);

        assertThat(executionsAfterSecond)
            .as("Second call should use cache when book is expensive")
            .isEqualTo(1);

        assertThat(firstResult.getIsbn()).isEqualTo(secondResult.getIsbn());
    }

    @Test
    void testCondition_ComplexObject_PriceProperty_WhenFalse_DoesNotCache() throws Exception {
        // Given: Book with price <= 50 (condition will be FALSE)
        Book cheapBook = new Book(
            "978-9876543210",
            "Cheap Book",
            new BigDecimal("25.00"),
            new Author("Jane Smith", "UK", false)
        );

        // When: Call method twice with cheap book
        Book firstResult = bookService.getBookDetails(cheapBook);
        int executionsAfterFirst = bookService.getExecutionCount();

        Book secondResult = bookService.getBookDetails(cheapBook);
        int executionsAfterSecond = bookService.getExecutionCount();

        // Then: Should NOT cache (condition=false, no caching at all)
        assertThat(executionsAfterFirst)
            .as("First call should execute when book is cheap")
            .isEqualTo(1);

        assertThat(executionsAfterSecond)
            .as("Second call should ALSO execute (no caching for cheap books)")
            .isEqualTo(2);
    }

    @Test
    void testCondition_ComplexObject_MethodCall_IsExpensive() throws Exception {
        // Given: Book where isExpensive() returns true
        Book expensiveBook = new Book(
            "978-1111111111",
            "Premium Book",
            new BigDecimal("100.00"),
            new Author("Premium Author", "US", true)
        );

        // When: Call method twice
        bookService.getExpensiveBookData(expensiveBook);
        int executionsAfterFirst = bookService.getExecutionCount();

        bookService.getExpensiveBookData(expensiveBook);
        int executionsAfterSecond = bookService.getExecutionCount();

        // Then: Should cache (isExpensive() returns true)
        assertThat(executionsAfterFirst).isEqualTo(1);
        assertThat(executionsAfterSecond).isEqualTo(1);
    }

    @Test
    void testCondition_NestedObject_AuthorCountry_US_Caches() throws Exception {
        // Given: Book with US author (condition=true)
        Book usBook = new Book(
            "978-2222222222",
            "American Book",
            new BigDecimal("45.00"),
            new Author("US Author", "US", false)
        );

        // When: Call twice
        bookService.getUSAuthorBook(usBook);
        bookService.getUSAuthorBook(usBook);

        // Then: Should cache (author.country == 'US')
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCondition_NestedObject_AuthorCountry_NonUS_DoesNotCache() throws Exception {
        // Given: Book with non-US author (condition=false)
        Book ukBook = new Book(
            "978-3333333333",
            "British Book",
            new BigDecimal("45.00"),
            new Author("UK Author", "UK", false)
        );

        // When: Call twice
        bookService.getUSAuthorBook(ukBook);
        bookService.getUSAuthorBook(ukBook);

        // Then: Should NOT cache (author.country != 'US')
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCondition_NestedObject_ComplexExpression_BestsellingUSAuthor() throws Exception {
        // Given: Book with US bestselling author (both conditions true)
        Book bestsellingUSBook = new Book(
            "978-4444444444",
            "Bestseller",
            new BigDecimal("50.00"),
            new Author("Bestselling Author", "US", true)
        );

        // When: Call twice
        bookService.getBestsellingUSAuthorBook(bestsellingUSBook);
        bookService.getBestsellingUSAuthorBook(bestsellingUSBook);

        // Then: Should cache
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCondition_NestedObject_ComplexExpression_NonBestsellingUSAuthor_DoesNotCache() throws Exception {
        // Given: Book with US non-bestselling author (second condition false)
        Book nonBestsellerBook = new Book(
            "978-5555555555",
            "Regular Book",
            new BigDecimal("30.00"),
            new Author("Regular Author", "US", false)
        );

        // When: Call twice
        bookService.getBestsellingUSAuthorBook(nonBestsellerBook);
        bookService.getBestsellingUSAuthorBook(nonBestsellerBook);

        // Then: Should NOT cache (not a bestseller)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    // ========== COMPLEX REQUEST OBJECT TESTS ==========

    @Test
    void testCondition_ComplexRequest_ValidSearch_Caches() throws Exception {
        // Given: Valid search request (query length >= 3)
        SearchRequest validRequest = new SearchRequest("java programming", SearchType.TITLE);

        // When: Call twice
        bookService.searchBooks(validRequest);
        bookService.searchBooks(validRequest);

        // Then: Should cache (isValidSearch() returns true)
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCondition_ComplexRequest_InvalidSearch_DoesNotCache() throws Exception {
        // Given: Invalid search request (query too short)
        SearchRequest invalidRequest = new SearchRequest("ab", SearchType.TITLE);

        // When: Call twice
        bookService.searchBooks(invalidRequest);
        bookService.searchBooks(invalidRequest);

        // Then: Should NOT cache (isValidSearch() returns false)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCondition_ComplexRequest_MultipleProperties() throws Exception {
        // Given: Valid request with acceptable page size
        SearchRequest validRequest = new SearchRequest("spring boot", SearchType.FULL_TEXT);
        validRequest.setPageSize(50);

        // When: Call twice
        bookService.searchBooksWithPagination(validRequest);
        bookService.searchBooksWithPagination(validRequest);

        // Then: Should cache (both conditions met)
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCondition_ComplexRequest_PageSizeTooLarge_DoesNotCache() throws Exception {
        // Given: Request with page size > 100
        SearchRequest largePageRequest = new SearchRequest("database", SearchType.TITLE);
        largePageRequest.setPageSize(200);

        // When: Call twice
        bookService.searchBooksWithPagination(largePageRequest);
        bookService.searchBooksWithPagination(largePageRequest);

        // Then: Should NOT cache (pageSize > 100)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    // ========== COMPLEX RESULT OBJECT TESTS ==========

    @Test
    void testUnless_ComplexResult_NotEmpty_Caches() throws Exception {
        // Given: Query that returns non-empty results
        String queryWithResults = "java programming";

        // When: Call twice
        SearchResult firstResult = bookService.searchBooksByTitle(queryWithResults);
        SearchResult secondResult = bookService.searchBooksByTitle(queryWithResults);

        // Then: Should cache (result is not empty)
        assertThat(firstResult.isEmpty()).isFalse();
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testUnless_ComplexResult_Empty_DoesNotCache() throws Exception {
        // Given: Query that returns empty results
        String queryNoResults = "nonexistent book xyz";

        // When: Call twice
        SearchResult firstResult = bookService.searchBooksByTitle(queryNoResults);
        SearchResult secondResult = bookService.searchBooksByTitle(queryNoResults);

        // Then: Should NOT cache (result is empty)
        assertThat(firstResult.isEmpty()).isTrue();
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testUnless_ComplexResult_HasErrors_DoesNotCache() throws Exception {
        // Given: ISBN that causes error
        String invalidIsbn = "invalid-isbn-format";

        // When: Call twice
        SearchResult firstResult = bookService.findBookByIsbn(invalidIsbn);
        SearchResult secondResult = bookService.findBookByIsbn(invalidIsbn);

        // Then: Should NOT cache (result has errors)
        assertThat(firstResult.hasErrors()).isTrue();
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testUnless_ComplexResult_NoErrors_Caches() throws Exception {
        // Given: Valid ISBN
        String validIsbn = "978-1234567890";

        // When: Call twice
        SearchResult firstResult = bookService.findBookByIsbn(validIsbn);
        SearchResult secondResult = bookService.findBookByIsbn(validIsbn);

        // Then: Should cache (no errors)
        assertThat(firstResult.hasErrors()).isFalse();
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testUnless_ComplexCondition_EmptyOrErrors_BothScenarios() throws Exception {
        // Scenario 1: Empty result
        SearchRequest emptyRequest = new SearchRequest("empty-result", SearchType.TITLE);
        bookService.advancedSearch(emptyRequest);
        bookService.advancedSearch(emptyRequest);
        assertThat(bookService.getExecutionCount()).isEqualTo(2); // Not cached

        bookService.resetExecutionCounter();

        // Scenario 2: Error result
        SearchRequest errorRequest = new SearchRequest(null, SearchType.TITLE);
        bookService.advancedSearch(errorRequest);
        bookService.advancedSearch(errorRequest);
        assertThat(bookService.getExecutionCount()).isEqualTo(2); // Not cached

        bookService.resetExecutionCounter();

        // Scenario 3: Valid result
        SearchRequest validRequest = new SearchRequest("valid-query", SearchType.TITLE);
        bookService.advancedSearch(validRequest);
        bookService.advancedSearch(validRequest);
        assertThat(bookService.getExecutionCount()).isEqualTo(1); // Cached!
    }

    // ========== COMBINED CONDITION AND UNLESS TESTS ==========

    @Test
    void testCombined_ValidCondition_ValidResult_Caches() throws Exception {
        // Given: Valid request that returns valid result
        SearchRequest validRequest = new SearchRequest("java", SearchType.TITLE);

        // When: Call twice
        SearchResult firstResult = bookService.complexConditionalSearch(validRequest);
        SearchResult secondResult = bookService.complexConditionalSearch(validRequest);

        // Then: Should cache (condition=true, unless=false)
        assertThat(firstResult.isEmpty()).isFalse();
        assertThat(firstResult.hasErrors()).isFalse();
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCombined_ValidCondition_EmptyResult_DoesNotCache() throws Exception {
        // Given: Valid request that returns empty result
        SearchRequest emptyRequest = new SearchRequest("empty-query", SearchType.TITLE);

        // When: Call twice
        bookService.complexConditionalSearch(emptyRequest);
        bookService.complexConditionalSearch(emptyRequest);

        // Then: Should NOT cache (condition=true, but unless=true due to empty)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCombined_ValidCondition_ErrorResult_DoesNotCache() throws Exception {
        // Given: Valid request that returns error
        SearchRequest errorRequest = new SearchRequest("error-query", SearchType.TITLE);

        // When: Call twice
        bookService.complexConditionalSearch(errorRequest);
        bookService.complexConditionalSearch(errorRequest);

        // Then: Should NOT cache (condition=true, but unless=true due to error)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCombined_InvalidCondition_SkipsCaching() throws Exception {
        // Given: Invalid request (query too short)
        SearchRequest invalidRequest = new SearchRequest("ab", SearchType.TITLE);

        // When: Call twice
        bookService.complexConditionalSearch(invalidRequest);
        bookService.complexConditionalSearch(invalidRequest);

        // Then: Should NOT cache (condition=false, skips all caching)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCombined_UserRoleCondition_PremiumActive_Caches() throws Exception {
        // Given: Active premium user
        User premiumUser = new User("user123", "MEMBER", true, true);
        String isbn = "premium-book-001";

        // When: Call twice
        Book firstResult = bookService.getPremiumBookAccess(premiumUser, isbn);
        Book secondResult = bookService.getPremiumBookAccess(premiumUser, isbn);

        // Then: Should cache (user is active and premium, result is not null)
        assertThat(firstResult).isNotNull();
        assertThat(bookService.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testCombined_UserRoleCondition_NotPremium_DoesNotCache() throws Exception {
        // Given: Active but non-premium user
        User regularUser = new User("user456", "MEMBER", false, true);
        String isbn = "premium-book-002";

        // When: Call twice
        bookService.getPremiumBookAccess(regularUser, isbn);
        bookService.getPremiumBookAccess(regularUser, isbn);

        // Then: Should NOT cache (user is not premium, condition=false)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCombined_UserRoleCondition_Inactive_DoesNotCache() throws Exception {
        // Given: Inactive premium user
        User inactiveUser = new User("user789", "MEMBER", true, false);
        String isbn = "premium-book-003";

        // When: Call twice
        bookService.getPremiumBookAccess(inactiveUser, isbn);
        bookService.getPremiumBookAccess(inactiveUser, isbn);

        // Then: Should NOT cache (user is not active, condition=false)
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }

    @Test
    void testCombined_UserRoleCondition_NullResult_DoesNotCache() throws Exception {
        // Given: Premium active user but non-premium ISBN
        User premiumUser = new User("user999", "MEMBER", true, true);
        String isbn = "regular-book-001"; // Not a premium book

        // When: Call twice
        Book firstResult = bookService.getPremiumBookAccess(premiumUser, isbn);
        Book secondResult = bookService.getPremiumBookAccess(premiumUser, isbn);

        // Then: Should NOT cache (condition=true, but unless=true due to null result)
        assertThat(firstResult).isNull();
        assertThat(bookService.getExecutionCount()).isEqualTo(2);
    }
}
