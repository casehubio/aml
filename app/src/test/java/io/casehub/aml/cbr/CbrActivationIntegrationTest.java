package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.ledger.AmlCbrAdvisoryLedgerEntry;
import io.casehub.aml.rest.BootstrapReport;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CbrActivationIntegrationTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    CbrCaseMemoryStore cbrStore;

    @Inject
    WorkItemService workItemService;

    @Test
    @Order(1)
    void seedEndpoint_returnsCorrectCount() {
        cbrStore.eraseByScope(Path.root(), TENANT);

        given().contentType(ContentType.JSON)
                .body(Map.of("count", 20))
                .when().post("/api/simulation/seed/cbr")
                .then().statusCode(202)
                .body("seeded", org.hamcrest.Matchers.equalTo(20));
    }

    @Test
    @Order(2)
    void deleteEndpoint_returns204() {
        given().when().delete("/api/simulation/seed/cbr")
                .then().statusCode(204);
    }

    @Test
    @Order(3)
    void bootstrapReport_returnsReport() {
        var report = given().when().get("/api/cbr/bootstrap-report")
                .then().statusCode(200)
                .extract().as(BootstrapReport.class);
        assertNotNull(report);
        assertEquals(30, report.caseBase().activationThreshold());
    }

    @Test
    @Order(4)
    void learningMode_belowThreshold_advisorWritesActiveFalse() {
        cbrStore.eraseByScope(Path.root(), TENANT);

        var seeder = new CbrSyntheticSeeder(cbrStore);
        seeder.seed(5, TENANT);

        var tx = new SuspiciousTransaction(
                "TXN-CBR-LEARN-" + UUID.randomUUID(),
                "ACC-A", "ACC-B",
                new BigDecimal("120000"), "USD",
                Instant.now(),
                FlagReason.HIGH_RISK_JURISDICTION);

        String caseIdStr = given().contentType(ContentType.JSON).body(tx)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        UUID caseId = UUID.fromString(caseIdStr);
        awaitAndApproveGate(caseId);

        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().get("/api/layer6/investigations/" + caseIdStr)
                                .path("status")));

        List<AmlCbrAdvisoryLedgerEntry> advisories = QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT e FROM AmlCbrAdvisoryLedgerEntry e WHERE e.active = false",
                        AmlCbrAdvisoryLedgerEntry.class).getResultList());
        assertFalse(advisories.isEmpty(), "Should have learning-mode advisory with active=false");
    }

    private List<WorkItem> findGateWorkItems(UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                        "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                        WorkItem.class)
                        .setParameter("pattern", "case:" + caseId + "/gate:%")
                        .getResultList());
    }

    private void awaitAndApproveGate(UUID caseId) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        List<WorkItem> gateItems = findGateWorkItems(caseId);
        assertTrue(!gateItems.isEmpty(), "Gate WorkItem must exist");
        workItemService.completeFromSystem(gateItems.get(0).id, "test-mlro", "approved");
    }
}
