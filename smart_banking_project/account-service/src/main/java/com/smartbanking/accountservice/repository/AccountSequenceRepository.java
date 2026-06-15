package com.smartbanking.accountservice.repository;

import com.smartbanking.accountservice.entity.AccountSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AccountSequenceRepository
        extends JpaRepository<AccountSequence, Long> {

    // @Lock(PESSIMISTIC_WRITE) translates to: SELECT ... FOR UPDATE in MySQL.
    // When one thread holds this lock, ALL other threads block on this query.
    // They wait until the first thread commits its transaction.
    // This guarantees: no two threads ever read the same sequence value.
    // Without this lock, under concurrent requests:
    // Thread A reads currentValue=5, Thread B reads currentValue=5,
    // both generate SBP0000000006, one INSERT fails with duplicate key error.
    // With this lock: Thread A reads 5 → increments to 6 → commits.
    // Then Thread B reads 6 → increments to 7 → commits. No collision.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AccountSequence s WHERE s.id = 1")
    Optional<AccountSequence> findByIdWithLock();
}