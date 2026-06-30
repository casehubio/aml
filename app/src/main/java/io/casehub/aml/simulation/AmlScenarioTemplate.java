package io.casehub.aml.simulation;

import io.casehub.aml.domain.SuspiciousTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Scenario templates for AML investigation simulation and demo seeding.
 *
 * <p>Each template produces a {@link SuspiciousTransaction} with appropriate field
 * values that trigger deterministic behavior from the existing {@code @DefaultBean}
 * specialist workers.
 *
 * <p><strong>Build-time gating:</strong> Only available when
 * {@code casehub.aml.simulation.enabled=true}. In production builds, the simulation
 * endpoints do not exist.
 */
public enum AmlScenarioTemplate {

    /**
     * Politically Exposed Person (PEP) transaction.
     * <p>
     * Triggers senior analyst routing via {@code senior-analyst-required} binding
     * and PEP-specific OSINT screening.
     */
    PEP(
        "TX-PEP-SIM",
        "ACCT-PEP-001",
        "ACCT-DEST-PEP-001",
        new BigDecimal("75000.00"),
        "USD",
        "PEP_DETECTED"
    ),

    /**
     * Structuring pattern (multiple small transactions to avoid reporting thresholds).
     * <p>
     * Triggers pattern analysis specialist with high suspicion score.
     */
    STRUCTURING(
        "TX-STRUCT-SIM",
        "ACCT-STRUCT-001",
        "ACCT-DEST-STRUCT-001",
        new BigDecimal("9500.00"),
        "USD",
        "STRUCTURING_PATTERN"
    ),

    /**
     * Low-risk transaction (expected to clear without SAR filing).
     * <p>
     * Low amount, benign flag reason — tests happy path with no escalation.
     */
    LOW_RISK(
        "TX-LOWRISK-SIM",
        "ACCT-BENIGN-001",
        "ACCT-DEST-BENIGN-001",
        new BigDecimal("1200.00"),
        "USD",
        "ROUTINE_REVIEW"
    ),

    /**
     * System error scenario (specialist agent returns FAILURE).
     * <p>
     * Tests fallback routing and error handling when a specialist cannot complete analysis.
     */
    SYSTEM_ERROR(
        "TX-ERROR-SIM",
        "ACCT-ERROR-001",
        "ACCT-DEST-ERROR-001",
        new BigDecimal("25000.00"),
        "USD",
        "SYSTEM_FAILURE_TEST"
    ),

    /**
     * Oversight gate rejection scenario (PlannedAction rejected by compliance officer).
     * <p>
     * Triggers SAR filing PlannedAction that requires human approval via Layer 9
     * {@code ActionRiskClassifier} gate.
     */
    GATE_REJECTION(
        "TX-GATE-SIM",
        "ACCT-GATE-001",
        "ACCT-DEST-GATE-001",
        new BigDecimal("150000.00"),
        "USD",
        "HIGH_RISK_JURISDICTION"
    ),

    /**
     * GDPR erasure scenario (erasure requested post-investigation).
     * <p>
     * Demonstrates {@code LedgerErasureService} tokenization of PII in completed investigation.
     */
    GDPR_ERASED(
        "TX-GDPR-SIM",
        "ACCT-GDPR-001",
        "ACCT-DEST-GDPR-001",
        new BigDecimal("5000.00"),
        "EUR",
        "PRIVACY_ERASURE_TEST"
    ),

    /**
     * High-volume parallel processing scenario.
     * <p>
     * Tests concurrent specialist checks (entity-resolution, pattern-analysis, osint-screening
     * running simultaneously).
     */
    HIGH_VOLUME(
        "TX-HIGHVOL-SIM",
        "ACCT-HIGHVOL-001",
        "ACCT-DEST-HIGHVOL-001",
        new BigDecimal("500000.00"),
        "USD",
        "LARGE_VOLUME_WIRE"
    );

    private final String transactionId;
    private final String originAccountId;
    private final String destinationAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final String flagReason;

    AmlScenarioTemplate(
            final String transactionId,
            final String originAccountId,
            final String destinationAccountId,
            final BigDecimal amount,
            final String currency,
            final String flagReason) {
        this.transactionId = transactionId;
        this.originAccountId = originAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.flagReason = flagReason;
    }

    /**
     * Generate a {@link SuspiciousTransaction} from this template.
     *
     * @return new transaction with template field values and current timestamp
     */
    public SuspiciousTransaction toTransaction() {
        return new SuspiciousTransaction(
            transactionId,
            originAccountId,
            destinationAccountId,
            amount,
            currency,
            Instant.now(),
            flagReason
        );
    }

    /**
     * Generate a {@link SuspiciousTransaction} with a unique transaction ID.
     * <p>
     * Useful for multi-run scenarios where idempotency based on {@code transactionId}
     * would prevent re-seeding the same template.
     *
     * @return new transaction with unique ID suffix
     */
    public SuspiciousTransaction toTransactionWithUniqueId() {
        final String uniqueId = transactionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new SuspiciousTransaction(
            uniqueId,
            originAccountId,
            destinationAccountId,
            amount,
            currency,
            Instant.now(),
            flagReason
        );
    }
}
