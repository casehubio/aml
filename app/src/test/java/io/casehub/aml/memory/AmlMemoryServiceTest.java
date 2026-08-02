package io.casehub.aml.memory;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmlMemoryServiceTest {

    @Mock CaseMemoryStore memoryStore;
    @Mock CurrentPrincipal principal;
    @Mock PreferenceProvider preferenceProvider;
    @Mock Preferences preferences;

    @Captor ArgumentCaptor<MemoryInput>       inputCaptor;
    @Captor ArgumentCaptor<List<MemoryInput>> listCaptor;

    private AmlMemoryService service;

    @BeforeEach
    void setUp() {
        lenient().when(principal.tenancyId()).thenReturn("test-tenant");
        lenient().when(preferenceProvider.resolve(any())).thenReturn(preferences);
        lenient().when(preferences.get(any())).thenReturn(null); // use default 365 days
        service = new AmlMemoryService(memoryStore, principal, preferenceProvider);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private SuspiciousTransaction transaction() {
        return new SuspiciousTransaction(
            "tx-001", "origin-acc", "dest-acc",
            new BigDecimal("50000.00"), "USD", Instant.now(), FlagReason.STRUCTURING);
    }

    private EntityResolutionResult entityResult() {
        return new EntityResolutionResult("origin-acc", "Shell Corp A -> Real Beneficiary B", "PEP", 0.92);
    }

    private PatternAnalysisResult patternResult(boolean structuring, String description) {
        return new PatternAnalysisResult(structuring, description);
    }

    private SarOutcome sarOutcome(SarVerdict verdict, double accuracy) {
        return new SarOutcome(verdict, "Reason for " + verdict.name(), accuracy);
    }

    // ── Text formatting ────────────────────────────────────────────────────────

    // 1. storeEntityRisk — text contains entity type (FlagReason.PEP_MATCH) and risk score ("0.9200")
    @Test
    void storeEntityRiskTextContainsEntityTypeAndRiskScore() {
        EntityResolutionResult result = entityResult();
        when(memoryStore.store(any())).thenReturn("mem-id-1");

        service.storeEntityRisk(UUID.randomUUID(), "origin-acc", result);

        verify(memoryStore).store(inputCaptor.capture());
        String text = inputCaptor.getValue().text();
        assertTrue(text.contains("PEP"),    "text must mention entity type: " + text);
        assertTrue(text.contains("0.9200"), "text must mention risk score: " + text);
    }

    // 2. storeEntityRisk — CONFIDENCE attribute set correctly
    @Test
    void storeEntityRiskConfidenceAttributeSetCorrectly() {
        EntityResolutionResult result = entityResult();
        when(memoryStore.store(any())).thenReturn("mem-id-1");

        service.storeEntityRisk(UUID.randomUUID(), "origin-acc", result);

        verify(memoryStore).store(inputCaptor.capture());
        String conf = inputCaptor.getValue().attributes().get(MemoryAttributeKeys.CONFIDENCE);
        assertEquals("0.9200", conf);
    }

    // 3. storeNetworkRelationship — text mentions both account IDs
    @Test
    void storeNetworkRelationshipTextMentionsBothAccounts() {
        SuspiciousTransaction tx = transaction();
        EntityResolutionResult result = entityResult();

        service.storeNetworkRelationship(UUID.randomUUID(), tx, result);

        verify(memoryStore).storeAll(listCaptor.capture());
        List<MemoryInput> inputs = listCaptor.getValue();
        String text = inputs.get(0).text();
        assertTrue(text.contains("origin-acc"), "text must mention origin account: " + text);
        assertTrue(text.contains("dest-acc"),   "text must mention destination account: " + text);
    }

    // 4. storePatternFindings — text mentions description and "structuring"
    @Test
    void storePatternFindingsTextMentionsDescriptionAndStructuring() {
        SuspiciousTransaction tx = transaction();
        PatternAnalysisResult result = patternResult(true, "Classic layering pattern");

        service.storePatternFindings(UUID.randomUUID(), tx, result);

        verify(memoryStore).storeAll(listCaptor.capture());
        String text = listCaptor.getValue().get(0).text();
        assertTrue(text.contains("Classic layering pattern"), "text must mention description: " + text);
        assertTrue(text.toLowerCase().contains("structuring"), "text must mention structuring: " + text);
    }

    // ── Domain routing ─────────────────────────────────────────────────────────

    // 5. storeEntityRisk calls store() with ENTITY_RISK domain
    @Test
    void storeEntityRiskUsesEntityRiskDomain() {
        when(memoryStore.store(any())).thenReturn("mem-id");

        service.storeEntityRisk(UUID.randomUUID(), "origin-acc", entityResult());

        verify(memoryStore).store(inputCaptor.capture());
        assertEquals(AmlMemoryDomains.ENTITY_RISK, inputCaptor.getValue().domain());
    }

    // 6. storeNetworkRelationship calls storeAll() with 2 entries both in NETWORK domain, one per account
    @Test
    void storeNetworkRelationshipWritesTwoNetworkEntries() {
        SuspiciousTransaction tx = transaction();

        service.storeNetworkRelationship(UUID.randomUUID(), tx, entityResult());

        verify(memoryStore).storeAll(listCaptor.capture());
        List<MemoryInput> inputs = listCaptor.getValue();
        assertEquals(2, inputs.size());
        assertTrue(inputs.stream().allMatch(i -> AmlMemoryDomains.NETWORK.equals(i.domain())));
        assertTrue(inputs.stream().anyMatch(i -> "origin-acc".equals(i.entityId())));
        assertTrue(inputs.stream().anyMatch(i -> "dest-acc".equals(i.entityId())));
    }

    // 7. storePatternFindings calls storeAll() with 2 entries both in PATTERN domain, one per account
    @Test
    void storePatternFindingsWritesTwoPatternEntries() {
        SuspiciousTransaction tx = transaction();

        service.storePatternFindings(UUID.randomUUID(), tx, patternResult(true, "Layering"));

        verify(memoryStore).storeAll(listCaptor.capture());
        List<MemoryInput> inputs = listCaptor.getValue();
        assertEquals(2, inputs.size());
        assertTrue(inputs.stream().allMatch(i -> AmlMemoryDomains.PATTERN.equals(i.domain())));
        assertTrue(inputs.stream().anyMatch(i -> "origin-acc".equals(i.entityId())));
        assertTrue(inputs.stream().anyMatch(i -> "dest-acc".equals(i.entityId())));
    }

    // 8. storeSarOutcome (UPHELD) calls storeAll() with 2 entries in ENTITY_RISK domain
    @Test
    void storeSarOutcomeUpheldWritesTwoEntityRiskEntries() {
        SuspiciousTransaction tx = transaction();

        service.storeSarOutcome(UUID.randomUUID(), tx, sarOutcome(SarVerdict.UPHELD, 0.85));

        verify(memoryStore).storeAll(listCaptor.capture());
        List<MemoryInput> inputs = listCaptor.getValue();
        assertEquals(2, inputs.size());
        assertTrue(inputs.stream().allMatch(i -> AmlMemoryDomains.ENTITY_RISK.equals(i.domain())));
    }

    // ── Failure handling ───────────────────────────────────────────────────────

    // 9. storeEntityRisk does NOT throw when memoryStore.store() throws RuntimeException
    @Test
    void storeEntityRiskDoesNotThrowOnStoreFailure() {
        when(memoryStore.store(any())).thenThrow(new RuntimeException("Store unavailable"));

        assertDoesNotThrow(() ->
            service.storeEntityRisk(UUID.randomUUID(), "origin-acc", entityResult()));
    }

    // 10. storeSarOutcome (WITHDRAWN) writes CONFIDENCE = "0.0000" (zero confidence reversal)
    @Test
    void storeSarOutcomeWithdrawnWritesZeroConfidence() {
        SuspiciousTransaction tx = transaction();

        service.storeSarOutcome(UUID.randomUUID(), tx, sarOutcome(SarVerdict.WITHDRAWN, 0.0));

        verify(memoryStore).storeAll(listCaptor.capture());
        List<MemoryInput> inputs = listCaptor.getValue();
        String conf = inputs.get(0).attributes().get(MemoryAttributeKeys.CONFIDENCE);
        assertEquals("0.0000", conf, "WITHDRAWN reversal must write zero confidence");
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    // 11. queryPriorContext executes exactly 3 domain queries
    @Test
    void queryPriorContextExecutesThreeDomainQueries() {
        when(memoryStore.query(any())).thenReturn(List.of());

        service.queryPriorContext(transaction());

        verify(memoryStore, times(3)).query(any(MemoryQuery.class));
    }

    // 12. queryPriorContext — if one domain query throws, the other two succeed
    //     (partial context returned, no exception)
    @Test
    void queryPriorContextReturnsPartialContextOnSingleDomainFailure() {
        Memory fakeMemory = new Memory(
            "mem-1", "origin-acc", AmlMemoryDomains.NETWORK,
            "test-tenant", null, "fact", java.util.Map.of(), Instant.now(), null);

        // First call (ENTITY_RISK) throws; second (NETWORK) returns one entry; third (PATTERN) returns empty
        when(memoryStore.query(any()))
            .thenThrow(new RuntimeException("ENTITY_RISK store down"))
            .thenReturn(List.of(fakeMemory))
            .thenReturn(List.of());

        AmlPriorContext ctx = assertDoesNotThrow(() -> service.queryPriorContext(transaction()));

        assertTrue(ctx.entityRisk().isEmpty(),     "entity risk must be empty on failure");
        assertEquals(1, ctx.network().size(),      "network must have one entry from successful call");
        assertTrue(ctx.pattern().isEmpty(),        "pattern must be empty");
        verify(memoryStore, times(3)).query(any()); // all 3 were still attempted
    }
}
