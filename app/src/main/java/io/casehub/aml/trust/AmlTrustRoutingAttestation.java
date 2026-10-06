package io.casehub.aml.trust;

import io.casehub.ledger.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * Layer 7: captures trust score at routing time per capability.
 * Written by AmlTrustRoutingObserver on each WorkerDecisionEvent.
 * trustScoreAtRouting is nullable — null means TrustScoreCache had no entry at routing time.
 * Workaround for casehubio/engine#403 (WorkerDecisionEntry missing these fields).
 */
@Entity
@Table(name = "aml_trust_routing_attestation")
@DiscriminatorValue("AML_TRUST_ROUTING")
public class AmlTrustRoutingAttestation extends JpaLedgerEntry {

    @Column(name = "capability_tag", nullable = false, length = 100)
    public String capabilityTag;

    @Column(name = "selected_worker_id", nullable = false)
    public String selectedWorkerId;

    @Column(name = "trust_score_at_routing")
    public Double trustScoreAtRouting;

    @Column(name = "threshold_applied", nullable = false)
    public double thresholdApplied;

    @Column(name = "investigation_case_id", nullable = false)
    public UUID investigationCaseId;

    @Column(name = "reconstructed", nullable = false)
    public boolean reconstructed = false;

    @Column(name = "observer_failed", nullable = false)
    public boolean observerFailed = false;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
            capabilityTag != null ? capabilityTag : "",
            selectedWorkerId != null ? selectedWorkerId : "",
            trustScoreAtRouting != null ? String.valueOf(trustScoreAtRouting) : "",
            String.valueOf(thresholdApplied),
            investigationCaseId != null ? investigationCaseId.toString() : "",
            String.valueOf(reconstructed),
            String.valueOf(observerFailed)
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
