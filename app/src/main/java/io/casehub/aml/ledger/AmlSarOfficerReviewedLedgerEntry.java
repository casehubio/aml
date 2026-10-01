package io.casehub.aml.ledger;

import io.casehub.ledger.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Layer 9: records the compliance officer's SAR approval or rejection decision.
 *
 * <p>Written by {@link io.casehub.aml.compliance.AmlWorkItemLifecycleObserver} when the
 * compliance officer completes or rejects the SAR review WorkItem. The officer's identity
 * is stored in {@code actorId} (HUMAN) — GDPR Art.17 erasable via
 * {@link io.casehub.ledger.runtime.privacy.LedgerErasureService}.
 *
 * <p>{@code causedByEntryId} points to the {@link AmlComplianceReviewLedgerEntry} that
 * opened the review, completing the causal chain.
 */
@Entity
@Table(name = "aml_sar_officer_reviewed_ledger_entry")
@DiscriminatorValue("AML_SAR_OFFICER_REVIEWED")
public class AmlSarOfficerReviewedLedgerEntry extends JpaLedgerEntry {

    /** "APPROVED" or "REJECTED" — the officer's explicit SAR verdict. */
    @Column(name = "review_decision", nullable = false, length = 20)
    public String reviewDecision;

    /** Free-text reason when decision is REJECTED. Nullable — approved SARs have no rejection reason. */
    @Column(name = "rejection_reason", length = 1000)
    public String rejectionReason;

    @Override
    protected byte[] domainContentBytes() {
        if (rejectionReason == null) {
            return (reviewDecision != null ? reviewDecision : "")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.join("|",
                reviewDecision != null ? reviewDecision : "",
                rejectionReason).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
