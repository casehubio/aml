package io.casehub.aml.supervisor;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SupervisorDegradedInvestigationTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject AmlSupervisorLlmAdapter llmAdapter;
    @Inject io.casehub.work.runtime.service.WorkItemService workItemService;
    @Inject jakarta.persistence.EntityManager defaultEm;

    @Test
    @SuppressWarnings("unchecked")
    void degraded_supervisor_completes_identically_to_choreography() {
        assertThat(llmAdapter.isAvailable()).isFalse();

        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-DEGRADE-" + UUID.randomUUID(),
                "ACC-DEG-O-" + UUID.randomUUID(),
                "ACC-DEG-D-" + UUID.randomUUID(),
                new BigDecimal("80000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);

        UUID caseId = coordinator.startInvestigation(tx);

        awaitAndApproveGate(caseId);
        drain(caseId);

        var instance = caseInstanceCache.get(caseId);
        var ctx = instance.getCaseContext();
        assertThat(ctx.get("investigationTriage")).isNotNull();
        var triage = (Map<String, Object>) ctx.get("investigationTriage");
        assertThat(triage.get("decision")).isEqualTo("SAR_WARRANTED");
    }

    private void awaitAndApproveGate(UUID caseId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        var gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private java.util.List<io.casehub.work.runtime.model.WorkItemEntity> findGateWorkItems(UUID caseId) {
        return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :pattern",
                                io.casehub.work.runtime.model.WorkItemEntity.class)
                        .setParameter("pattern", "case:" + caseId + "/gate:%")
                        .getResultList());
    }

    private void drain(UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseId)
                                .then().extract().path("status")));
    }
}
