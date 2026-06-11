package com.smartbanking.notificationservice.kafka;

import com.smartbanking.common.event.FraudAlertEvent;
import com.smartbanking.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Consumes FraudAlertEvent from fraud-detection-service.
// Only published when risk score >= 40 (flagged or blocked).
// Sends a security warning email to the account holder.

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "fraud-detection-service.fraud.alert",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFraudAlert(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("FraudAlertEvent received — partition: {}, offset: {}, " +
                        "ref: {}, score: {}, action: {}",
                partition, offset, event.getTransactionRef(),
                event.getRiskScore(), event.getRecommendedAction());

        try {
            notificationService.sendFraudAlertEmail(event);
        } catch (Exception e) {
            log.error("Failed to process FraudAlertEvent for ref: {}. Error: {}",
                    event.getTransactionRef(), e.getMessage(), e);
        }
    }
}