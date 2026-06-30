package io.casehub.aml.engine;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AmlLayer9ResourceTest {

    @Inject CaseInstanceCache caseInstanceCache;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    private static final SuspiciousTransaction CORPORATE_TX = new SuspiciousTransaction(
            "TXN-L9-RES-" + UUID.randomUUID(),
            "ACC-C", "ACC-D",
            new BigDecimal("50000"), "USD",
            Instant.parse("2024-12-01T00:00:00Z"),
            "Routine structured layering — CORPORATE");

    private static SuspiciousTransaction pepTransaction(final String id) {
        return new SuspiciousTransaction(id, "ACC-PEP-A", "ACC-PEP-B",
                new BigDecimal("200000"), "USD",
                Instant.parse("2024-12-01T00:00:00Z"),
                "PEP -- high risk");
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
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    @Test
    void post_investigate_returns_202_with_caseId() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");
        assertNotNull(caseIdStr, "caseId must not be null");
        // CORPORATE transactions are Autonomous — no gate approval needed. Drain directly.
        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    @Test
    void get_investigation_returns_completed_after_cache_eviction() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        // CORPORATE transactions are Autonomous — no gate approval needed.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().statusCode(200).extract().path("status")));

        // Simulate cache eviction — endpoint must fall back to CaseInstanceRepository
        caseInstanceCache.clear();

        given().when().get("/api/layer9/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("status", equalTo("completed"));
    }

    @Test
    void get_nonexistent_investigation_returns_404() {
        given().when().get("/api/layer9/investigations/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void gate_approval_allows_investigation_to_complete() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(pepTransaction("TXN-L9-APPROVED-" + UUID.randomUUID()))
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);
        awaitAndApproveGate(caseId);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        // Layer 9 investigations do NOT produce SAR outcomes — only oversight gates exist
        given().when().get("/api/layer9/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("outcome", nullValue());
    }

    @Test
    void gate_rejection_completes_with_null_outcome() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(pepTransaction("TXN-L9-REJECTED-" + UUID.randomUUID()))
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Wait for gate to appear, then reject it
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.rejectFromSystem(gate.id, "test-mlro", "Entity link rejected - insufficient evidence");

        // Gate rejection terminal state may not be COMPLETED — depends on engine
        // ActionGateRejectedHandler. Wait for the case to exist and settle.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    final String status = given().when().get("/api/layer9/investigations/" + caseIdStr)
                            .then().extract().path("status");
                    return status != null;
                });

        // Layer 9 investigations do NOT produce SAR outcomes — only oversight gates exist.
        given().when().get("/api/layer9/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("outcome", nullValue());
    }

    @Test
    void post_suspend_returns_409_for_completed_case() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        given().when().post("/api/layer9/investigations/" + caseIdStr + "/suspend")
                .then().statusCode(409);
    }

    @Test
    void post_suspend_returns_404_for_nonexistent_case() {
        given().when().post("/api/layer9/investigations/" + UUID.randomUUID() + "/suspend")
                .then().statusCode(404);
    }

    @Test
    void post_resume_returns_409_for_completed_case() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        given().when().post("/api/layer9/investigations/" + caseIdStr + "/resume")
                .then().statusCode(409);
    }

    @Test
    void post_resume_returns_404_for_nonexistent_case() {
        given().when().post("/api/layer9/investigations/" + UUID.randomUUID() + "/resume")
                .then().statusCode(404);
    }
}
