package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// Published by account-service after every successful account creation.
// notification-service consumes this to send "Your account is ready" email.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {

    private String accountNumber;
    private String userUuid;
    private String accountType;
    private String currency;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}