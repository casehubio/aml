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
 * Integration tests for SAR_FILING gate rejection routing.
 *
 * HIGH_RISK_JURISDICTION triggers SHELL_COMPANY hard gate → SAR_WARRANTED.
 * After gate rejection, re-triage still hits the hard gate → escalation always fires.
 * Two scenarios: head of compliance decides NO_SAR or FILE_SAR.
 */
@QuarkusTest
class SarFilingRejectionRoutingTest {

    @Inject CbrCaseMemoryStore cbrStore;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject WorkItemService workItemService;

    @PersistenceContext
    EntityManager defaultEm;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    private static SuspiciousTransaction highRiskTransaction(String id) {
        return new SuspiciousTransaction(id, "ACC-HR-A", "ACC-HR-B",
                new BigDecimal("200000"), "USD",
                Instant.parse("2024-12-01T00:00:00Z"),
                FlagReason.HIGH_RISK_JURISDICTION);
    }

    /**
     * SAR_FILING gate rejected → rejection-review → post-rejection-triage (SAR_WARRANTED,
     * hard gate) → rejection-escalation → head of compliance decides NO_SAR →
     * investigation-closed-no-sar goal met.
     */
    @Test
    void sar_filing_rejection_escalation_no_sar() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(highRiskTransaction("TXN-REJ-NOSAR-" + UUID.randomUUID()))
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Wait for SAR_FILING PlannedAction gate, then REJECT it
        awaitAndRejectGate(caseId, "Insufficient evidence for SAR filing");

        // Rejection routing: rejection-review → post-rejection-triage → rejection-escalation
        final WorkItemEntity escalation = awaitEscalationWorkItem(caseId);
        assertNotNull(escalation, "escalation WorkItem must exist");

        // Head of compliance decides NO_SAR
        workItemService.completeFromSystem(escalation.id, "test-head-compliance", "NO_SAR");

        // investigation-closed-no-sar goal fires → case COMPLETED
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    /**
     * SAR_FILING gate rejected → rejection-review → post-rejection-triage (SAR_WARRANTED) →
     * rejection-escalation → head of compliance decides FILE_SAR → sar-drafting-escalated
     * (no PlannedAction) → compliance-review-opening → investigation-complete goal met.
     * Then compliance review completed → sar-filed outcome.
     */
    @Test
    void sar_filing_rejection_escalation_file_sar() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(highRiskTransaction("TXN-REJ-FILESAR-" + UUID.randomUUID()))
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Reject the SAR_FILING gate
        awaitAndRejectGate(caseId, "Need more evidence");

        // Wait for escalation WorkItem
        final WorkItemEntity escalation = awaitEscalationWorkItem(caseId);
        assertNotNull(escalation, "escalation WorkItem must exist");

        // Head of compliance overrides — FILE_SAR
        workItemService.completeFromSystem(escalation.id, "test-head-compliance", "FILE_SAR");

        // sar-drafting-escalated fires (no PlannedAction), compliance-review-opening fires,
        // investigation-complete goal met → COMPLETED
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        // Complete the compliance review for sar-filed outcome
        final WorkItemEntity review = findComplianceReviewWorkItem(caseId);
        assertNotNull(review, "compliance review WorkItem must exist after escalation FILE_SAR");
        workItemService.completeFromSystem(review.id, "test-compliance-officer", "SAR approved post-escalation");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> "sar-filed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("outcome.type")));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private void awaitAndRejectGate(UUID caseId, String reason) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItemEntity gate = findGateWorkItems(caseId).get(0);
        workItemService.rejectFromSystem(gate.id, "test-mlro", reason);
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

    private WorkItemEntity findComplianceReviewWorkItem(UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                                "SELECT w FROM WorkItemEntity w WHERE w.callerRef = :ref",
                                WorkItemEntity.class)
                        .setParameter("ref", "aml:investigation:" + caseId)
                        .getSingleResult());
    }
}
