package io.casehub.aml.engine;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for INVESTIGATION_CLEARANCE gate rejection routing.
 *
 * PEP_MATCH produces INCONCLUSIVE (score 0.5545, no hard gate) →
 * PlannedAction(INVESTIGATION_CLEARANCE). After rejection, re-triage
 * still INCONCLUSIVE → escalation fires.
 */
@QuarkusTest
class ClearanceRejectionRoutingTest {

    @Inject CbrCaseMemoryStore cbrStore;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject WorkItemService workItemService;

    @PersistenceContext
    EntityManager defaultEm;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    private static SuspiciousTransaction pepTransaction(String id) {
        return new SuspiciousTransaction(id, "ACC-PEP-A", "ACC-PEP-B",
                new BigDecimal("150000"), "USD",
                Instant.parse("2024-12-01T00:00:00Z"),
                FlagReason.PEP_MATCH);
    }

    /**
     * INVESTIGATION_CLEARANCE gate rejected → rejection-review → post-rejection-triage
     * (still INCONCLUSIVE — PEP score + rejection factors stay in the ambiguous band) →
     * rejection-escalation → head of compliance decides CLEAR → investigation-cleared goal met.
     */
    @Test
    void clearance_rejection_escalation_clear() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(pepTransaction("TXN-CLR-REJ-" + UUID.randomUUID()))
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Wait for INVESTIGATION_CLEARANCE gate (PEP_MATCH → INCONCLUSIVE → PlannedAction)
        // PEP_MATCH also fires senior-analyst-required-resolution, but triage runs independently.
        awaitAndRejectGate(caseId, "Insufficient grounds for clearance");

        // Rejection routing: rejection-review → post-rejection-triage (INCONCLUSIVE) → escalation
        final WorkItemEntity escalation = awaitEscalationWorkItem(caseId);
        assertNotNull(escalation, "escalation WorkItem must exist");

        // Head of compliance decides CLEAR
        workItemService.completeFromSystem(escalation.id, "test-head-compliance", "CLEAR");

        // investigation-cleared goal fires (.rejectionEscalation.decision == "CLEAR") → COMPLETED
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private void awaitAndRejectGate(UUID caseId, String reason) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItemEntity gate = findGateWorkItems(caseId).get(0);
        workItemService.rejectFromSystem(gate.id, "test-compliance-officer", reason);
    }

    private List<WorkItemEntity> findGateWorkItems(UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                                WorkItemEntity.class)
                        .setParameter("pattern", "case:" + caseId + "/gate:%")
                        .getResultList());
    }

    private WorkItemEntity awaitEscalationWorkItem(UUID caseId) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findEscalationWorkItems(caseId).isEmpty());
        return findEscalationWorkItems(caseId).get(0);
    }

    private List<WorkItemEntity> findEscalationWorkItems(UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                                "SELECT w FROM WorkItemEntity w WHERE w.callerRef = :ref",
                                WorkItemEntity.class)
                        .setParameter("ref", "aml:escalation:" + caseId)
                        .getResultList());
    }
}
