package io.casehub.aml.engine;

import io.casehub.aml.domain.AmlGroups;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.work.runtime.model.WorkItemEntity;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full end-to-end integration test for Layer 6 trust routing.
 *
 * <p>Verifies the complete flow: start an investigation via HTTP → wait for async
 * completion → confirm senior agent was selected via trust score → record SAR outcome →
 * confirm LedgerAttestation persisted with SOUND verdict and correct trust dimension.
 *
 * <p>This is a {@code @QuarkusTest} (not a separate IT module) because the Quartz
 * worker that drives the engine runs inside the same Quarkus application context.
 *
 * <p>Layer 6 tutorial component — ties together all trust routing primitives:
 * {@code AmlTrustScoreSeeder} seeds initial scores → {@code AmlTrustRoutingPolicyProvider}
 * supplies the threshold → engine selects senior worker → {@code SarOutcomeFeedbackService}
 * writes the attestation back.
 */
@QuarkusTest
class AmlLayer6InvestigationTest {

    @Inject CbrCaseMemoryStore cbrStore;

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void full_trust_routing_flow_corporate_case() {
        final SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-IT-" + UUID.randomUUID(),
                "ACC-A", "ACC-B",
                new BigDecimal("120000"), "USD",
                Instant.parse("2024-09-15T00:00:00Z"),
                FlagReason.HIGH_RISK_JURISDICTION);

        // Step 1: Start investigation (async)
        final String caseIdStr = given().contentType(ContentType.JSON).body(tx)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Step 2: Await and approve MLRO gate
        awaitAndApproveGate(caseId);

        // Step 3: Poll until completed (engine runs async on Quartz)
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        // Step 4: Verify senior sar-drafting agent was selected
        final io.restassured.response.Response getResponse =
                given().when().get("/api/layer6/investigations/" + caseIdStr)
                        .then().extract().response();
        final List<Map<String, Object>> decisions =
                getResponse.jsonPath().getList("routingDecisions");

        assertTrue(!decisions.isEmpty(), "Routing decisions must be populated");
        final Optional<Map<String, Object>> sarDecision = decisions.stream()
                .filter(d -> "sar-drafting".equals(d.get("capabilityTag")))
                .findFirst();
        assertTrue(sarDecision.isPresent(), "sar-drafting routing decision must be present");
        assertEquals("sar-drafting-agent-senior", sarDecision.get().get("selectedWorker"),
                "Senior agent must be selected (trust 0.90 > threshold 0.75)");
        assertNotNull(sarDecision.get().get("trustScore"), "Trust score must be in response");

        // Step 5: Record SAR outcome (positive)
        given().contentType(ContentType.JSON)
                .body(new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN unit", 0.94))
                .when().post("/api/layer6/investigations/" + caseIdStr + "/outcome")
                .then().statusCode(204);

        // Step 6: Verify LedgerAttestation persisted with SOUND verdict
        final List<LedgerAttestation> attestations = em.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid",
                LedgerAttestation.class)
                .setParameter("sid", caseId)
                .getResultList();
        assertFalse(attestations.isEmpty(), "LedgerAttestation must be written after outcome recorded");
        assertEquals(AttestationVerdict.SOUND, attestations.get(0).verdict);
        assertEquals("sar-drafting", attestations.get(0).capabilityTag);
        assertEquals("investigation-accuracy", attestations.get(0).trustDimension);
        assertEquals(0.94, attestations.get(0).dimensionScore, 0.001);
    }

    private List<WorkItemEntity> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                WorkItemEntity.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final List<WorkItemEntity> gateItems = findGateWorkItems(caseId);
        assertEquals(1, gateItems.size(), "Exactly one SAR_FILING gate WorkItem");
        assertEquals(AmlGroups.MLRO, gateItems.get(0).candidateGroups,
                "candidateGroups must be aml-mlro (SAR_FILING type)");
        workItemService.completeFromSystem(gateItems.get(0).id, "test-mlro", "approved");
    }
}
