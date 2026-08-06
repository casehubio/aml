package io.casehub.aml.ledger;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmlCaseProfileLedgerEntryTest {

    @Test
    void domainContentBytes_allFields() {
        var entry = new AmlCaseProfileLedgerEntry();
        entry.flagReason = "STRUCTURING";
        entry.transactionAmount = new BigDecimal("50000.0000");
        entry.priorIncidentCount = 3;
        entry.entityType = "SHELL_COMPANY";
        entry.jurisdictionRisk = "HIGH";
        entry.networkComplexity = "LARGE_NETWORK";
        entry.outcome = "UPHELD";
        entry.confidence = 0.92;
        entry.investigationPath = "entity-resolution → pattern-analysis → sar-drafting";

        byte[] bytes = entry.domainContentBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(
                "STRUCTURING|50000.0000|3|SHELL_COMPANY|HIGH|LARGE_NETWORK|UPHELD|0.92|entity-resolution → pattern-analysis → sar-drafting|||",
                content);
    }

    @Test
    void domainContentBytes_nullableFieldsEmpty() {
        var entry = new AmlCaseProfileLedgerEntry();
        entry.flagReason = "LAYERING";
        entry.transactionAmount = new BigDecimal("1000.5000");
        entry.priorIncidentCount = 0;
        entry.entityType = null;
        entry.jurisdictionRisk = null;
        entry.networkComplexity = null;
        entry.outcome = "FLAGGED";
        entry.confidence = 0.5;
        entry.investigationPath = "(direct-verdict)";

        byte[] bytes = entry.domainContentBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);

        assertEquals("LAYERING|1000.5000|0||||FLAGGED|0.5|(direct-verdict)|||", content);
    }

    @Test
    void domainContentBytes_withNarrativeSeedingFields() {
        var entry = new AmlCaseProfileLedgerEntry();
        entry.flagReason         = "STRUCTURING";
        entry.transactionAmount  = new java.math.BigDecimal("50000.0000");
        entry.priorIncidentCount = 3;
        entry.entityType         = "SHELL_COMPANY";
        entry.jurisdictionRisk   = "HIGH";
        entry.networkComplexity  = "LARGE_NETWORK";
        entry.outcome            = "SAR_WARRANTED";
        entry.confidence         = 0.92;
        entry.investigationPath  = "entity-resolution → sar-drafting";
        entry.narrativeSeeded    = true;
        entry.seedCount          = 3;

        byte[] bytes   = entry.domainContentBytes();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(
                "STRUCTURING|50000.0000|3|SHELL_COMPANY|HIGH|LARGE_NETWORK|SAR_WARRANTED|0.92|entity-resolution → sar-drafting|true|3|",
                content);
    }

    @Test
    void domainContentBytes_narrativeSeedingFieldsNull() {
        var entry = new AmlCaseProfileLedgerEntry();
        entry.flagReason         = "LAYERING";
        entry.transactionAmount  = new java.math.BigDecimal("1000.5000");
        entry.priorIncidentCount = 0;
        entry.outcome            = "FALSE_POSITIVE";
        entry.confidence         = null;
        entry.investigationPath  = "(direct-verdict)";
        entry.narrativeSeeded    = null;
        entry.seedCount          = null;

        byte[] bytes   = entry.domainContentBytes();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("LAYERING|1000.5000|0||||FALSE_POSITIVE||(direct-verdict)|||", content);
    }
}
