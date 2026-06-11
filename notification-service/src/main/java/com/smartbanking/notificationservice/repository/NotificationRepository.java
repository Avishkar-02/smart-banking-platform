package com.smartbanking.notificationservice.repository;

import com.smartbanking.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // Used for admin endpoint — see all notifications for a user
    List<Notification> findByRecipientUuidOrderByCreatedAtDesc(
            String recipientUuid);

    // Used for idempotency — check if this notification was already sent.
    // We check transactionRef + type to prevent duplicate emails on Kafka retry.
    boolean existsByTransactionRefAndNotificationType(
            String transactionRef,
            Notification.NotificationType notificationType);
}