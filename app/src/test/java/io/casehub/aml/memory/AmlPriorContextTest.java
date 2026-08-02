package io.casehub.aml.memory;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmlPriorContextTest {

    private Memory memory(String entityId, MemoryDomain domain, double confidence, Instant createdAt) {
        return new Memory(UUID.randomUUID().toString(), entityId, domain,
            "tenant-1", null, "Entity " + entityId + " history.",
            Map.of(MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(confidence)),
            createdAt, null);
    }

    private Memory memoryNoConf(String entityId, MemoryDomain domain, Instant createdAt) {
        return new Memory(UUID.randomUUID().toString(), entityId, domain,
            "tenant-1", null, "fact", Map.of(), createdAt, null);
    }

    // 1. empty() → hasHistory() returns false
    @Test
    void emptyHasNoHistory() {
        assertFalse(AmlPriorContext.empty().hasHistory());
    }

    // 2. Non-empty entityRisk → hasHistory() returns true
    @Test
    void nonEmptyEntityRiskHasHistory() {
        Memory m = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.5, Instant.now());
        AmlPriorContext ctx = new AmlPriorContext(List.of(m), List.of(), List.of());
        assertTrue(ctx.hasHistory());
    }

    // 3. Confidence 0.8 → isKnownHighRisk() true (at threshold)
    @Test
    void confidenceAtThresholdIsHighRisk() {
        Memory m = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.8, Instant.now());
        AmlPriorContext ctx = new AmlPriorContext(List.of(m), List.of(), List.of());
        assertTrue(ctx.isKnownHighRisk());
    }

    // 4. Confidence 0.79 → isKnownHighRisk() false (below threshold)
    @Test
    void confidenceBelowThresholdIsNotHighRisk() {
        Memory m = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.79, Instant.now());
        AmlPriorContext ctx = new AmlPriorContext(List.of(m), List.of(), List.of());
        assertFalse(ctx.isKnownHighRisk());
    }

    // 5. Most-recent-per-entity: WITHDRAWN (0.0) is newer than UPHELD (0.9) → false
    @Test
    void newerWithdrawalSupersededOlderUpheld() {
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-06-01T00:00:00Z");
        Memory upheld    = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.9, older);
        Memory withdrawn = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.0, newer);
        AmlPriorContext ctx = new AmlPriorContext(List.of(upheld, withdrawn), List.of(), List.of());
        assertFalse(ctx.isKnownHighRisk());
    }

    // 6. Most-recent-per-entity: old WITHDRAWN (0.0), newer UPHELD (0.9) → true
    @Test
    void newerUpheldSupersedersOlderWithdrawal() {
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-06-01T00:00:00Z");
        Memory withdrawn = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.0, older);
        Memory upheld    = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.9, newer);
        AmlPriorContext ctx = new AmlPriorContext(List.of(withdrawn, upheld), List.of(), List.of());
        assertTrue(ctx.isKnownHighRisk());
    }

    // 7. toContextMap() on empty → correct shape with hasHistory=false, knownHighRisk=false, empty facts list
    @Test
    void toContextMapEmptyShape() {
        Map<String, Object> map = AmlPriorContext.empty().toContextMap();
        assertEquals(false, map.get("hasHistory"));
        assertEquals(false, map.get("knownHighRisk"));
        assertEquals(0, map.get("entityRiskCount"));
        assertEquals(0, map.get("networkCount"));
        assertEquals(0, map.get("patternCount"));
        List<?> facts = (List<?>) map.get("facts");
        assertNotNull(facts);
        assertTrue(facts.isEmpty());
    }

    // 8. toContextMap() fact has domain, text, createdAt, confidence fields
    @Test
    void toContextMapFactHasRequiredFields() {
        Memory m = memory("acc-1", AmlMemoryDomains.ENTITY_RISK, 0.9, Instant.now());
        AmlPriorContext ctx = new AmlPriorContext(List.of(m), List.of(), List.of());
        Map<String, Object> map = ctx.toContextMap();
        List<?> facts = (List<?>) map.get("facts");
        assertEquals(1, facts.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> fact = (Map<String, Object>) facts.get(0);
        assertTrue(fact.containsKey("domain"),     "fact must have 'domain'");
        assertTrue(fact.containsKey("text"),       "fact must have 'text'");
        assertTrue(fact.containsKey("createdAt"),  "fact must have 'createdAt'");
        assertTrue(fact.containsKey("confidence"), "fact must have 'confidence'");
        assertEquals("aml.entity-risk", fact.get("domain"));
    }

    // 9. toContextMap() limits to 10 facts (create 12 memories across 3 domains)
    @Test
    void toContextMapLimitsToTenFacts() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        List<Memory> entityRisk = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            entityRisk.add(memory("acc-e" + i, AmlMemoryDomains.ENTITY_RISK, 0.5, base.plusSeconds(i)));
        }
        List<Memory> network = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            network.add(memoryNoConf("acc-n" + i, AmlMemoryDomains.NETWORK, base.plusSeconds(i)));
        }
        List<Memory> pattern = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pattern.add(memoryNoConf("acc-p" + i, AmlMemoryDomains.PATTERN, base.plusSeconds(i)));
        }
        AmlPriorContext ctx = new AmlPriorContext(entityRisk, network, pattern);
        List<?> facts = (List<?>) ctx.toContextMap().get("facts");
        assertEquals(10, facts.size());
    }

    // 10. toContextMap() guarantees at least one per non-empty domain
    //     (9 entity + 1 network + 1 pattern → all three domains present in 10 facts)
    @Test
    void toContextMapGuaranteesOnePerNonEmptyDomain() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        // 9 entity-risk memories (older)
        List<Memory> entityRisk = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            entityRisk.add(memory("acc-e" + i, AmlMemoryDomains.ENTITY_RISK, 0.5, base.plusSeconds(i)));
        }
        // 1 network memory (newer than all entity-risk)
        List<Memory> network = List.of(
            memoryNoConf("acc-n0", AmlMemoryDomains.NETWORK, base.plusSeconds(100))
        );
        // 1 pattern memory (newer still)
        List<Memory> pattern = List.of(
            memoryNoConf("acc-p0", AmlMemoryDomains.PATTERN, base.plusSeconds(200))
        );
        AmlPriorContext ctx = new AmlPriorContext(entityRisk, network, pattern);
        List<?> facts = (List<?>) ctx.toContextMap().get("facts");
        assertEquals(10, facts.size());

        long networkCount = facts.stream()
            .map(f -> ((Map<?, ?>) f).get("domain"))
            .filter("aml.network"::equals)
            .count();
        long patternCount = facts.stream()
            .map(f -> ((Map<?, ?>) f).get("domain"))
            .filter("aml.pattern"::equals)
            .count();

        assertTrue(networkCount >= 1, "At least one network fact must appear");
        assertTrue(patternCount >= 1, "At least one pattern fact must appear");
    }
}
