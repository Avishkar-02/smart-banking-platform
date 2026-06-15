package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    private String userUuid;
    private String email;
    private String firstName;
    private String lastName;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}