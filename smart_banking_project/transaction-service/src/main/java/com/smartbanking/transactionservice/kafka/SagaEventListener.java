package com.smartbanking.transactionservice.kafka;

import com.smartbanking.common.event.*;
import com.smartbanking.transactionservice.service.TransactionSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// SagaEventListener is the event-driven brain of the saga.
// It consumes events from other services and delegates to TransactionSagaService
// to advance or compensate the saga state machine.
//
// IMPORTANT: @KafkaListener methods must NEVER throw unhandled exceptions
// that would cause Kafka to retry the message indefinitely.
// We catch all exceptions and log them — bad messages go to a dead letter queue
// (which we would configure in production).

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventListener {

    private final TransactionSagaService sagaService;

    // Consumes fraud result — first response in the saga chain
    // APPROVED → saga proceeds to debit
    // BLOCKED  → saga terminates with FAILED

    @KafkaListener(
            topics = "fraud-detection-service.fraud.result",
            groupId = "transaction-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFraudResult(
            @Payload FraudResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received FraudResultEvent — topic: {}, partition: {}, " +
                        "offset: {}, ref: {}, decision: {}",
                topic, partition, offset,
                event.getTransactionRef(), event.getDecision());

        try {
            sagaService.handleFraudResult(event);
        } catch (Exception e) {
            log.error("Failed to process FraudResultEvent for ref: {}. Error: {}",
                    event.getTransactionRef(), e.getMessage(), e);
            // In production: publish to dead letter topic for manual investigation
        }
    }

    // Consumes AccountDebitedEvent — debit step of saga confirmed

    @KafkaListener(
            topics = "account-service.account.debited",
            groupId = "transaction-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAccountDebited(
            @Payload AccountDebitedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received AccountDebitedEvent — ref: {}, account: {}, amount: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountDebited());

        try {
            sagaService.handleAccountDebited(event);
        } catch (Exception e) {
            log.error("Failed to process AccountDebitedEvent for ref: {}",
                    event.getTransactionRef(), e);
        }
    }

    // Consumes AccountCreditedEvent — credit step confirmed — saga complete

    @KafkaListener(
            topics = "account-service.account.credited",
            groupId = "transaction-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAccountCredited(
            @Payload AccountCreditedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received AccountCreditedEvent — ref: {}, account: {}, amount: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountCredited());

        try {
            sagaService.handleAccountCredited(event);
        } catch (Exception e) {
            log.error("Failed to process AccountCreditedEvent for ref: {}",
                    event.getTransactionRef(), e);
        }
    }

    // Consumes DebitReversedEvent — compensation confirmed — saga terminated

    @KafkaListener(
            topics = "account-service.debit.reversed",
            groupId = "transaction-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDebitReversed(
            @Payload DebitReversedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received DebitReversedEvent — ref: {}, account: {}, refunded: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountRefunded());

        try {
            sagaService.handleDebitReversed(event);
        } catch (Exception e) {
            log.error("Failed to process DebitReversedEvent for ref: {}",
                    event.getTransactionRef(), e);
        }
    }
}