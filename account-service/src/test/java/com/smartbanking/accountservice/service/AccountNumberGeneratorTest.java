package com.smartbanking.accountservice.service;

import com.smartbanking.accountservice.entity.AccountSequence;
import com.smartbanking.accountservice.repository.AccountSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Pure unit test — no Spring, no DB.
// Tests account number generation logic in complete isolation.

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountNumberGenerator Unit Tests")
class AccountNumberGeneratorTest {

    @Mock
    private AccountSequenceRepository sequenceRepository;

    @InjectMocks
    private AccountNumberGenerator generator;

    @Test
    @DisplayName("Should generate SBP0000000001 when sequence value is 0")
    void shouldGenerateFirstAccountNumber() {
        // Given — sequence exists with currentValue=0
        AccountSequence sequence = new AccountSequence(1L, 0L);
        when(sequenceRepository.findByIdWithLock())
                .thenReturn(Optional.of(sequence));
        // save returns the updated sequence
        when(sequenceRepository.save(any(AccountSequence.class)))
                .thenReturn(sequence);

        // When
        String accountNumber = generator.generate();

        // Then
        assertThat(accountNumber).isEqualTo("SBP0000000001");
        // Verify we incremented the sequence
        assertThat(sequence.getCurrentValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should generate SBP0000000042 when sequence value is 41")
    void shouldGenerateCorrectlyForHighSequenceValue() {
        AccountSequence sequence = new AccountSequence(1L, 41L);
        when(sequenceRepository.findByIdWithLock())
                .thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(AccountSequence.class)))
                .thenReturn(sequence);

        String accountNumber = generator.generate();

        assertThat(accountNumber).isEqualTo("SBP0000000042");
    }

    @Test
    @DisplayName("Should format correctly with zero padding to 10 digits")
    void shouldZeroPadCorrectly() {
        AccountSequence sequence = new AccountSequence(1L, 999999998L);
        when(sequenceRepository.findByIdWithLock())
                .thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(AccountSequence.class)))
                .thenReturn(sequence);

        String accountNumber = generator.generate();

        // 999999999 still fits in 10 digits
        assertThat(accountNumber).isEqualTo("SBP0999999999");
        assertThat(accountNumber).startsWith("SBP");
        assertThat(accountNumber).hasSize(13); // "SBP" (3) + 10 digits
    }

    @Test
    @DisplayName("Should initialize sequence when no row exists")
    void shouldInitializeSequenceWhenEmpty() {
        // No row in DB yet (first ever account)
        when(sequenceRepository.findByIdWithLock())
                .thenReturn(Optional.empty());

        AccountSequence newSequence = new AccountSequence(1L, 0L);
        // First save = creating the initial row
        when(sequenceRepository.save(any(AccountSequence.class)))
                .thenReturn(newSequence);

        String accountNumber = generator.generate();

        assertThat(accountNumber).isEqualTo("SBP0000000001");
        // save() called twice — once to create the row, once to increment it
        verify(sequenceRepository, times(2)).save(any(AccountSequence.class));
    }

    @Test
    @DisplayName("Account number should always start with SBP")
    void accountNumberShouldAlwaysStartWithSBP() {
        AccountSequence sequence = new AccountSequence(1L, 100L);
        when(sequenceRepository.findByIdWithLock())
                .thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(AccountSequence.class)))
                .thenReturn(sequence);

        String accountNumber = generator.generate();

        assertThat(accountNumber).startsWith("SBP");
        assertThat(accountNumber).hasSize(13);
    }
}