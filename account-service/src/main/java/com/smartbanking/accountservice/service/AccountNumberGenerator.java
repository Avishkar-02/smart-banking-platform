package com.smartbanking.accountservice.service;

import com.smartbanking.accountservice.entity.AccountSequence;
import com.smartbanking.accountservice.repository.AccountSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private static final String PREFIX = "SBP";
    // SBP - Smart Banking Platform

    // SBP + 0000000001 = SBP0000000001
    private static final int SEQUENCE_LENGTH = 10;

    private final AccountSequenceRepository sequenceRepository;

    // @Transactional is CRITICAL here.
    // The SELECT FOR UPDATE lock (in findByIdWithLock) is only held
    // for the duration of a transaction. Without @Transactional, the
    // lock releases immediately after the SELECT — before we can UPDATE.
    // Other threads would then read the same value.
    // With @Transactional: lock held from SELECT → INCREMENT → UPDATE → COMMIT.
    // Atomic and safe under any concurrency.
    @Transactional
    public String generate() {
        log.debug("Generating new account number...");


        AccountSequence sequence = sequenceRepository
                .findByIdWithLock()
                .orElseGet(() -> {
                    log.info("No sequence row found — initializing with value 0");
                    return sequenceRepository.save(
                            new AccountSequence(1L, 0L));
                });

        // Increment the sequence value
        long nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);

        // Save the incremented value — this commits with the transaction
        sequenceRepository.save(sequence);

        // Format: PREFIX + zero-padded sequence number
        // e.g. nextValue=1 → "0000000001" → "SBP0000000001"
        String accountNumber = PREFIX +
                String.format("%0" + SEQUENCE_LENGTH + "d", nextValue);

        log.info("Generated account number: {}", accountNumber);
        return accountNumber;
    }
}