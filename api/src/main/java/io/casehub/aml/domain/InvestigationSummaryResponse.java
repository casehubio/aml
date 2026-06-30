package io.casehub.aml.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvestigationSummaryResponse(
    UUID caseId,
    String status,
    String outcomeType,
    String transactionId,
    String originAccount,
    String destinationAccount,
    BigDecimal amount,
    String currency,
    String flagReason,
    Instant createdAt
) {}
