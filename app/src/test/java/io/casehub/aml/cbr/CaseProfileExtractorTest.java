package io.casehub.aml.cbr;

import io.casehub.aml.domain.*;
import io.casehub.aml.memory.AmlPriorContext;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaseProfileExtractorTest {

    private final CaseProfileExtractor extractor = new CaseProfileExtractor();

    private SuspiciousTransaction tx(FlagReason reason, BigDecimal amount) {
        return new SuspiciousTransaction("TX-1", "ACC-A", "ACC-B",
                amount, "USD", Instant.now(), reason);
    }

    private Memory memory(String id) {
        return new Memory(id, "entity-1", new MemoryDomain("aml.entity-risk"),
                "tenant-1", "case-1", "risk note", Map.of(), Instant.now(), null);
    }

    @Test
    void extractInitial_noPriorHistory() {
        CaseProfile profile = extractor.extractInitial(
                tx(FlagReason.STRUCTURING, new BigDecimal("9500")),
                AmlPriorContext.empty());

        assertEquals(FlagReason.STRUCTURING, profile.flagReason());
        assertEquals(new BigDecimal("9500"), profile.transactionAmount());
        assertEquals(0, profile.priorIncidentCount());
        assertNull(profile.entityType());
        assertNull(profile.jurisdiction());
        assertNull(profile.network());
    }

    @Test
    void extractInitial_withPriorHistory() {
        AmlPriorContext prior = new AmlPriorContext(
                List.of(memory("m1"), memory("m2"), memory("m3"), memory("m4"), memory("m5")),
                List.of(), List.of());

        CaseProfile profile = extractor.extractInitial(
                tx(FlagReason.PEP_MATCH, new BigDecimal("500000")),
                prior);

        assertEquals(5, profile.priorIncidentCount());
    }

    @Test
    void extractComplete_allDimensions() {
        CaseProfile profile = extractor.extractComplete(
                tx(FlagReason.LAYERING, new BigDecimal("1000000")),
                AmlPriorContext.empty(),
                EntityType.SHELL_COMPANY, JurisdictionRisk.HIGH,
                NetworkComplexity.LARGE_NETWORK);

        assertEquals(FlagReason.LAYERING, profile.flagReason());
        assertEquals(new BigDecimal("1000000"), profile.transactionAmount());
        assertEquals(0, profile.priorIncidentCount());
        assertEquals(EntityType.SHELL_COMPANY, profile.entityType());
        assertEquals(JurisdictionRisk.HIGH, profile.jurisdiction());
        assertEquals(NetworkComplexity.LARGE_NETWORK, profile.network());
    }
}
