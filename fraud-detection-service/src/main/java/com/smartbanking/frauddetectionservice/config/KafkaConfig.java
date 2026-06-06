package com.smartbanking.frauddetectionservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Creates the topics that fraud-detection-service PUBLISHES to.
// The topic we CONSUME (transaction-service.transaction.initiated)
// is created by transaction-service — we just read from it here.

@Configuration
public class KafkaConfig {

    // Consumed by transaction-service SagaEventListener.
    // Our most important output — tells the saga to proceed or stop.
    @Bean
    public NewTopic fraudResultTopic() {
        return TopicBuilder
                .name("fraud-detection-service.fraud.result")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Consumed by notification-service to alert the user.
    // Published when risk score is 40-69 (flagged but not blocked).
    @Bean
    public NewTopic fraudAlertTopic() {
        return TopicBuilder
                .name("fraud-detection-service.fraud.alert")
                .partitions(3)
                .replicas(1)
                .build();
    }
}