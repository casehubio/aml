package io.casehub.aml.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "aml_cbr_advisory_ledger_entry")
@DiscriminatorValue("AML_CBR_ADVISORY")
public class AmlCbrAdvisoryLedgerEntry extends JpaLedgerEntry {

    @Column(name = "case_count", nullable = false)
    public int caseCount;

    @Column(name = "avg_similarity", nullable = false)
    public double avgSimilarity;

    @Column(name = "confidence", nullable = false)
    public double confidence;

    @Column(name = "predominant_outcome", length = 50)
    public String predominantOutcome;

    @Column(name = "predominant_outcome_frequency")
    public Double predominantOutcomeFrequency;

    @Column(name = "recommended_capabilities", length = 1000)
    public String recommendedCapabilities;
    @Column(name = "active", nullable = false)
    public boolean active;


    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                           String.valueOf(caseCount),
                           String.valueOf(avgSimilarity),
                           String.valueOf(confidence),
                           predominantOutcome != null ? predominantOutcome : "",
                           predominantOutcomeFrequency != null ? String.valueOf(predominantOutcomeFrequency) : "",
                           recommendedCapabilities != null ? recommendedCapabilities : "",
                           String.valueOf(active)
                          ).getBytes(StandardCharsets.UTF_8);}
}
