package io.casehub.aml.metrics;

import io.casehub.aml.api.model.AgentTrustScore;
import io.casehub.aml.api.model.GateMetrics;
import io.casehub.aml.api.model.ThroughputMetrics;
import io.casehub.aml.api.model.TrustScoreMetrics;
import io.casehub.aml.query.InvestigationSummaryView;
import io.casehub.aml.trust.AmlTrustScoreSeeder;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integration test for AML metrics endpoints.
 * Tests throughput, trust score, and gate metrics aggregation.
 */
@QuarkusTest
class AmlMetricsResourceTest {

    @Inject
    EntityManager em;

    @Inject
    WorkItemService workItemService;

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Inject
    AmlTrustScoreSeeder trustScoreSeeder;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing data
        em.createQuery("DELETE FROM InvestigationSummaryView").executeUpdate();
        em.createQuery("DELETE FROM WorkItemEntity").executeUpdate();
    }

    @Test
    void testThroughputMetrics_emptyDatabase() {
        ThroughputMetrics metrics = RestAssured
            .given()
                .accept(ContentType.JSON)
            .when()
                .get("/api/metrics/throughput")
            .then()
                .statusCode(200)
                .extract()
                .as(ThroughputMetrics.class);

        assertThat(metrics.totalInvestigations(), is(0L));
        assertThat(metrics.byStatus(), is(anEmptyMap()));
        assertThat(metrics.byFlagReason(), is(anEmptyMap()));
        assertThat(metrics.byOutcomeType(), is(anEmptyMap()));
    }

    @Test
    void testThroughputMetrics_withInvestigations() {
        // Create test investigations in a committed transaction
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            UUID caseId1 = UUID.randomUUID();
            UUID caseId2 = UUID.randomUUID();
            UUID caseId3 = UUID.randomUUID();

            InvestigationSummaryView view1 = new InvestigationSummaryView(
                caseId1, "tx-001", "ACC-001", "ACC-002",
                new BigDecimal("50000.00"), "USD", "high-risk-jurisdiction"
            );
            view1.updateStatus("COMPLETED");
            view1.updateOutcomeType("SAR_FILED");
            em.persist(view1);

            InvestigationSummaryView view2 = new InvestigationSummaryView(
                caseId2, "tx-002", "ACC-003", "ACC-004",
                new BigDecimal("25000.00"), "USD", "velocity-anomaly"
            );
            view2.updateStatus("IN_PROGRESS");
            em.persist(view2);

            InvestigationSummaryView view3 = new InvestigationSummaryView(
                caseId3, "tx-003", "ACC-005", "ACC-006",
                new BigDecimal("75000.00"), "USD", "high-risk-jurisdiction"
            );
            view3.updateStatus("COMPLETED");
            view3.updateOutcomeType("SAR_DECLINED");
            em.persist(view3);
        });

        ThroughputMetrics metrics = RestAssured
            .given()
                .accept(ContentType.JSON)
            .when()
                .get("/api/metrics/throughput")
            .then()
                .statusCode(200)
                .extract()
                .as(ThroughputMetrics.class);

        assertThat(metrics.totalInvestigations(), is(3L));
        assertThat(metrics.byStatus().get("COMPLETED"), is(2L));
        assertThat(metrics.byStatus().get("IN_PROGRESS"), is(1L));
        assertThat(metrics.byFlagReason().get("high-risk-jurisdiction"), is(2L));
        assertThat(metrics.byFlagReason().get("velocity-anomaly"), is(1L));
        assertThat(metrics.byOutcomeType().get("SAR_FILED"), is(1L));
        assertThat(metrics.byOutcomeType().get("SAR_DECLINED"), is(1L));
    }

    @Test
    void testTrustScoreMetrics_withSeededScores() {
        // Trust scores are seeded at startup by AmlTrustScoreSeeder
        TrustScoreMetrics metrics = RestAssured
            .given()
                .accept(ContentType.JSON)
            .when()
                .get("/api/metrics/trust-scores")
            .then()
                .statusCode(200)
                .extract()
                .as(TrustScoreMetrics.class);

        assertThat(metrics.scores(), is(not(empty())));

        // Verify specific seeded agents
        AgentTrustScore sarSenior = metrics.scores().stream()
            .filter(s -> s.agentId().equals("sar-drafting-agent-senior"))
            .findFirst()
            .orElse(null);

        assertThat(sarSenior, is(notNullValue()));
        assertThat(sarSenior.capabilityTag(), is("sar-drafting"));
        assertThat(sarSenior.score(), is(notNullValue()));
        assertThat(sarSenior.score(), is(greaterThan(0.8))); // alpha=9, beta=1 → ~0.9
    }

    @Test
    void testGateMetrics_emptyDatabase() {
        GateMetrics metrics = RestAssured
            .given()
                .accept(ContentType.JSON)
            .when()
                .get("/api/metrics/gates")
            .then()
                .statusCode(200)
                .extract()
                .as(GateMetrics.class);

        assertThat(metrics.totalGates(), is(0L));
        assertThat(metrics.byActionType(), is(anEmptyMap()));
        assertThat(metrics.byStatus(), is(anEmptyMap()));
        assertThat(metrics.averageApprovalTimeSeconds(), is(nullValue()));
    }

    @Test
    void testGateMetrics_withGates() {
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.now();

        // Create gates in a committed transaction
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            // Create pending gate
            String payload1 = """
                {
                    "actionType": "sar.filing",
                    "description": "File SAR for case %s",
                    "reversible": false
                }
                """.formatted(caseId);

            workItemService.create(WorkItemCreateRequest.builder()
                .title("Gate: SAR Filing")
                .payload(payload1)
                .priority(WorkItemPriority.HIGH)
                .candidateGroups("aml-mlro")
                .callerRef("case:" + caseId + "/gate:sar-filing-1")
                .build());

            // Create completed gate (approved 60 seconds after creation)
            String payload2 = """
                {
                    "actionType": "account.restriction",
                    "description": "Restrict account for case %s",
                    "reversible": true
                }
                """.formatted(caseId);

            WorkItem gate2 = workItemService.create(WorkItemCreateRequest.builder()
                                                                               .title("Gate: Account Restriction")
                                                                               .payload(payload2)
                                                                               .priority(WorkItemPriority.HIGH)
                                                                               .candidateGroups("aml-mlro")
                                                                               .callerRef("case:" + caseId + "/gate:account-restriction-1")
                                                                               .build());
            UUID gate2Id = gate2.id();

            // Manually complete the gate with a specific completion time
            em.createQuery("""
                UPDATE WorkItemEntity w
                SET w.status = io.casehub.work.api.WorkItemStatus.COMPLETED,
                    w.assigneeId = 'mlro-001',
                    w.completedAt = :completedAt
                WHERE w.id = :id
                """)
                .setParameter("id", gate2Id)
                .setParameter("completedAt", now.plus(Duration.ofSeconds(60)))
                .executeUpdate();
        });

        GateMetrics metrics = RestAssured
            .given()
                .accept(ContentType.JSON)
            .when()
                .get("/api/metrics/gates")
            .then()
                .statusCode(200)
                .extract()
                .as(GateMetrics.class);

        assertThat(metrics.totalGates(), is(2L));
        assertThat(metrics.byActionType().get("sar.filing"), is(1L));
        assertThat(metrics.byActionType().get("account.restriction"), is(1L));
        assertThat(metrics.byStatus().get("PENDING"), is(1L));
        assertThat(metrics.byStatus().get("COMPLETED"), is(1L));
        assertThat(metrics.averageApprovalTimeSeconds(), is(notNullValue()));
        assertThat(metrics.averageApprovalTimeSeconds(), is(closeTo(60.0, 1.0)));
    }

    @Test
    void testSarQualityMetrics_returnsValidStructure() {
        io.casehub.aml.quality.SarQualityReport report = io.restassured.RestAssured
                                                                 .given()
                                                                 .accept(io.restassured.http.ContentType.JSON)
                                                                 .when()
                                                                 .get("/api/metrics/sar-quality")
                                                                 .then()
                                                                 .statusCode(200)
                                                                 .extract()
                                                                 .as(io.casehub.aml.quality.SarQualityReport.class);

        assertThat(report.seeded(), is(notNullValue()));
        assertThat(report.unseeded(), is(notNullValue()));
        assertThat(report.bySeedCount(), is(notNullValue()));
        assertThat(report.totalCases(), is(report.seeded().total() + report.unseeded().total()));
    }
}
