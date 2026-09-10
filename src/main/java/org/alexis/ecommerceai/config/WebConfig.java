package org.alexis.ecommerceai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Global web configuration for Pageable argument resolution.
 * Sets a maximum page size of 50 and a default of 20 elements per page.
 * These defaults apply to every endpoint that accepts a {@code Pageable}
 * parameter, preventing clients from requesting unbounded result sets.
 */
@Configuration
public class WebConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setMaxPageSize(50);
        };
    }
}
