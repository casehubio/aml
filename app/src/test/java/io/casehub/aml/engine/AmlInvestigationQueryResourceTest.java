package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.domain.PagedResponse;
import io.casehub.aml.query.InvestigationSummaryView;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlInvestigationQueryResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM InvestigationSummaryView").executeUpdate();
        });
    }

    @Test
    void listInvestigations_emptyDatabase_returnsEmptyList() {
        var response = given()
            .when()
            .get("/api/investigations")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PagedResponse<Object>>() {});

        assertTrue(response.items().isEmpty());
        assertEquals(0, response.total());
        assertEquals(0, response.page());
        assertEquals(25, response.pageSize());
    }

    @Test
    void listInvestigations_withData_returnsInvestigations() {
        // Given: two investigations in the database
        var caseId1 = UUID.randomUUID();
        var caseId2 = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            var summary1 = new InvestigationSummaryView(
                caseId1, "TX-001", "ACC-123", "ACC-456",
                new BigDecimal("10000.00"), "USD", "High value transaction"
            );
            em.persist(summary1);

            var summary2 = new InvestigationSummaryView(
                caseId2, "TX-002", "ACC-789", "ACC-101",
                new BigDecimal("50000.00"), "EUR", "Suspicious pattern"
            );
            summary2.updateStatus("COMPLETED");
            summary2.updateOutcomeType("SAR_FILED");
            em.persist(summary2);

            em.flush();
        });

        // When: fetching all investigations
        var response = given()
            .when()
            .get("/api/investigations")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PagedResponse<Object>>() {});

        // Then: both investigations are returned
        assertEquals(2, response.items().size());
        assertEquals(2, response.total());
        assertEquals(0, response.page());
        assertEquals(25, response.pageSize());
    }

    @Test
    void listInvestigations_withStatusFilter_returnsFilteredResults() {
        // Given: one IN_PROGRESS and one COMPLETED investigation
        var caseId1 = UUID.randomUUID();
        var caseId2 = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            var summary1 = new InvestigationSummaryView(
                caseId1, "TX-001", "ACC-123", "ACC-456",
                new BigDecimal("10000.00"), "USD", "High value transaction"
            );
            em.persist(summary1);

            var summary2 = new InvestigationSummaryView(
                caseId2, "TX-002", "ACC-789", "ACC-101",
                new BigDecimal("50000.00"), "EUR", "Suspicious pattern"
            );
            summary2.updateStatus("COMPLETED");
            summary2.updateOutcomeType("SAR_FILED");
            em.persist(summary2);

            em.flush();
        });

        // When: filtering by IN_PROGRESS status
        var response = given()
            .queryParam("status", "IN_PROGRESS")
            .when()
            .get("/api/investigations")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PagedResponse<Object>>() {});

        // Then: only the IN_PROGRESS investigation is returned
        assertEquals(1, response.items().size());
        assertEquals(1, response.total());
    }

    @Test
    void listInvestigations_withPagination_returnsPaginatedResults() {
        // Given: 30 investigations in the database
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 30; i++) {
                var summary = new InvestigationSummaryView(
                    UUID.randomUUID(), "TX-" + String.format("%03d", i),
                    "ACC-" + i, "ACC-" + (i + 1000),
                    new BigDecimal("1000.00"), "USD", "Test"
                );
                em.persist(summary);
            }
            em.flush();
        });

        // When: requesting page 1 with pageSize 10
        var response = given()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/api/investigations")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PagedResponse<Object>>() {});

        // Then: 10 items returned, total is 30, page is 1
        assertEquals(10, response.items().size());
        assertEquals(30, response.total());
        assertEquals(1, response.page());
        assertEquals(10, response.pageSize());
    }

    @Test
    void listInvestigations_withStatusFilterAndPagination_worksCorrectly() {
        // Given: 15 IN_PROGRESS and 15 COMPLETED investigations
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 15; i++) {
                var summary = new InvestigationSummaryView(
                    UUID.randomUUID(), "TX-IP-" + i,
                    "ACC-" + i, "ACC-" + (i + 1000),
                    new BigDecimal("1000.00"), "USD", "Test"
                );
                em.persist(summary);
            }
            for (int i = 0; i < 15; i++) {
                var summary = new InvestigationSummaryView(
                    UUID.randomUUID(), "TX-CP-" + i,
                    "ACC-" + (i + 2000), "ACC-" + (i + 3000),
                    new BigDecimal("2000.00"), "EUR", "Test"
                );
                summary.updateStatus("COMPLETED");
                summary.updateOutcomeType("SAR_FILED");
                em.persist(summary);
            }
            em.flush();
        });

        // When: filtering by COMPLETED with page 0, pageSize 10
        var response = given()
            .queryParam("status", "COMPLETED")
            .queryParam("page", 0)
            .queryParam("pageSize", 10)
            .when()
            .get("/api/investigations")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PagedResponse<Object>>() {});

        // Then: 10 items returned, total is 15 (only COMPLETED)
        assertEquals(10, response.items().size());
        assertEquals(15, response.total());
        assertEquals(0, response.page());
        assertEquals(10, response.pageSize());
    }

    @Test
    void getFindings_nonExistentCase_returnsPendingForAllSpecialists() {
        // Given: a case ID that doesn't exist in the engine
        UUID nonExistentCaseId = UUID.randomUUID();

        // When: fetching findings
        var response = given()
            .when()
            .get("/api/investigations/{caseId}/findings", nonExistentCaseId)
            .then()
            .statusCode(200)
            .extract()
            .as(InvestigationFindingsResponse.class);

        // Then: all specialists show PENDING status
        assertNotNull(response);
        assertEquals("PENDING", response.entityResolution().status());
        assertNull(response.entityResolution().result());
        assertEquals("PENDING", response.patternAnalysis().status());
        assertNull(response.patternAnalysis().result());
        assertEquals("PENDING", response.osintScreening().status());
        assertNull(response.osintScreening().result());
        assertEquals("PENDING", response.sarNarrative().status());
        assertNull(response.sarNarrative().result());
    }
}
