package io.casehub.aml.ledger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AmlSupervisorDecisionLedgerEntryTest {

    @Test
    void domainContentBytes_all_fields() {
        var entry = new AmlSupervisorDecisionLedgerEntry();
        entry.selectedBindings = "pattern-analysis,osint-screening";
        entry.suppressedBindings = "sar-drafting";
        entry.rationale = "parallel first";
        entry.earlyTermination = false;
        entry.eligibleCount = 3;
        entry.degraded = false;

        String content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(
                "pattern-analysis,osint-screening|sar-drafting|parallel first|false|3|false");
    }

    @Test
    void domainContentBytes_null_suppressed() {
        var entry = new AmlSupervisorDecisionLedgerEntry();
        entry.selectedBindings = "investigation-triage";
        entry.suppressedBindings = null;
        entry.rationale = "early termination";
        entry.earlyTermination = true;
        entry.eligibleCount = 2;
        entry.degraded = false;

        String content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(
                "investigation-triage||early termination|true|2|false");
    }

    @Test
    void domainContentBytes_degraded() {
        var entry = new AmlSupervisorDecisionLedgerEntry();
        entry.selectedBindings = "pattern-analysis,osint-screening";
        entry.suppressedBindings = "";
        entry.rationale = "LLM unavailable — degraded to choreography";
        entry.earlyTermination = false;
        entry.eligibleCount = 2;
        entry.degraded = true;

        String content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertThat(content).contains("|true");
        assertThat(content).endsWith("|2|true");
    }
}
