package com.example.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom caching annotation that prevents cache stampede using distributed locks.
 * 
 * Similar to Spring's @Cacheable but with built-in distributed locking to ensure
 * only one thread executes the method for a given cache key while others wait
 * for the cached result.
 * 
 * Usage:
 * <pre>
 * @DistributedCacheable(value = "books", key = "#isbn")
 * public Book getBookByIsbn(String isbn) {
 *     return expensiveApiCall(isbn);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedCacheable {
    
    /**
     * Name of the cache to use.
     * @return cache name
     */
    String value();
    
    /**
     * SpEL expression for computing the cache key.
     * Supports parameter references like "#isbn", "#user.id", etc.
     * Mutually exclusive with {@link #keyGenerator()}.
     * @return SpEL expression for cache key
     */
    String key() default "";
    
    /**
     * The bean name of the custom KeyGenerator to use.
     * Mutually exclusive with {@link #key()}.
     * @return the name of the KeyGenerator bean
     */
    String keyGenerator() default "";
    
    /**
     * Lock timeout in milliseconds. Defaults to 10 seconds.
     * If a thread cannot acquire the lock within this time, it will proceed
     * to execute the method (fallback behavior to prevent deadlock).
     * @return lock timeout in milliseconds
     */
    long lockTimeout() default 10000;
    
    /**
     * SpEL expression to conditionally cache based on method parameters.
     * Evaluated BEFORE method execution. If the expression evaluates to false,
     * caching is skipped entirely (no cache lookup, no cache storage).
     * 
     * Example: condition = "#isbn != null && #isbn.length() > 10"
     * 
     * @return SpEL expression for conditional caching
     */
    String condition() default "";
    
    /**
     * SpEL expression to conditionally prevent caching based on the method result.
     * Evaluated AFTER method execution. If the expression evaluates to true,
     * the result will NOT be cached (but the method still executes).
     * The result is available as '#result' in the expression.
     * 
     * Example: unless = "#result == null || #result.isEmpty()"
     * 
     * @return SpEL expression to prevent caching
     */
    String unless() default "";
}

