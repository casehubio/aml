package io.casehub.aml.provenance;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AmlProvenanceResourceTest {

    @Inject CbrCaseMemoryStore cbrStore;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    private static final SuspiciousTransaction TX = new SuspiciousTransaction(
        "TXN-PROV-" + UUID.randomUUID(),
        "ACC-PROV-A", "ACC-PROV-B",
        new BigDecimal("50000"), "USD",
        Instant.parse("2024-12-01T00:00:00Z"),
        FlagReason.LAYERING);

    @Test
    void provenance_forCompletedInvestigation_returnsProvJson() {
        String caseIdStr = given().contentType(ContentType.JSON).body(TX)
            .when().post("/api/layer9/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer9/investigations/" + caseIdStr)
                    .then().extract().path("status")));

        given().when().get("/api/investigations/" + caseIdStr + "/provenance")
            .then().statusCode(200)
            .body("prefix.prov", equalTo("http://www.w3.org/ns/prov#"))
            .body("prefix.casehub", equalTo("urn:casehub:ledger:"))
            .body("prefix.aml", equalTo("urn:casehub:aml:"))
            .body("entity.size()", greaterThan(0))
            .body("activity.size()", greaterThan(0))
            .body("agent.size()", greaterThan(0))
            .body("wasGeneratedBy.size()", greaterThan(0))
            .body("wasAttributedTo.size()", greaterThan(0));
    }

    @Test
    void provenance_forUnknownCaseId_returns404() {
        given().when().get("/api/investigations/" + UUID.randomUUID() + "/provenance")
            .then().statusCode(404);
    }
}
