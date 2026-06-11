package com.smartbanking.notificationservice.service;

import com.smartbanking.common.event.FraudAlertEvent;
import com.smartbanking.common.event.TransactionCompletedEvent;
import com.smartbanking.common.event.UserRegisteredEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.notificationservice.dto.NotificationResponse;
import com.smartbanking.notificationservice.entity.Notification;
import com.smartbanking.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final EmailTemplateService emailTemplateService;

    @Value("${notification.mail.from:noreply@smartbanking.com}")
    private String fromEmail;

    @Value("${notification.mail.from-name:Smart Banking Platform}")
    private String fromName;

    // ── Welcome Email ──────────────────────────────────────────────────────

    @Transactional
    public void sendWelcomeEmail(UserRegisteredEvent event) {
        log.info("Sending welcome email to: {} (UUID: {})",
                event.getEmail(), event.getUserUuid());

        // Idempotency check — Kafka may deliver same event twice on restart
        // We use userUuid as a proxy transaction ref for welcome emails
        if (notificationRepository.existsByTransactionRefAndNotificationType(
                event.getUserUuid(), Notification.NotificationType.WELCOME)) {
            log.warn("Welcome email already sent for UUID: {}. Skipping duplicate.",
                    event.getUserUuid());
            return;
        }

        String subject = emailTemplateService.buildWelcomeSubject(event);
        String body    = emailTemplateService.buildWelcomeBody(event);

        // We use userUuid as the "transactionRef" for welcome emails
        // so the idempotency check has something to match on
        sendAndSave(
                event.getEmail(),
                event.getUserUuid(),
                Notification.NotificationType.WELCOME,
                subject,
                body,
                event.getUserUuid()   // transactionRef field = userUuid for welcome
        );
    }

    // ── Transaction Completed Email ────────────────────────────────────────

    @Transactional
    public void sendTransactionCompletedEmail(TransactionCompletedEvent event) {
        log.info("Sending transaction notification — ref: {}, status: {}",
                event.getTransactionRef(), event.getFinalStatus());

        if ("COMPLETED".equals(event.getFinalStatus())) {
            sendTransferSuccessEmails(event);
        } else {
            sendTransferFailedEmail(event);
        }
    }

    private void sendTransferSuccessEmails(TransactionCompletedEvent event) {
        // Send to sender
        if (event.getSenderEmail() != null && !event.getSenderEmail().isBlank()) {
            boolean alreadySent = notificationRepository
                    .existsByTransactionRefAndNotificationType(
                            event.getTransactionRef(),
                            Notification.NotificationType.TRANSFER_SUCCESS);
            if (!alreadySent) {
                String subject = emailTemplateService
                        .buildTransferSuccessSubject(event);
                String body = emailTemplateService
                        .buildTransferSuccessSenderBody(event);
                sendAndSave(
                        event.getSenderEmail(), null,
                        Notification.NotificationType.TRANSFER_SUCCESS,
                        subject, body, event.getTransactionRef());
            }
        } else {
            log.warn("No sender email in TransactionCompletedEvent for ref: {}",
                    event.getTransactionRef());
        }

        // Send to receiver
        if (event.getReceiverEmail() != null
                && !event.getReceiverEmail().isBlank()) {
            String subject = "You received " +
                    (event.getAmount() != null
                            ? "₹" + String.format("%,.2f", event.getAmount())
                            : "a transfer");
            String body = emailTemplateService.buildTransferReceivedBody(event);

            // Use a different "type" suffix for receiver so idempotency key differs
            // We store the receiver notification as a second row
            sendAndSave(
                    event.getReceiverEmail(), null,
                    Notification.NotificationType.TRANSFER_SUCCESS,
                    subject, body,
                    event.getTransactionRef() + "_receiver");
        }
    }

    private void sendTransferFailedEmail(TransactionCompletedEvent event) {
        if (event.getSenderEmail() == null || event.getSenderEmail().isBlank()) {
            log.warn("No sender email in failed TransactionCompletedEvent for ref: {}",
                    event.getTransactionRef());
            return;
        }

        boolean alreadySent = notificationRepository
                .existsByTransactionRefAndNotificationType(
                        event.getTransactionRef(),
                        Notification.NotificationType.TRANSFER_FAILED);
        if (alreadySent) {
            log.warn("Transfer failed email already sent for ref: {}",
                    event.getTransactionRef());
            return;
        }

        String subject = emailTemplateService.buildTransferFailedSubject(event);
        String body    = emailTemplateService.buildTransferFailedBody(event);

        sendAndSave(
                event.getSenderEmail(), null,
                Notification.NotificationType.TRANSFER_FAILED,
                subject, body, event.getTransactionRef());
    }

    // ── Fraud Alert Email ──────────────────────────────────────────────────

    @Transactional
    public void sendFraudAlertEmail(FraudAlertEvent event) {
        log.info("Sending fraud alert email — ref: {}, user: {}, score: {}",
                event.getTransactionRef(), event.getUserUuid(),
                event.getRiskScore());

        boolean alreadySent = notificationRepository
                .existsByTransactionRefAndNotificationType(
                        event.getTransactionRef(),
                        Notification.NotificationType.FRAUD_ALERT);
        if (alreadySent) {
            log.warn("Fraud alert email already sent for ref: {}",
                    event.getTransactionRef());
            return;
        }

        // FraudAlertEvent has userEmail field from common-lib
        if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
            log.warn("No user email in FraudAlertEvent for ref: {}. Cannot send alert.",
                    event.getTransactionRef());
            return;
        }

        String subject = emailTemplateService.buildFraudAlertSubject();
        String body    = emailTemplateService.buildFraudAlertBody(event);

        sendAndSave(
                event.getUserEmail(), event.getUserUuid(),
                Notification.NotificationType.FRAUD_ALERT,
                subject, body, event.getTransactionRef());
    }

    // ── Admin Query ────────────────────────────────────────────────────────

    public List<NotificationResponse> getNotificationsForUser(String userUuid) {
        log.debug("Fetching notifications for user: {}", userUuid);
        return notificationRepository
                .findByRecipientUuidOrderByCreatedAtDesc(userUuid)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Core send + save logic ─────────────────────────────────────────────

    // Single method that sends the email AND persists the result.
    // If sending succeeds: saves with status=SENT and sentAt timestamp.
    // If sending fails: saves with status=FAILED and failureReason.
    // This guarantees we ALWAYS have an audit record, success or failure.
    private void sendAndSave(
            String recipientEmail,
            String recipientUuid,
            Notification.NotificationType type,
            String subject,
            String body,
            String transactionRef) {

        // Build the notification record first — before attempting to send
        Notification notification = Notification.builder()
                .recipientEmail(recipientEmail)
                .recipientUuid(recipientUuid)
                .notificationType(type)
                .subject(subject)
                .body(body)
                .transactionRef(transactionRef)
                .status(Notification.NotificationStatus.FAILED) // assume failure until proven success
                .build();

        try {
            // Attempt to send the email
            sendHtmlEmail(recipientEmail, subject, body);

            // Email sent successfully — update status
            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());

            log.info("Email sent successfully to: {} type: {} ref: {}",
                    recipientEmail, type, transactionRef);

        } catch (Exception e) {
            // Email failed — record the reason but do NOT rethrow.
            // The Kafka consumer must not fail because of email issues.
            // We save the failed attempt for ops team to investigate.
            notification.setFailureReason(
                    e.getMessage() != null
                            ? e.getMessage().substring(0,
                            Math.min(e.getMessage().length(), 490))
                            : "Unknown email error");

            log.error("Failed to send email to: {} type: {} ref: {} error: {}",
                    recipientEmail, type, transactionRef, e.getMessage());
        }

        // Save regardless of success or failure — always audit
        notificationRepository.save(notification);
    }

    // Sends a MIME HTML email using Spring's JavaMailSender
    private void sendHtmlEmail(String to, String subject, String htmlBody)
            throws MessagingException, UnsupportedEncodingException {

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        // MimeMessageHelper wraps MimeMessage with a cleaner API.
        // true = multipart message (required for HTML emails)
        // "UTF-8" = character encoding for the email body
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        // true = this is HTML content, not plain text
        helper.setText(htmlBody, true);

        // JavaMailSender connects to SMTP server (Mailtrap in dev) and delivers
        mailSender.send(mimeMessage);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .uuid(n.getUuid())
                .recipientEmail(n.getRecipientEmail())
                .notificationType(n.getNotificationType().name())
                .subject(n.getSubject())
                .status(n.getStatus().name())
                .failureReason(n.getFailureReason())
                .transactionRef(n.getTransactionRef())
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .build();
    }
}