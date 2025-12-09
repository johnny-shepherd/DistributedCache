package com.example.demo.config;

import com.redis.testcontainers.RedisContainer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestRedisConfiguration {

    private static final RedisContainer redisContainer;

    static {
        redisContainer = new RedisContainer(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
    }

    @Bean
    public RedisContainer redisContainer() {
        return redisContainer;
    }

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = String.format("redis://%s:%d",
            redisContainer.getHost(),
            redisContainer.getFirstMappedPort());
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2);
        return Redisson.create(config);
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }
}


