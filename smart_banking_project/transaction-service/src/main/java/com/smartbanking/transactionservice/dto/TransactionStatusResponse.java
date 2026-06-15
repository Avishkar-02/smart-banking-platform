package com.smartbanking.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lightweight response for status polling.
// Client polls this every 2 seconds to track saga progress.
// Returns minimal data — no need to serialize full transaction.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusResponse {

    private String transactionRef;
    private String currentStatus;

    // Human-readable message about current state
    private String message;
}