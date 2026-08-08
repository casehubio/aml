package io.casehub.aml.trust;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 7: verifies that AmlTrustRoutingObserver writes attestation entries for each
 * worker dispatch, capturing the trust score from TrustScoreCache at routing time.
 *
 * <p>Note: {@code @TestTransaction} is intentionally omitted — same pattern as
 * {@code AmlLedgerChainTest}. Tests use unique transaction IDs to avoid cross-test
 * interference with persisted attestation entries.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AmlTrustRoutingAttestationTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject AmlTrustAttestationRepository attestationRepo;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

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
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    @Test
    @Order(1)
    void workerDispatch_writesAttestationPerCapability() {
        UUID caseId = coordinator.startInvestigation(highRisk("TXN-ATT-001-" + UUID.randomUUID()));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() ->
            !attestationRepo.findByInvestigationCaseId(caseId).isEmpty()
        );

        List<AmlTrustRoutingAttestation> attestations =
            attestationRepo.findByInvestigationCaseId(caseId);

        assertFalse(attestations.isEmpty(), "At least one attestation must be written");

        for (AmlTrustRoutingAttestation a : attestations) {
            assertEquals(caseId, a.investigationCaseId);
            assertNotNull(a.capabilityTag, "capabilityTag must be set");
            assertNotNull(a.selectedWorkerId, "selectedWorkerId must be set");
            assertTrue(a.thresholdApplied > 0.0, "thresholdApplied must be positive");
        }
        awaitAndApproveGate(caseId);
        drain(caseId);
    }

    @Test
    @Order(2)
    void workerDispatch_sarDraftingAttestation_hasNonNullScore_whenCacheSeeded() {
        UUID caseId = coordinator.startInvestigation(highRisk("TXN-ATT-002-" + UUID.randomUUID()));

        // Gate must be approved BEFORE checking sar-drafting attestation: the sar-drafting
        // worker returns PlannedAction(SAR_FILING), blocking at the oversight gate.
        // WorkerDecisionEvent (which triggers the attestation write) fires on worker
        // completion, which requires gate approval first.
        awaitAndApproveGate(caseId);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() ->
            attestationRepo.findByInvestigationCaseId(caseId).stream()
                .anyMatch(a -> "sar-drafting".equals(a.capabilityTag))
        );

        AmlTrustRoutingAttestation sarAttestation = attestationRepo
            .findByInvestigationCaseId(caseId).stream()
            .filter(a -> "sar-drafting".equals(a.capabilityTag))
            .findFirst().orElseThrow(() -> new AssertionError("No sar-drafting attestation"));

        assertNotNull(sarAttestation.trustScoreAtRouting,
            "trustScoreAtRouting must be non-null when cache is seeded");
        assertTrue(sarAttestation.trustScoreAtRouting > 0.0,
            "trustScoreAtRouting must be positive when seeded");
        drain(caseId);
    }

    private SuspiciousTransaction highRisk(String id) {
        return new SuspiciousTransaction(id, "ACC-A", "ACC-B",
            new BigDecimal("200000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);
    }
}
