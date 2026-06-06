package com.smartbanking.frauddetectionservice.repository;

import com.smartbanking.frauddetectionservice.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository
        extends JpaRepository<FraudAlert, Long> {

    // All alerts for a specific user — compliance review
    List<FraudAlert> findByUserUuidOrderByCreatedAtDesc(String userUuid);

    // Most recent unresolved alerts — admin dashboard "work queue"
    // Pageable allows limiting results — we pass PageRequest.of(0, 50)
    List<FraudAlert> findByResolvedFalseOrderByCreatedAtDesc(
            Pageable pageable);

    // Count unresolved alerts — for dashboard statistics
    long countByResolvedFalse();
}