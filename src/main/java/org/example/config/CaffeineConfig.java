package org.example.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Object> orderCache() {
        return newCache(500);
    }

    @Bean
    public Cache<String, Object> userOrdersCache() {
        return newCache(200);
    }

    private Cache<String, Object> newCache(long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats()
                .build();
    }
}
