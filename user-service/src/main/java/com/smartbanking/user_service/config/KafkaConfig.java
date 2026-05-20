package com.smartbanking.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Creates topics on startup if they don't exist.
// If topic exists already — nothing happens. Safe to restart.

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder
                .name("user-service.user.registered")
                // 3 partitions = up to 3 consumer instances in parallel
                .partitions(3)
                // 1 replica for local dev — only 1 Kafka broker running
                .replicas(1)
                .build();
    }
}