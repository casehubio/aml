package io.casehub.aml.ledger;

import io.casehub.ledger.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Layer 8: dedicated ledger entry for the CASE_OPENED event.
 *
 * <p>Replaces the dual-use {@code AmlInvestigationLedgerEntry} (discriminator {@code AML_INVESTIGATION})
 * for case-opening events. Each field maps to a dedicated column in the join table
 * {@code aml_case_opened_ledger_entry} (Flyway V2007).
 *
 * <p>{@code subjectId} on this entry equals the case UUID, shared with all other
 * ledger entries for the same investigation.
 */
@Entity
@Table(name = "aml_case_opened_ledger_entry")
@DiscriminatorValue("AML_CASE_OPENED")
public class AmlCaseOpenedLedgerEntry extends JpaLedgerEntry {

    /** External transaction reference — e.g. {@code "TXN-2024-001"}. */
    @Column(name = "transaction_id", nullable = false, length = 255)
    public String transactionId;

    /**
     * Source account of the flagged transaction.
     * Used by {@link io.casehub.aml.memory.AmlSarOutcomeMemoryObserver} to write
     * SAR outcome memories under both account IDs without requiring a separate DB query.
     */
    @Column(name = "origin_account_id", nullable = false, length = 255)
    public String originAccountId;

    /** Destination account of the flagged transaction. @see #originAccountId */
    @Column(name = "destination_account_id", nullable = false, length = 255)
    public String destinationAccountId;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
            transactionId != null ? transactionId : "",
            originAccountId != null ? originAccountId : "",
            destinationAccountId != null ? destinationAccountId : ""
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
