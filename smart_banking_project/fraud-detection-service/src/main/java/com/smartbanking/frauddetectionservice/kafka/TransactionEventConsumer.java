package com.smartbanking.frauddetectionservice.kafka;

import com.smartbanking.common.event.TransactionInitiatedEvent;
import com.smartbanking.frauddetectionservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// TransactionEventConsumer is the Kafka entry point for this service.
// It receives TransactionInitiatedEvent and delegates to FraudDetectionService.
//
// KEY RULE: @KafkaListener methods must NEVER throw unhandled exceptions.
// If an exception propagates out of a listener, Kafka retries the message.
// For most exceptions that will keep failing (e.g. data format errors),
// this creates an infinite retry loop that blocks the partition.
// We catch all exceptions here and log them.
// In production: configure a Dead Letter Topic for failed messages.

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final FraudDetectionService fraudDetectionService;

    // topics: the Kafka topic to consume from
    // groupId: consumers with same groupId share partition load
    //   If we run 2 instances, each gets half the partitions
    // containerFactory: the bean name of our KafkaListenerContainerFactory
    //   Spring Boot auto-creates this from kafka consumer properties

    @KafkaListener(
            topics = "transaction-service.transaction.initiated",
            groupId = "fraud-detection-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionInitiated(
            // @Payload: deserialize the Kafka message value into this object
            // Jackson reads the type header we set (spring.json.add.type.headers=true)
            // and deserializes to TransactionInitiatedEvent automatically
            @Payload TransactionInitiatedEvent event,

            // @Header: extract Kafka metadata headers
            // Useful for logging — we can see exactly which partition and offset
            // this message came from. Critical for debugging duplicate processing.
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("TransactionInitiatedEvent received — " +
                        "topic: {}, partition: {}, offset: {}, ref: {}, amount: {}",
                topic, partition, offset,
                event.getTransactionRef(), event.getAmount());

        try {
            // Delegate all business logic to FraudDetectionService
            fraudDetectionService.evaluate(event);

        } catch (Exception e) {
            // Catch everything. Log in detail. Do NOT rethrow.
            // If we rethrow, Kafka retries this message forever.
            // The transaction-service saga will timeout and use the
            // circuit breaker fallback after waiting too long.
            log.error("CRITICAL: Failed to evaluate fraud for ref: {}. " +
                            "Error: {}. Message will NOT be retried. " +
                            "Transaction-service circuit breaker will handle this.",
                    event.getTransactionRef(), e.getMessage(), e);
            // TODO production: publish to dead letter topic for manual review
        }
    }
}