package io.casehub.aml.engine;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the prior context endpoint.
 * Tests the endpoint wiring and error handling.
 *
 * <p>Full integration tests that start investigations and verify prior context
 * are in {@link AmlLayer6InvestigationTest} and {@link AmlLayer8InvestigationTest}.
 */
@QuarkusTest
class AmlInvestigationPriorContextServiceTest {

    @Inject
    AmlInvestigationPriorContextService service;

    @Test
    void getPriorContext_nonExistentCase_returns404() {
        UUID nonExistentCaseId = UUID.randomUUID();

        given()
            .when()
            .get("/api/investigations/{caseId}/prior-context", nonExistentCaseId)
            .then()
            .statusCode(404);
    }

    @Test
    void service_injectionWorks() {
        // Verify the service is properly injected and available
        assertNotNull(service);
    }
}
