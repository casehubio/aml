package io.casehub.aml.query;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CQRS-lite read model for AML investigation list queries.
 * <p>
 * Populated by {@code InvestigationSummaryEventObserver} (Task 2), queried by
 * {@code InvestigationListResource} (Task 3).
 * <p>
 * This is a denormalized projection — not a ledger entry. Lives on the default
 * persistence unit alongside WorkItem.
 */
@Entity
@Table(name = "aml_investigation_summary")
public class InvestigationSummaryView {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "outcome_type", length = 64)
    private String outcomeType;

    @Column(name = "transaction_id", nullable = false, length = 128)
    private String transactionId;

    @Column(name = "origin_account", nullable = false, length = 128)
    private String originAccount;

    @Column(name = "dest_account", nullable = false, length = 128)
    private String destinationAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "flag_reason", nullable = false, length = 128)
    private String flagReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected InvestigationSummaryView() {}

    /**
     * Create a new investigation summary view.
     * <p>
     * Initial status is {@code IN_PROGRESS}. Call {@link #updateStatus(String)} and
     * {@link #updateOutcomeType(String)} to reflect case completion.
     *
     * @param caseId case instance ID (engine)
     * @param transactionId flagged transaction ID
     * @param originAccount source account
     * @param destinationAccount destination account
     * @param amount transaction amount
     * @param currency currency code (e.g. "USD")
     * @param flagReason why the transaction was flagged
     */
    public InvestigationSummaryView(UUID caseId, String transactionId,
            String originAccount, String destinationAccount,
            BigDecimal amount, String currency, String flagReason) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.transactionId = transactionId;
        this.originAccount = originAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.flagReason = flagReason;
        this.status = "IN_PROGRESS";
        this.createdAt = Instant.now();
    }

    // ────────────────────────────────────────────────────────────────────
    // Getters — record-style naming (no "get" prefix)
    // ────────────────────────────────────────────────────────────────────

    public UUID id() { return id; }

    public UUID caseId() { return caseId; }

    public String status() { return status; }

    public String outcomeType() { return outcomeType; }

    public String transactionId() { return transactionId; }

    public String originAccount() { return originAccount; }

    public String destinationAccount() { return destinationAccount; }

    public BigDecimal amount() { return amount; }

    public String currency() { return currency; }

    public String flagReason() { return flagReason; }

    public Instant createdAt() { return createdAt; }

    // ────────────────────────────────────────────────────────────────────
    // Update methods — status/outcome only (write path controlled by observer)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Update investigation status.
     * <p>
     * Expected values: {@code IN_PROGRESS}, {@code COMPLETED}, {@code CANCELLED}.
     */
    public void updateStatus(String status) {
        this.status = status;
    }

    /**
     * Update investigation outcome type.
     * <p>
     * Expected values: {@code SAR_FILED}, {@code SAR_DECLINED}, {@code ESCALATED}, null.
     */
    public void updateOutcomeType(String outcomeType) {
        this.outcomeType = outcomeType;
    }
}
