package io.casehub.aml.memory;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AmlSarOutcomeObserverTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject CaseMemoryStore memoryStore;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                WorkItem.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private void drain(final UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100))
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    @Test
    void sarOutcomeEvent_writesMemoryForBothAccounts() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR-MEM-001-" + UUID.randomUUID(), "ACC-SAR-ORIGIN-1-" + UUID.randomUUID(), "ACC-SAR-DEST-1-" + UUID.randomUUID(),
            new BigDecimal("80000"), "USD", Instant.now(), FlagReason.STRUCTURING);
        UUID caseId = coordinator.startInvestigation(tx);
        drain(caseId);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
            caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.92)));

        List<io.casehub.neocortex.memory.Memory> originMemories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.originAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));
        List<io.casehub.neocortex.memory.Memory> destMemories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.destinationAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));

        assertFalse(originMemories.isEmpty(), "Origin account must have SAR outcome memory");
        assertFalse(destMemories.isEmpty(), "Destination account must have SAR outcome memory");
        assertTrue(originMemories.stream().anyMatch(m -> m.text().contains("UPHELD")));
    }

    @Test
    void withdrawn_sarOutcome_writesZeroConfidenceReversal() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR-MEM-002-" + UUID.randomUUID(), "ACC-SAR-WITHDRAWN-" + UUID.randomUUID(), "ACC-SAR-WITHDRAWN-DEST-" + UUID.randomUUID(),
            new BigDecimal("30000"), "USD", Instant.now(), FlagReason.STRUCTURING);
        UUID caseId = coordinator.startInvestigation(tx);
        drain(caseId);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
            caseId, new SarOutcome(SarVerdict.WITHDRAWN, "SAR withdrawn", 0.10)));

        List<io.casehub.neocortex.memory.Memory> memories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.originAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));

        assertFalse(memories.isEmpty(), "WITHDRAWN must still write a reversal memory");
        String confidence = memories.get(0).attributes().get(MemoryAttributeKeys.CONFIDENCE);
        assertEquals("0.0000", confidence, "WITHDRAWN verdict must write confidence=0.0");
    }

    @Test
    void sarOutcomeEvent_withNoLedgerEntry_doesNotThrow() {
        UUID nonexistentCaseId = UUID.randomUUID();
        assertDoesNotThrow(() ->
            sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                nonexistentCaseId, new SarOutcome(SarVerdict.UPHELD, "test", 0.9))));
    }
}
