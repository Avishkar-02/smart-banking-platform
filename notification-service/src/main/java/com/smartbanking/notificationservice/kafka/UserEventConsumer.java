package com.smartbanking.notificationservice.kafka;

import com.smartbanking.common.event.UserRegisteredEvent;
import com.smartbanking.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Consumes UserRegisteredEvent from user-service.
// Triggers welcome email to the new user.
// If notification-service was down during registration,
// events are replayed from Kafka on restart (auto-offset-reset=earliest).

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "user-service.user.registered",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(
            @Payload UserRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("UserRegisteredEvent received — partition: {}, offset: {}, " +
                "email: {}", partition, offset, event.getEmail());

        try {
            notificationService.sendWelcomeEmail(event);
        } catch (Exception e) {
            // Never let exceptions escape from @KafkaListener
            // or Kafka will retry the message indefinitely
            log.error("Failed to process UserRegisteredEvent for: {}. Error: {}",
                    event.getEmail(), e.getMessage(), e);
        }
    }
}