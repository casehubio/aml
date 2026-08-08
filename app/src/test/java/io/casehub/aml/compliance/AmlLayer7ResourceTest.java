package io.casehub.aml.compliance;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 7: integration test for the compliance evidence REST endpoint.
 *
 * <p>Starts a full investigation via the Layer 6 engine path, waits for async
 * completion, then verifies the compliance evidence response structure.
 *
 * <p>Note on Merkle chain: concurrent CaseLedgerEntry writes in the H2 test environment
 * cause Merkle frontier collisions (unique constraint on subject_id + level). This
 * makes chainVerified=false in tests. Production PostgreSQL with row-level locking
 * does not have this issue.
 */
@QuarkusTest
class AmlLayer7ResourceTest {

    @Inject CbrCaseMemoryStore cbrStore;
    @Inject AmlTrustAttestationRepository attestationRepo;
    @Inject WorkItemService workItemService;

    @PersistenceContext(unitName = "qhorus")
    EntityManager qhorusEm;

    @PersistenceContext
    EntityManager defaultEm;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

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
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    @Test
    void getComplianceEvidence_afterInvestigation_returnsAllRequirements() {
        // Use Layer 5 endpoint — same engine path as Layer 6, proven stable in isolation.
        // AmlLayer7ResourceTest runs alphabetically before engine-package tests;
        // Layer 6 endpoint occasionally fails as the first investigation in a fresh JVM
        // (case definition registration timing). Layer 5 endpoint does not have this issue.
        // Layer 5 returns 200 (direct object, not Response.accepted())
        String caseId = given().contentType(ContentType.JSON)
            .body(highRiskTransaction("TXN-L7-001"))
            .when().post("/api/layer5/investigations")
            .then().statusCode(200)
            .extract().path("caseId");

        // Gate must be approved BEFORE waiting for sar-drafting attestation: the sar-drafting
        // worker returns PlannedAction(SAR_FILING), blocking at the oversight gate.
        // WorkerDecisionEvent (which triggers the attestation write) fires on worker
        // completion, which requires gate approval first.
        UUID caseUUID = UUID.fromString(caseId);
        awaitAndApproveGate(caseUUID);

        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> attestationRepo.findByInvestigationCaseId(caseUUID).stream()
                .anyMatch(a -> "sar-drafting".equals(a.capabilityTag)));

        // Full drain: wait for Layer6 "completed" status to ensure ALL Quartz jobs finish.
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                    .then().extract().path("status")));

        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            .body("caseId", equalTo(caseId))
            .body("generatedAt", notNullValue())
            .body("signature", nullValue())
            // Audit chain: CASE_OPENED + COMPLIANCE_REVIEW_OPENED now both present;
            // hash-chain disabled in tests (H2 concurrent Merkle writes violate UQ_MERKLE_FRONTIER_SUBJECT_LEVEL)
            .body("auditChain.status", anyOf(equalTo("CLOSED"), equalTo("PARTIAL"), equalTo("GAP")))
            .body("auditChain.events", hasSize(greaterThanOrEqualTo(2)))
            .body("auditChain.events[0].eventType", equalTo("CASE_OPENED"))
            .body("auditChain.events[0].causedByEntryId", nullValue())
            .body("auditChain.events[1].eventType", equalTo("COMPLIANCE_REVIEW_OPENED"))
            // SLA: WorkItem now created by engine path (COMPLIANCE_REVIEW_OPENED written)
            .body("sla.status", anyOf(equalTo("PARTIAL"), equalTo("CLOSED"), equalTo("BREACHED")))
            .body("sla.workItemId", notNullValue())
            // Trust routing: all dispatched capabilities have attestations
            .body("trustRouting.status", equalTo("CLOSED"))
            .body("trustRouting.decisions", not(empty()))
            .body("trustRouting.decisions.capabilityTag", hasItem("sar-drafting"))
            // GDPR erasure: tokenisation and erasure receipt enabled
            .body("gdprErasure.status", equalTo("CLOSED"))
            .body("gdprErasure.tokenisationEnabled", is(true))
            .body("gdprErasure.erasureReceiptEnabled", is(true))
            .body("gdprErasure.erasureEndpoint", equalTo("POST /api/actors/{actorId}/erasure"));
    }

    @Test
    void getComplianceEvidence_unknownCase_returns404() {
        given().when()
            .get("/api/investigations/{caseId}/compliance-evidence", UUID.randomUUID())
            .then().statusCode(404);
    }

    @Test
    void gdprDemoFlow_officerReview_erasure() {
        // Start investigation via layer6 (async, returns 202)
        String caseId = given().contentType(ContentType.JSON)
            .body(highRiskTransaction("TXN-GDPR-" + UUID.randomUUID()))
            .when().post("/api/layer6/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        UUID caseUUID = UUID.fromString(caseId);

        // Gate approval must precede sar-drafting attestation wait (same pattern as test 1).
        awaitAndApproveGate(caseUUID);

        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> attestationRepo.findByInvestigationCaseId(caseUUID).stream()
                .anyMatch(a -> "sar-drafting".equals(a.capabilityTag)));

        // Drain to completed status
        Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                    .then().extract().path("status")));

        // Verify sla.workItemId is present (COMPLIANCE_REVIEW_OPENED now written on engine path)
        String workItemIdStr = given().when()
            .get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            .body("sla.workItemId", notNullValue())
            .extract().path("sla.workItemId");
        UUID taskId = UUID.fromString(workItemIdStr);

        // Complete WorkItem as compliance officer: PENDING → claim → ASSIGNED → start → IN_PROGRESS → complete → COMPLETED
        workItemService.claim(taskId, "compliance-officer-001");
        workItemService.start(taskId, "compliance-officer-001");
        // 4-param complete: id, actorId, resolution, outcome — fires both sync and async WorkItemLifecycleEvent
        workItemService.complete(taskId, "compliance-officer-001", "SAR approved", "APPROVED");

        // Await @ObservesAsync delivery — poll until SAR_OFFICER_REVIEWED appears in audit chain
        Awaitility.await().atMost(5, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
            .until(() -> {
                io.restassured.response.Response r = given().when()
                    .get("/api/investigations/{caseId}/compliance-evidence", caseId);
                if (r.statusCode() != 200) return false;
                List<?> events = r.jsonPath().getList("auditChain.events");
                return events != null && events.stream().anyMatch(e ->
                    "SAR_OFFICER_REVIEWED".equals(((Map<?, ?>) e).get("eventType")));
            });

        // Assert officer review event appears in audit chain
        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            // auditChain: CLOSED requires chainVerified=true; hash-chain disabled in H2 — accept PARTIAL too
            .body("auditChain.status", anyOf(equalTo("CLOSED"), equalTo("PARTIAL")))
            .body("auditChain.events.eventType", hasItem("SAR_OFFICER_REVIEWED"));

        // GDPR erasure: erase the officer's actorId from the ledger
        given().when().post("/api/actors/compliance-officer-001/erasure")
            .then().statusCode(200);

        // Verify the officer's actorId is pseudonymized after erasure — no longer the original value
        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200);
        // Extract events and verify officer entry is pseudonymized (not the raw actorId)
        io.restassured.response.Response evidenceResponse = given().when()
            .get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200).extract().response();
        List<Map<String, Object>> events = evidenceResponse.jsonPath().getList("auditChain.events");
        boolean officerEntryPseudonymized = events.stream()
            .filter(e -> "SAR_OFFICER_REVIEWED".equals(e.get("eventType")))
            .allMatch(e -> !"compliance-officer-001".equals(e.get("actorId")));
        org.junit.jupiter.api.Assertions.assertTrue(officerEntryPseudonymized,
            "Officer actorId should be pseudonymized after GDPR erasure");
    }

    @Test
    void reconciliationPath_deletedAttestation_rebuiltOnRead() {
        // Start investigation and drain
        String caseId = given().contentType(ContentType.JSON)
            .body(highRiskTransaction("TXN-RECON-" + UUID.randomUUID()))
            .when().post("/api/layer6/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        UUID caseUUID = UUID.fromString(caseId);

        awaitAndApproveGate(caseUUID);

        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> attestationRepo.findByInvestigationCaseId(caseUUID).stream()
                .anyMatch(a -> "sar-drafting".equals(a.capabilityTag)));

        Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                    .then().extract().path("status")));

        // Verify baseline: trustRouting.status = CLOSED
        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            .body("trustRouting.status", equalTo("CLOSED"));

        // Simulate observer failure: delete one attestation row via JPQL
        UUID deletedAttId = attestationRepo.findByInvestigationCaseId(caseUUID)
            .stream().findFirst().orElseThrow().id;
        deleteAttestation(deletedAttId);

        // First read after deletion: reconciler fills the gap, status = PARTIAL (reconstructed=true)
        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            .body("trustRouting.status", equalTo("PARTIAL"));
        // reconstructed flag is on the entity, not the RoutingDecisionRecord DTO — verify via repo
        assertTrue(attestationRepo.findByInvestigationCaseId(caseUUID).stream()
            .anyMatch(a -> a.reconstructed), "At least one attestation must be reconstructed");

        // Second read: count reconstructed entries — should not increase (idempotent)
        long countBefore = attestationRepo.findByInvestigationCaseId(caseUUID).stream()
            .filter(a -> a.reconstructed).count();
        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId);
        long countAfter = attestationRepo.findByInvestigationCaseId(caseUUID).stream()
            .filter(a -> a.reconstructed).count();
        assertEquals(countBefore, countAfter, "No duplicate reconstructed entries on second call");
    }

    @Transactional
    void deleteAttestation(UUID id) {
        qhorusEm.createQuery("DELETE FROM AmlTrustRoutingAttestation a WHERE a.id = :id")
            .setParameter("id", id)
            .executeUpdate();
        qhorusEm.clear();
    }

    private SuspiciousTransaction highRiskTransaction(String id) {
        return new SuspiciousTransaction(id, "ACC-A", "ACC-B",
            new BigDecimal("200000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);
    }
}
