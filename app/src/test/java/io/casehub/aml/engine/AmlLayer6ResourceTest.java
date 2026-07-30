package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.FlagReason;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AmlLayer6ResourceTest {

    @Inject CaseInstanceCache caseInstanceCache;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    private static final SuspiciousTransaction TRANSACTION = new SuspiciousTransaction(
            "TXN-L6-" + UUID.randomUUID(),
            "ACC-ORIGIN", "ACC-DEST",
            new BigDecimal("95000"), "USD",
            Instant.parse("2024-06-01T00:00:00Z"),
            FlagReason.LAYERING);

    private static SuspiciousTransaction highRiskTransaction(final String id) {
        return new SuspiciousTransaction(id, "ACC-HR-A", "ACC-HR-B",
                new BigDecimal("200000"), "USD",
                Instant.parse("2024-12-01T00:00:00Z"),
                FlagReason.HIGH_RISK_JURISDICTION);
    }

    @Test
    void post_investigate_returns_202_with_caseId() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");
        assertNotNull(caseIdStr, "caseId must not be null");
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    @Test
    void get_investigation_returns_completed_with_routing_decisions() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().statusCode(200).extract().path("status")));

        given().when().get("/api/layer6/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("status", equalTo("completed"))
                .body("routingDecisions", not(empty()))
                .body("routingDecisions.capabilityTag", hasItem("entity-resolution"));
    }

    @Test
    void post_outcome_with_invalid_score_returns_400_with_domain_message() {
        // investigationAccuracyScore=1.5 violates SarOutcome compact constructor invariant
        given().contentType(ContentType.JSON)
                .body("{\"verdict\":\"UPHELD\",\"reason\":\"test\",\"investigationAccuracyScore\":1.5}")
                .when().post("/api/layer6/investigations/" + UUID.randomUUID() + "/outcome")
                .then().statusCode(400)
                .body("error", containsString("investigationAccuracyScore"));
    }

    @Test
    void get_investigation_returns_completed_after_cache_eviction() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().statusCode(200).extract().path("status")));

        // Simulate cache eviction — endpoint must fall back to CaseInstanceRepository
        caseInstanceCache.clear();

        given().when().get("/api/layer6/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("status", equalTo("completed"));
    }

    @Test
    void post_outcome_returns_204() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        given().contentType(ContentType.JSON)
                .body(new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN", 0.95))
                .when().post("/api/layer6/investigations/" + caseIdStr + "/outcome")
                .then().statusCode(204);
    }

    @Test
    void officer_approval_surfaces_sar_filed_outcome() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(highRiskTransaction("TXN-L6-OUTCOME-" + UUID.randomUUID()))
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);
        awaitAndApproveGate(caseId);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        final WorkItem review = findComplianceReviewWorkItem(caseId);
        workItemService.completeFromSystem(review.id, "test-compliance-officer", "SAR approved");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> "sar-filed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("outcome.type")));
    }

    @Test
    void officer_rejection_surfaces_gate_rejected_outcome() {
        final String caseIdStr = given().contentType(ContentType.JSON)
                .body(highRiskTransaction("TXN-L6-REJECT-" + UUID.randomUUID()))
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);
        awaitAndApproveGate(caseId);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        final WorkItem review = findComplianceReviewWorkItem(caseId);
        workItemService.rejectFromSystem(review.id, "test-compliance-officer", "Insufficient evidence");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> "gate-rejected".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("outcome.type")));
    }

    private WorkItem findComplianceReviewWorkItem(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItem w WHERE w.callerRef = :ref",
                WorkItem.class)
                .setParameter("ref", "aml:investigation:" + caseId)
                .getSingleResult());
    }

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                WorkItem.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }

    @Test
    void get_nonexistent_investigation_returns_404() {
        given().when().get("/api/layer6/investigations/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }
}
