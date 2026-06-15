package com.smartbanking.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// Gateway uses REACTIVE Redis (ReactiveRedisTemplate, not RedisTemplate).
// The rate limiter built into Spring Cloud Gateway uses reactive Redis internally.
// We configure a ReactiveRedisTemplate with String serializers for our own use
// (correlation ID blacklist checks etc. if needed later).
// The rate limiter creates its own reactive connection automatically from
// the ReactiveRedisConnectionFactory that Spring Boot auto-configures.

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        // StringRedisSerializer for both key and value.
        // Keys in Redis will be readable strings, not binary garbage.
        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context =
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(serializer)
                        .value(serializer)
                        .hashKey(serializer)
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}