package io.casehub.aml.engine;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class ZAmlAuditTrailResourceTest {

    @Inject CbrCaseMemoryStore cbrStore;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

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
                .body("[0].discriminator", notNullValue())
                .body("[0].domainFields", notNullValue())
                .body("[0].occurredAt", notNullValue());}

    @Test
    void getAuditTrail_returnsEmptyForNonexistentCase() {
        given()
            .when()
            .get("/api/investigations/" + UUID.randomUUID() + "/audit-trail")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void getAuditTrail_firstEntryIsCaseOpenedWithDomainFields() {
        final UUID caseId = startAndDrainInvestigation();

        final List<Map<String, Object>> entries = given()
                .when()
                .get("/api/investigations/" + caseId + "/audit-trail")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath().getList("$");

        final Map<String, Object> caseOpened = entries.stream()
                .filter(e -> "AML_CASE_OPENED".equals(e.get("discriminator")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No AML_CASE_OPENED entry found; discriminators: "
                                + entries.stream().map(e -> e.get("discriminator")).toList()));

        @SuppressWarnings("unchecked")
        final Map<String, Object> domainFields = (Map<String, Object>) caseOpened.get("domainFields");
        assertNotNull(domainFields.get("transactionId"));
        assertEquals("acct-001", domainFields.get("originAccountId"));
        assertEquals("acct-002", domainFields.get("destinationAccountId"));
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
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                    given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));

        return caseId;
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItemEntity gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private List<WorkItemEntity> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                WorkItemEntity.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }
}
