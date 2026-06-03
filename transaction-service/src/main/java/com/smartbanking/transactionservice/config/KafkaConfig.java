package com.smartbanking.transactionservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Creates all topics that transaction-service PUBLISHES to.
// Topics that other services publish (account.debited, fraud.result)
// are created by those services — we just consume them here.
// Topics are created idempotently — safe to restart the service.

@Configuration
public class KafkaConfig {

    // Consumed by fraud-detection-service to score the transaction
    @Bean
    public NewTopic transactionInitiatedTopic() {
        return TopicBuilder.name("transaction-service.transaction.initiated")
                .partitions(3).replicas(1).build();
    }

    // Consumed by account-service to debit the source account
    @Bean
    public NewTopic debitCommandTopic() {
        return TopicBuilder.name("transaction-service.debit.command")
                .partitions(3).replicas(1).build();
    }

    // Consumed by account-service to credit the destination account
    @Bean
    public NewTopic creditCommandTopic() {
        return TopicBuilder.name("transaction-service.credit.command")
                .partitions(3).replicas(1).build();
    }

    // Consumed by account-service to reverse a debit when credit fails
    @Bean
    public NewTopic reverseDebitCommandTopic() {
        return TopicBuilder.name("transaction-service.reverse.debit.command")
                .partitions(3).replicas(1).build();
    }

    // Consumed by notification-service to send transfer result emails/SMS
    @Bean
    public NewTopic transactionCompletedTopic() {
        return TopicBuilder.name("transaction-service.transaction.completed")
                .partitions(3).replicas(1).build();
    }
}