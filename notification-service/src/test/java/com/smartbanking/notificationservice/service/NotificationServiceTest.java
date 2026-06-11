package com.smartbanking.notificationservice.service;

import com.smartbanking.common.event.TransactionCompletedEvent;
import com.smartbanking.common.event.UserRegisteredEvent;
import com.smartbanking.notificationservice.entity.Notification;
import com.smartbanking.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private EmailTemplateService emailTemplateService;

    @InjectMocks private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService,
                "fromEmail", "noreply@smartbanking.com");
        ReflectionTestUtils.setField(notificationService,
                "fromName", "Smart Banking Platform");
    }

    // ── WELCOME EMAIL ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should send welcome email and save SENT notification")
    void shouldSendWelcomeEmailSuccessfully() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .lastName("Suryawanshi")
                .build();

        // Not a duplicate
        when(notificationRepository.existsByTransactionRefAndNotificationType(
                anyString(), any())).thenReturn(false);

        when(emailTemplateService.buildWelcomeSubject(any()))
                .thenReturn("Welcome, Avishkar!");
        when(emailTemplateService.buildWelcomeBody(any()))
                .thenReturn("<html>Welcome body</html>");

        // Mock MimeMessage creation
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.sendWelcomeEmail(event);

        // Verify email was sent
        verify(mailSender).send(any(MimeMessage.class));

        // Verify notification saved with SENT status
        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus())
                .isEqualTo(Notification.NotificationStatus.SENT);
        assertThat(captor.getValue().getRecipientEmail())
                .isEqualTo("avishkar@test.com");
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(Notification.NotificationType.WELCOME);
        assertThat(captor.getValue().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("Should skip duplicate welcome email")
    void shouldSkipDuplicateWelcomeEmail() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .build();

        // Already sent — duplicate
        when(notificationRepository.existsByTransactionRefAndNotificationType(
                anyString(), any())).thenReturn(true);

        notificationService.sendWelcomeEmail(event);

        // Email should NOT be sent
        verify(mailSender, never()).createMimeMessage();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should save FAILED notification when email sending throws exception")
    void shouldSaveFailedNotificationOnEmailError() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .build();

        when(notificationRepository.existsByTransactionRefAndNotificationType(
                anyString(), any())).thenReturn(false);
        when(emailTemplateService.buildWelcomeSubject(any()))
                .thenReturn("Welcome!");
        when(emailTemplateService.buildWelcomeBody(any()))
                .thenReturn("<html>body</html>");

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        // Simulate email server being down
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        // Should NOT throw even though email failed
        notificationService.sendWelcomeEmail(event);

        // Verify FAILED notification was saved with reason
        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus())
                .isEqualTo(Notification.NotificationStatus.FAILED);
        assertThat(captor.getValue().getFailureReason())
                .contains("SMTP connection refused");
        assertThat(captor.getValue().getSentAt()).isNull();
    }

    // ── TRANSACTION COMPLETED ─────────────────────────────────────────────

    @Test
    @DisplayName("Should send success email for COMPLETED transaction")
    void shouldSendSuccessEmailForCompletedTransaction() throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionRef("TXN-001")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .finalStatus("COMPLETED")
                .senderEmail("sender@test.com")
                .receiverEmail("receiver@test.com")
                .build();

        when(notificationRepository.existsByTransactionRefAndNotificationType(
                anyString(), any())).thenReturn(false);
        when(emailTemplateService.buildTransferSuccessSubject(any()))
                .thenReturn("Transfer Successful");
        when(emailTemplateService.buildTransferSuccessSenderBody(any()))
                .thenReturn("<html>Success</html>");
        when(emailTemplateService.buildTransferReceivedBody(any()))
                .thenReturn("<html>Received</html>");

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.sendTransactionCompletedEmail(event);

        // Two emails sent — one to sender, one to receiver
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should send failure email for FAILED transaction")
    void shouldSendFailureEmailForFailedTransaction() throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionRef("TXN-002")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .finalStatus("FAILED")
                .failureReason("Blocked by fraud detection")
                .senderEmail("sender@test.com")
                .build();

        when(notificationRepository.existsByTransactionRefAndNotificationType(
                anyString(), any())).thenReturn(false);
        when(emailTemplateService.buildTransferFailedSubject(any()))
                .thenReturn("Transfer Failed");
        when(emailTemplateService.buildTransferFailedBody(any()))
                .thenReturn("<html>Failed body</html>");

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.sendTransactionCompletedEmail(event);

        // Only one email — to sender, not receiver (for failures)
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(Notification.NotificationType.TRANSFER_FAILED);
    }
}