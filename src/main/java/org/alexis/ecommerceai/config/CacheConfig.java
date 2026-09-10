package org.alexis.ecommerceai.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configures Caffeine as the cache provider for the application.
 * Enables Spring's {@code @Cacheable} / {@code @CacheEvict} annotations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name shared by paginated listings and single-entity lookups. */
    public static final String PRODUCTOS_CACHE = "productos";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PRODUCTOS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5)));
        return manager;
    }
}
