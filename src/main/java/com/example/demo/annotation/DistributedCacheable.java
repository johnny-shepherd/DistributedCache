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
}

