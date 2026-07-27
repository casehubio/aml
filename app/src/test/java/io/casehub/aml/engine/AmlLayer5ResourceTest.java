package io.casehub.aml.engine;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AmlLayer5ResourceTest {

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                WorkItem.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    /** Drain: wait for investigation to complete to prevent Quartz contamination. */
    private void drain(final String caseId) {
        await().atMost(DRAIN_TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
            "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    @Test
    void startInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-001-%s",
                  "originAccountId": "ACC-A",
                  "destinationAccountId": "ACC-B",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "VELOCITY_ANOMALY"
                }
                """.formatted(UUID.randomUUID());

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        assertNotNull(response.caseId(), "caseId must be returned");
        assertNotNull(response.status(), "status must be returned");
        awaitAndApproveGate(response.caseId());
        drain(response.caseId().toString());
    }

    @Test
    void startPepInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-PEP-%s",
                  "originAccountId": "ACC-PEP",
                  "destinationAccountId": "ACC-B",
                  "amount": 95000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "PEP_MATCH"
                }
                """.formatted(UUID.randomUUID());

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        assertNotNull(response.caseId(), "PEP investigation must return a case ID");
        awaitAndApproveGate(response.caseId());
        drain(response.caseId().toString());
    }
}
