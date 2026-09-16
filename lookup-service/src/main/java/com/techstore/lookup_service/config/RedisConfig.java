package com.techstore.lookup_service.config;

import com.techstore.lookup_service.dto.ProductItem;
import com.techstore.lookup_service.dto.ProductItemResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ProductItem> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {

        RedisTemplate<String, ProductItem> template =
                new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        return template;
    }
}
