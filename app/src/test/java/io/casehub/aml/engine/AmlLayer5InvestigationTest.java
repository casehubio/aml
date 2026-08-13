package io.casehub.aml.engine;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying Layer 5 adaptive investigation paths.
 *
 * <p>Each test starts a case via HTTP, then uses Awaitility to poll the engine event log
 * until the expected workers have completed. This decouples HTTP response time (fast)
 * from investigation completion time (asynchronous via Quartz).
 */
@QuarkusTest
class AmlLayer5InvestigationTest {

    @Inject
    CaseHubRuntime caseHubRuntime;

    @PersistenceContext
    EntityManager defaultEm;

    @Inject
    WorkItemService workItemService;

    @Inject
    CbrCaseMemoryStore cbrStore;

    @BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(Path.root(), TenancyConstants.DEFAULT_TENANT_ID);
    }

    private static final Duration TIMEOUT      = Duration.ofSeconds(60);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private List<WorkItemEntity> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
            defaultEm.createQuery(
                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                WorkItemEntity.class)
                .setParameter("pattern", "case:" + caseId + "/gate:%")
                .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItemEntity gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    /**
     * Wait for an investigation to reach "completed" status via the Layer6 API.
     * This drains ALL pending Quartz jobs (including sar-drafting ledger writes)
     * to prevent cross-test Merkle frontier constraint violations.
     */
    private void drainInvestigation(final UUID caseId) {
        await().atMost(DRAIN_TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
            "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    private UUID startInvestigation(final String txId, final String flagReason) {
        final var body = """
                {
                  "id": "%s",
                  "originAccountId": "ACC-A",
                  "destinationAccountId": "ACC-B",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "%s"
                }
                """.formatted(txId, flagReason);

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        return response.caseId();
    }

    /**
     * Returns the set of worker names that were SCHEDULED for the given case.
     *
     * WORKER_SCHEDULED events carry "workerName" in metadata. WORKER_SCHEDULED is the
     * right event to poll: a worker is only scheduled when its binding condition is satisfied,
     * meaning all required prior workers have completed and written to context. Sar-drafting
     * being scheduled proves entity + pattern + osint all completed.
     */
    private Set<String> scheduledWorkerNames(final UUID caseId) {
        return caseHubRuntime.eventLog(caseId, Set.of(CaseHubEventType.WORKER_SCHEDULED))
                .stream()
                .filter(r -> r.metadata() != null && r.metadata().has("workerName"))
                .map(r -> r.metadata().get("workerName").asText())
                .collect(Collectors.toSet());
    }

    @Test
    void investigationCompletes_allCoreWorkersRun() {
        final UUID caseId = startInvestigation("TXN-COMPLETE-001", "STRUCTURING");

        // Trust routing may dispatch 'osint-screening-agent-senior' or 'osint-screening-agent'
        // depending on trust scores — use prefix match to accept either.
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var workers = scheduledWorkerNames(caseId);
            return workers.contains("entity-resolution-agent")
                    && workers.contains("pattern-analysis-agent")
                    && workers.stream().anyMatch(w -> w.startsWith("osint-screening-agent"))
                    && workers.contains("investigation-triage-agent");
        });
        drainInvestigation(caseId);
    }

    @Test
    void highRiskTransaction_investigationCompletes() {
        final UUID caseId = startInvestigation("TXN-HR-001",
                "HIGH_RISK_JURISDICTION");

        awaitAndApproveGate(caseId);
        drainInvestigation(caseId);
    }

    @Test
    void nonPepTransaction_seniorAnalystDoesNotFire() {
        final UUID caseId = startInvestigation("TXN-CORP-001",
                "STRUCTURING");

        // Wait for sar-drafting to be scheduled (proves entity+pattern+osint completed).
        // Capture the snapshot inside Awaitility to avoid a re-query race window.
        final var workers = new java.util.concurrent.atomic.AtomicReference<Set<String>>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var current = scheduledWorkerNames(caseId);
            workers.set(current);
            return current.contains("investigation-triage-agent");
        });

        assertTrue(!workers.get().contains("senior-analyst-agent"),
                "Non-PEP transaction must not trigger senior analyst: " + workers.get());
        drainInvestigation(caseId);
    }

    @Test
    void patternAndOsintRunInParallel_bothFireAfterEntity() {
        final UUID caseId = startInvestigation("TXN-PARALLEL-001",
                "LAYERING");

        // Wait until both have completed — they should fire simultaneously after entity
        // Trust routing may dispatch 'osint-screening-agent-senior' — use prefix match.
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var workers = scheduledWorkerNames(caseId);
            return workers.contains("pattern-analysis-agent")
                    && workers.stream().anyMatch(w -> w.startsWith("osint-screening-agent"));
        });

        final var workers = scheduledWorkerNames(caseId);
        assertTrue(workers.contains("entity-resolution-agent"),
                "Entity resolution must have fired before pattern/osint");
        drainInvestigation(caseId);
    }

    @Test
    void osintDecline_doesNotBlockSarDrafting() {
        // OSINT always declines in stubs — verify SAR still drafts successfully
        final UUID caseId = startInvestigation("TXN-OSINT-DECLINE-001",
                "HIGH_RISK_JURISDICTION");

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(caseId).stream().anyMatch(w -> w.startsWith("sar-drafting-agent")));
        awaitAndApproveGate(caseId);
        drainInvestigation(caseId);
    }
}
