package com.smartbanking.frauddetectionservice.repository;

import com.smartbanking.frauddetectionservice.entity.FraudEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudEvaluationRepository
        extends JpaRepository<FraudEvaluation, Long> {

    // Used by the consumer to check for duplicate events before processing.
    // If transactionRef already exists, we already evaluated this transaction.
    Optional<FraudEvaluation> findByTransactionRef(String transactionRef);

    // Used for checking if a transactionRef was already processed.
    // Faster than findByTransactionRef when we only need the boolean answer.
    boolean existsByTransactionRef(String transactionRef);

    // Admin endpoint — see all evaluations for a user
    List<FraudEvaluation> findByUserUuidOrderByEvaluatedAtDesc(String userUuid);
}