package io.casehub.aml.engine;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class ZAmlAuditTrailResourceTest {

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    @Test
    void getAuditTrail_returnsLedgerEntriesForCase() {
        final UUID caseId = startAndDrainInvestigation();

        given()
            .when()
            .get("/api/investigations/" + caseId + "/audit-trail")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].entryId", notNullValue())
            .body("[0].entryType", notNullValue())
            .body("[0].occurredAt", notNullValue());
    }

    @Test
    void getAuditTrail_returnsEmptyForNonexistentCase() {
        given()
            .when()
            .get("/api/investigations/" + UUID.randomUUID() + "/audit-trail")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    private UUID startAndDrainInvestigation() {
        final SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-AUDIT-" + UUID.randomUUID(),
                "acct-001", "acct-002",
                new BigDecimal("200000"), "USD",
                Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);

        final String caseIdStr = given()
                .contentType(ContentType.JSON)
                .body(tx)
                .when()
                .post("/api/layer6/investigations")
                .then()
                .statusCode(202)
                .extract()
                .path("caseId");
        final UUID caseId = UUID.fromString(caseIdStr);

        awaitAndApproveGate(caseId);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                    given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));

        return caseId;
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                WorkItem.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }
}
