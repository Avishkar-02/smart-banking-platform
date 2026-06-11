package com.smartbanking.notificationservice.service;

import com.smartbanking.common.event.FraudAlertEvent;
import com.smartbanking.common.event.TransactionCompletedEvent;
import com.smartbanking.common.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit test — no Spring context, no mocks needed.
// EmailTemplateService has no dependencies — just pure string building.
// Tests verify the generated HTML contains the right data.

@DisplayName("EmailTemplateService Unit Tests")
class EmailTemplateServiceTest {

    private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        emailTemplateService = new EmailTemplateService();
    }

    @Test
    @DisplayName("Welcome subject should include recipient first name")
    void welcomeSubjectShouldIncludeFirstName() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .lastName("Suryawanshi")
                .build();

        String subject = emailTemplateService.buildWelcomeSubject(event);

        assertThat(subject).contains("Avishkar");
        assertThat(subject).containsIgnoringCase("welcome");
    }

    @Test
    @DisplayName("Welcome body should contain user details")
    void welcomeBodyShouldContainUserDetails() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .lastName("Suryawanshi")
                .build();

        String body = emailTemplateService.buildWelcomeBody(event);

        assertThat(body).contains("Avishkar");
        assertThat(body).contains("avishkar@test.com");
        assertThat(body).contains("uuid-123");
        // Should be HTML
        assertThat(body).contains("<html>");
    }

    @Test
    @DisplayName("Transfer success subject should include formatted amount")
    void transferSuccessSubjectShouldIncludeAmount() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionRef("TXN-001")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .finalStatus("COMPLETED")
                .build();

        String subject = emailTemplateService.buildTransferSuccessSubject(event);

        assertThat(subject).containsIgnoringCase("successful");
        assertThat(subject).contains("5,000.00");
    }

    @Test
    @DisplayName("Transfer success sender body should include account numbers")
    void transferSuccessBodyShouldIncludeAccountNumbers() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionRef("TXN-001")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .finalStatus("COMPLETED")
                .build();

        String body = emailTemplateService.buildTransferSuccessSenderBody(event);

        assertThat(body).contains("SBP0000000001");
        assertThat(body).contains("SBP0000000002");
        assertThat(body).contains("TXN-001");
        assertThat(body).contains("5,000.00");
    }

    @Test
    @DisplayName("Transfer failed body should state funds are safe")
    void transferFailedBodyShouldStateFundsAreSafe() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionRef("TXN-002")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .finalStatus("FAILED")
                .failureReason("Insufficient balance")
                .build();

        String body = emailTemplateService.buildTransferFailedBody(event);

        assertThat(body).containsIgnoringCase("funds are safe");
        assertThat(body).contains("Insufficient balance");
    }

    @Test
    @DisplayName("Fraud alert body should include risk score and alert type")
    void fraudAlertBodyShouldIncludeRiskDetails() {
        FraudAlertEvent event = FraudAlertEvent.builder()
                .transactionRef("TXN-003")
                .userUuid("uuid-123")
                .userEmail("avishkar@test.com")
                .riskScore(85)
                .alertType("HIGH_VELOCITY")
                .description("6 transactions in 60 seconds")
                .recommendedAction("BLOCK")
                .build();

        String body = emailTemplateService.buildFraudAlertBody(event);

        assertThat(body).contains("HIGH_VELOCITY");
        assertThat(body).contains("85");
        assertThat(body).containsIgnoringCase("blocked");
    }
}