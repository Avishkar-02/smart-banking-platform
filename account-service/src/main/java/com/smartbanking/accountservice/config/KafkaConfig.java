package com.smartbanking.accountservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Creates Kafka topics on startup if they don't already exist.
// Safe to run repeatedly — Kafka is idempotent on topic creation.

@Configuration
public class KafkaConfig {

    // Topic naming convention: service-name.entity.event

    @Bean
    public NewTopic accountCreatedTopic() {
        return TopicBuilder
                .name("account-service.account.created")
                .partitions(3)
                // Production: use replicas(3) for fault tolerance.
                .replicas(1)
                .build();
    }
}