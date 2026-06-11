package com.smartbanking.notificationservice.kafka;

import com.smartbanking.common.event.TransactionCompletedEvent;
import com.smartbanking.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Consumes TransactionCompletedEvent from transaction-service.
// This same topic carries both SUCCESS and FAILED outcomes.
// NotificationService checks finalStatus to decide which email to send.

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "transaction-service.transaction.completed",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionCompleted(
            @Payload TransactionCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("TransactionCompletedEvent received — partition: {}, offset: {}, " +
                        "ref: {}, status: {}",
                partition, offset,
                event.getTransactionRef(), event.getFinalStatus());

        try {
            notificationService.sendTransactionCompletedEmail(event);
        } catch (Exception e) {
            log.error("Failed to process TransactionCompletedEvent for ref: {}. " +
                    "Error: {}", event.getTransactionRef(), e.getMessage(), e);
        }
    }
}