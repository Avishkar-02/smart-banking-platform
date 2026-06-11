package com.smartbanking.notificationservice.service;

import com.smartbanking.common.event.FraudAlertEvent;
import com.smartbanking.common.event.TransactionCompletedEvent;
import com.smartbanking.common.event.UserRegisteredEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

// EmailTemplateService builds the HTML email bodies.
// Keeping templates separate from business logic means:
// - You can change the email design without touching NotificationService

@Service
public class EmailTemplateService {

    // Format numbers as Indian currency — ₹5,000.00
    private static final NumberFormat INR_FORMAT =
            NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    // ── Welcome Email ─────────────────────────────────────────────────────

    public String buildWelcomeSubject(UserRegisteredEvent event) {
        return "Welcome to Smart Banking Platform, " + event.getFirstName() + "!";
    }

    public String buildWelcomeBody(UserRegisteredEvent event) {
        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #1B4F8A; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Smart Banking Platform</h1>
                    </div>
                    <div style="padding: 30px;">
                        <h2>Welcome, %s!</h2>
                        <p>Your account has been created successfully.</p>
                        <p>Here are your account details:</p>
                        <ul>
                            <li><strong>Name:</strong> %s %s</li>
                            <li><strong>Email:</strong> %s</li>
                            <li><strong>Account UUID:</strong> %s</li>
                        </ul>
                        <p>You can now create bank accounts and start transacting.</p>
                        <p style="color: #666; font-size: 12px;">
                            If you did not create this account, please contact support immediately.
                        </p>
                    </div>
                </body></html>
                """.formatted(
                event.getFirstName(),
                event.getFirstName(), event.getLastName(),
                event.getEmail(),
                event.getUserUuid());
    }

    // ── Transfer Success Email ─────────────────────────────────────────────

    public String buildTransferSuccessSubject(TransactionCompletedEvent event) {
        return "Transfer Successful — " + formatAmount(event.getAmount(),
                event.getCurrency());
    }

    public String buildTransferSuccessSenderBody(
            TransactionCompletedEvent event) {
        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #1E8449; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Transfer Successful ✓</h1>
                    </div>
                    <div style="padding: 30px;">
                        <p>Your transfer has been completed successfully.</p>
                        <div style="background-color: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <table style="width: 100%%;">
                                <tr><td><strong>Amount:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>From:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>To:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>Reference:</strong></td>
                                    <td>%s</td></tr>
                            </table>
                        </div>
                        <p style="color: #666; font-size: 12px;">
                            Keep this reference number for your records.
                        </p>
                    </div>
                </body></html>
                """.formatted(
                formatAmount(event.getAmount(), event.getCurrency()),
                event.getSourceAccountNumber(),
                event.getDestinationAccountNumber(),
                event.getTransactionRef());
    }

    public String buildTransferReceivedBody(TransactionCompletedEvent event) {
        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #1B4F8A; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Money Received ✓</h1>
                    </div>
                    <div style="padding: 30px;">
                        <p>You have received a transfer.</p>
                        <div style="background-color: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <table style="width: 100%%;">
                                <tr><td><strong>Amount:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>From:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>To your account:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>Reference:</strong></td>
                                    <td>%s</td></tr>
                            </table>
                        </div>
                    </div>
                </body></html>
                """.formatted(
                formatAmount(event.getAmount(), event.getCurrency()),
                event.getSourceAccountNumber(),
                event.getDestinationAccountNumber(),
                event.getTransactionRef());
    }

    // ── Transfer Failed Email ──────────────────────────────────────────────

    public String buildTransferFailedSubject(TransactionCompletedEvent event) {
        return "Transfer Failed — " + formatAmount(event.getAmount(),
                event.getCurrency());
    }

    public String buildTransferFailedBody(TransactionCompletedEvent event) {
        String reason = event.getFailureReason() != null
                ? event.getFailureReason()
                : "Transfer could not be processed";

        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #C0392B; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Transfer Failed</h1>
                    </div>
                    <div style="padding: 30px;">
                        <p>Your transfer could not be completed. <strong>Your funds are safe.</strong></p>
                        <div style="background-color: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <table style="width: 100%%;">
                                <tr><td><strong>Amount:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>From:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>To:</strong></td>
                                    <td>%s</td></tr>
                                <tr><td><strong>Reason:</strong></td>
                                    <td style="color: #C0392B;">%s</td></tr>
                                <tr><td><strong>Reference:</strong></td>
                                    <td>%s</td></tr>
                            </table>
                        </div>
                        <p>If you believe this is an error, please contact our support team.</p>
                    </div>
                </body></html>
                """.formatted(
                formatAmount(event.getAmount(), event.getCurrency()),
                event.getSourceAccountNumber(),
                event.getDestinationAccountNumber(),
                reason,
                event.getTransactionRef());
    }

    // ── Fraud Alert Email ──────────────────────────────────────────────────

    public String buildFraudAlertSubject() {
        return "Security Alert: Suspicious Activity Detected on Your Account";
    }

    public String buildFraudAlertBody(FraudAlertEvent event) {
        String actionText = "BLOCK".equals(event.getRecommendedAction())
                ? "This transaction has been <strong>blocked</strong> for your protection."
                : "This transaction has been <strong>flagged for review</strong> by our security team.";

        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #D35400; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">⚠ Security Alert</h1>
                    </div>
                    <div style="padding: 30px;">
                        <p>We detected suspicious activity on your account.</p>
                        <div style="background-color: #FEF9E7; border: 1px solid #D35400;
                                    padding: 20px; border-radius: 8px;">
                            <p><strong>Alert Type:</strong> %s</p>
                            <p><strong>Risk Level:</strong> %s (score: %d/100)</p>
                            <p><strong>Transaction Reference:</strong> %s</p>
                            <p>%s</p>
                        </div>
                        <p>If this was you, no action is needed.</p>
                        <p>If you did not initiate this transaction, please contact us immediately.</p>
                        <p style="color: #666; font-size: 12px;">
                            Smart Banking Platform Security Team
                        </p>
                    </div>
                </body></html>
                """.formatted(
                event.getAlertType(),
                getRiskLevel(event.getRiskScore()),
                event.getRiskScore(),
                event.getTransactionRef(),
                actionText);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String formatAmount(BigDecimal amount, String currency) {
        if ("INR".equals(currency)) {
            return "₹" + String.format("%,.2f", amount);
        }
        return currency + " " + String.format("%,.2f", amount);
    }

    private String getRiskLevel(int score) {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }
}