package com.example.demo.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableAspectJAutoProxy
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, org.redisson.spring.cache.CacheConfig> config = new HashMap<>();
        
        // Configure "books" cache with TTL and max idle time
        // Redisson's cache manager automatically handles distributed locking
        // to prevent cache stampede (similar to sync=true but distributed)
        org.redisson.spring.cache.CacheConfig booksConfig = new org.redisson.spring.cache.CacheConfig(
                TimeUnit.MINUTES.toMillis(30),  // TTL: 30 minutes
                TimeUnit.MINUTES.toMillis(15)   // Max idle time: 15 minutes
        );
        
        config.put("books", booksConfig);
        
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}

