package com.example.demo.aspect;

import com.example.demo.annotation.DistributedCacheable;
import com.example.demo.util.CacheKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Aspect that intercepts methods annotated with @DistributedCacheable
 * and implements distributed cache stampede prevention using Redisson locks.
 * 
 * Uses double-check locking pattern:
 * 1. Check cache (first check)
 * 2. If miss, acquire distributed lock
 * 3. Check cache again (double-check)
 * 4. If still miss, execute method
 * 5. Cache result
 * 6. Release lock
 */
@Aspect
@Component
public class DistributedCacheableAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheableAspect.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private CacheKeyResolver cacheKeyResolver;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Around("@annotation(distributedCacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint, 
                                 DistributedCacheable distributedCacheable) throws Throwable {
        
        // Resolve cache key - either from SpEL or KeyGenerator
        String cacheKey = resolveCacheKey(joinPoint, distributedCacheable);
        String cacheName = distributedCacheable.value();
        long lockTimeout = distributedCacheable.lockTimeout();
        
        logger.debug("[DistributedCacheable] Cache: {}, Key: {}", cacheName, cacheKey);
        
        // Get cache
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            logger.warn("[DistributedCacheable] Cache '{}' not found, executing method without caching", cacheName);
            return joinPoint.proceed();
        }
        
        // FIRST CHECK: Is value in cache?
        Cache.ValueWrapper cachedValue = cache.get(cacheKey);
        if (cachedValue != null) {
            logger.debug("[DistributedCacheable] Cache HIT for key: {}", cacheKey);
            return cachedValue.get();
        }
        
        // Cache miss - acquire distributed lock
        String lockKey = cacheName + ":lock:" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // Try to acquire lock with timeout
            boolean lockAcquired = lock.tryLock(lockTimeout, TimeUnit.MILLISECONDS);
            
            if (!lockAcquired) {
                // Could not acquire lock within timeout - fallback to executing method
                logger.warn("[DistributedCacheable] Could not acquire lock for key: {} within {}ms, executing method anyway", 
                           cacheKey, lockTimeout);
                return joinPoint.proceed();
            }
            
            logger.debug("[DistributedCacheable] Lock ACQUIRED for key: {}", cacheKey);
            
            try {
                // DOUBLE-CHECK: Another thread may have populated cache while we waited for lock
                cachedValue = cache.get(cacheKey);
                if (cachedValue != null) {
                    logger.debug("[DistributedCacheable] Cache HIT on double-check for key: {}", cacheKey);
                    return cachedValue.get();
                }
                
                // Still not in cache - execute method
                logger.debug("[DistributedCacheable] Cache MISS on double-check, executing method for key: {}", cacheKey);
                Object result = joinPoint.proceed();
                
                // Cache the result
                cache.put(cacheKey, result);
                logger.debug("[DistributedCacheable] Cached result for key: {}", cacheKey);
                
                return result;
                
            } finally {
                // Always release lock
                lock.unlock();
                logger.debug("[DistributedCacheable] Lock RELEASED for key: {}", cacheKey);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[DistributedCacheable] Interrupted while waiting for lock for key: {}", cacheKey, e);
            // Fallback: execute method without caching
            return joinPoint.proceed();
        }
    }
    
    /**
     * Resolves the cache key using either SpEL expression or custom KeyGenerator.
     * 
     * @param joinPoint the intercepted method call
     * @param distributedCacheable the annotation
     * @return the resolved cache key as a string
     */
    private String resolveCacheKey(ProceedingJoinPoint joinPoint, DistributedCacheable distributedCacheable) {
        String keyExpression = distributedCacheable.key();
        String keyGeneratorName = distributedCacheable.keyGenerator();
        
        // Validate: only one of key or keyGenerator should be specified
        boolean hasKey = keyExpression != null && !keyExpression.isEmpty();
        boolean hasKeyGenerator = keyGeneratorName != null && !keyGeneratorName.isEmpty();
        
        if (hasKey && hasKeyGenerator) {
            throw new IllegalStateException(
                "@DistributedCacheable: Cannot specify both 'key' and 'keyGenerator'. Use one or the other."
            );
        }
        
        if (!hasKey && !hasKeyGenerator) {
            throw new IllegalStateException(
                "@DistributedCacheable: Must specify either 'key' (SpEL expression) or 'keyGenerator' (bean name)."
            );
        }
        
        // Use KeyGenerator if specified
        if (hasKeyGenerator) {
            return resolveKeyUsingGenerator(joinPoint, keyGeneratorName);
        }
        
        // Otherwise use SpEL expression
        return cacheKeyResolver.resolve(joinPoint, keyExpression);
    }
    
    /**
     * Resolves the cache key using a custom KeyGenerator bean.
     * 
     * @param joinPoint the intercepted method call
     * @param keyGeneratorName the name of the KeyGenerator bean
     * @return the generated cache key as a string
     */
    private String resolveKeyUsingGenerator(ProceedingJoinPoint joinPoint, String keyGeneratorName) {
        // Get the KeyGenerator bean from Spring context
        KeyGenerator keyGenerator;
        try {
            keyGenerator = applicationContext.getBean(keyGeneratorName, KeyGenerator.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "@DistributedCacheable: KeyGenerator bean '" + keyGeneratorName + "' not found in application context.",
                e
            );
        }
        
        // Extract method and arguments
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        
        // Generate the key
        Object generatedKey = keyGenerator.generate(target, method, args);
        
        return generatedKey != null ? generatedKey.toString() : "null";
    }
}

