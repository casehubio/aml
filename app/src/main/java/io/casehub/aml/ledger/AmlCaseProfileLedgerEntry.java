package io.casehub.aml.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "aml_case_profile_ledger_entry")
@DiscriminatorValue("AML_CASE_PROFILE")
public class AmlCaseProfileLedgerEntry extends JpaLedgerEntry {

    @Column(name = "flag_reason", nullable = false, length = 50)
    public String flagReason;

    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 4)
    public BigDecimal transactionAmount;

    @Column(name = "prior_incident_count", nullable = false)
    public int priorIncidentCount;

    @Column(name = "entity_type", length = 50)
    public String entityType;

    @Column(name = "jurisdiction_risk", length = 50)
    public String jurisdictionRisk;

    @Column(name = "network_complexity", length = 50)
    public String networkComplexity;

    @Column(name = "outcome", nullable = false, length = 50)
    public String outcome;

    @Column(name = "confidence", nullable = true)
    public Double confidence;

    @Column(name = "investigation_path", nullable = false, length = 1000)
    public String investigationPath;
    @Column(name = "narrative_seeded")
    public Boolean narrativeSeeded;

    @Column(name = "seed_count")
    public Integer seedCount;


    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                           flagReason != null ? flagReason : "",
                           transactionAmount != null ? transactionAmount.toPlainString() : "",
                           String.valueOf(priorIncidentCount),
                           entityType != null ? entityType : "",
                           jurisdictionRisk != null ? jurisdictionRisk : "",
                           networkComplexity != null ? networkComplexity : "",
                           outcome != null ? outcome : "",
                           confidence != null ? String.valueOf(confidence) : "",
                           investigationPath != null ? investigationPath : "",
                           narrativeSeeded != null ? String.valueOf(narrativeSeeded) : "",
                           seedCount != null ? String.valueOf(seedCount) : ""
                          ).getBytes(StandardCharsets.UTF_8);
    }
}
