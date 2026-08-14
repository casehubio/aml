package io.casehub.aml.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "aml_supervisor_decision_ledger_entry")
@DiscriminatorValue("AML_SUPERVISOR_DECISION")
public class AmlSupervisorDecisionLedgerEntry extends JpaLedgerEntry {

    @Column(name = "selected_bindings", nullable = false, length = 500)
    public String selectedBindings;

    @Column(name = "suppressed_bindings", length = 500)
    public String suppressedBindings;

    @Column(name = "rationale", nullable = false, length = 2000)
    public String rationale;

    @Column(name = "early_termination", nullable = false)
    public boolean earlyTermination;

    @Column(name = "eligible_count", nullable = false)
    public int eligibleCount;

    @Column(name = "degraded", nullable = false)
    public boolean degraded;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                selectedBindings,
                suppressedBindings != null ? suppressedBindings : "",
                rationale,
                String.valueOf(earlyTermination),
                String.valueOf(eligibleCount),
                String.valueOf(degraded)
        ).getBytes(StandardCharsets.UTF_8);
    }
}
