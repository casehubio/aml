package io.casehub.aml.engine;

import io.casehub.aml.domain.AmlGroups;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 9: verifies the AML oversight gate fires for PEP entity link creation and
 * that low-risk CORPORATE cases proceed autonomously without a gate.
 *
 * <p>Gate WorkItems live on the default PU (WorkItem is in io.casehub.work.runtime.model,
 * mapped to the default datasource). Use {@code @PersistenceContext} with no unitName.
 *
 * <p>{@code WorkItemService.completeFromSystem()} fires both {@code fire()} (synchronous
 * observers) and {@code fireAsync()} (async observers). {@code WorkItemLifecycleAdapter}
 * is {@code @ObservesAsync} and is called via the async path, routing to
 * {@code ActionGateCompletionApplier} which resumes the engine case.
 */
@QuarkusTest
class AmlLayer9ActionGateTest {

    @Inject CbrCaseMemoryStore cbrStore;

    @PersistenceContext  // no unitName — WorkItem on default datasource, not qhorus
    EntityManager em;

    @Inject
    WorkItemService workItemService;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void gate_fires_for_pep_entity_and_resumes_on_approval() {
        final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-GATE-" + UUID.randomUUID(), "ACC-GATE-A", "ACC-GATE-B",
            new BigDecimal("500000"), "GBP",
            Instant.parse("2024-12-01T00:00:00Z"),
            FlagReason.PEP_MATCH);

        final String caseIdStr = given().contentType(ContentType.JSON).body(tx)
            .when().post("/api/layer9/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Await gate WorkItem — positive signal that the classifier returned GateRequired
        // and the engine paused the case
        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(300, TimeUnit.MILLISECONDS)
            .until(() -> !findGateWorkItems(caseId).isEmpty());

        final List<WorkItemEntity> gateItems = findGateWorkItems(caseId);
        assertEquals(1, gateItems.size(), "Exactly one gate WorkItem must be created");
        final WorkItemEntity gate = gateItems.get(0);

        assertEquals(AmlGroups.AML_COMPLIANCE, gate.candidateGroups,
            "candidateGroups must be aml-compliance (ENTITY_LINK_CREATION type)");
        assertTrue(gate.callerRef.startsWith("case:" + caseId + "/gate:"),
            "callerRef must follow GateCallerRef format, was: " + gate.callerRef);

        // Engine must be paused — investigation not completed yet
        final String statusBeforeApproval = given()
            .when().get("/api/layer9/investigations/" + caseIdStr)
            .then().statusCode(200)
            .extract().path("status");
        assertNotEquals("completed", statusBeforeApproval,
            "Investigation must be paused at gate, not completed");

        // Approve the gate — fires WorkItemLifecycleEvent(COMPLETED) async
        workItemService.completeFromSystem(gate.id, "test-aml-compliance", "approved");

        // Engine resumes via ActionGateCompletionApplier; await final completion
        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(300, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer9/investigations/" + caseIdStr)
                    .then().extract().path("status")));
    }

    @Test
    void gate_not_fired_for_low_risk_corporate() {
        final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-AUTO-" + UUID.randomUUID(), "ACC-AUTO-C", "ACC-AUTO-D",
            new BigDecimal("50000"), "GBP",
            Instant.parse("2024-12-01T00:00:00Z"),
            FlagReason.LAYERING);

        final String caseIdStr = given().contentType(ContentType.JSON).body(tx)
            .when().post("/api/layer9/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Classifier returns Autonomous for low-risk CORPORATE — case completes without gate
        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(300, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer9/investigations/" + caseIdStr)
                    .then().extract().path("status")));

        // Confirm no gate WorkItem was created
        assertTrue(findGateWorkItems(caseId).isEmpty(),
            "No gate WorkItem must be created for low-risk CORPORATE entity");
    }

    private List<WorkItemEntity> findGateWorkItems(final UUID caseId) {
        // Awaitility runs on a thread without CDI request context or transaction.
        // QuarkusTransaction.requiringNew() provides the transaction context needed by EntityManager.
        return QuarkusTransaction.requiringNew().call(() ->
            em.createQuery(
                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                WorkItemEntity.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }
}
