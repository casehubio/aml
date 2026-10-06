package io.casehub.aml.ledger;

import io.casehub.ledger.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Layer 8: dedicated ledger entry for the COMPLIANCE_REVIEW_OPENED event.
 *
 * <p>Replaces the dual-use {@code AmlInvestigationLedgerEntry} (discriminator {@code AML_INVESTIGATION})
 * for compliance-review events. The {@code taskId} column holds the WorkItem UUID of the
 * SAR review task created for the compliance officer.
 *
 * <p>{@code causedByEntryId} links this entry to the {@link AmlCaseOpenedLedgerEntry}
 * that opened the investigation, satisfying the FinCEN audit chain requirement.
 */
@Entity
@Table(name = "aml_compliance_review_ledger_entry")
@DiscriminatorValue("AML_COMPLIANCE_REVIEW")
public class AmlComplianceReviewLedgerEntry extends JpaLedgerEntry {

    /** WorkItem UUID of the SAR review task assigned to the compliance officer. */
    @Column(name = "task_id", nullable = false, length = 255)
    public String taskId;

    @Override
    protected byte[] domainContentBytes() {
        return (taskId != null ? taskId : "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
