package com.smartbanking.accountservice.repository;

import com.smartbanking.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Spring Data JPA reads method names and generates SQL automatically.
// findByAccountNumber → SELECT * FROM accounts WHERE account_number = ?
// findByUserUuid      → SELECT * FROM accounts WHERE user_uuid = ?
// existsByAccountNumber → SELECT COUNT(*) > 0 FROM accounts WHERE account_number = ?

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Used when client requests account by account number
    Optional<Account> findByAccountNumber(String accountNumber);

    // Used to list all accounts belonging to a user
    List<Account> findByUserUuid(String userUuid);

    // Used by AccountNumberGenerator as a safety check
    boolean existsByAccountNumber(String accountNumber);

    // Used when looking up by internal UUID
    Optional<Account> findByUuid(String uuid);
}