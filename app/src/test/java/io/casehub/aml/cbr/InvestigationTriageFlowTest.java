package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class InvestigationTriageFlowTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject CaseInstanceCache   caseInstanceCache;
    @Inject WorkItemService     workItemService;
    @PersistenceContext EntityManager defaultEm;

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
        WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private void drain(UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                  .until(() -> "completed".equals(
                          given().when().get("/api/layer6/investigations/" + caseId)
                                 .then().extract().path("status")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sarPath_shellCompanyHardGate_investigationCompletes() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-TRIAGE-SAR-" + UUID.randomUUID(),
                "ACC-TRIAGE-O-" + UUID.randomUUID(),
                "ACC-TRIAGE-D-" + UUID.randomUUID(),
                new BigDecimal("80000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);

        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var instance = caseInstanceCache.get(caseId);
        var ctx = instance.getCaseContext();
        assertThat(ctx.get("investigationTriage")).isNotNull();
        var triage = (Map<String, Object>) ctx.get("investigationTriage");
        assertThat(triage.get("decision")).isEqualTo("SAR_WARRANTED");
        assertThat(triage.get("hardGate")).isEqualTo("SHELL_COMPANY");
        assertThat(triage.get("riskScore")).isEqualTo(1.0);
        assertThat(ctx.get("complianceTaskId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void falsePositivePath_lowRisk_investigationCleared() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-FP-" + UUID.randomUUID(),
                "ACC-FP-O-" + UUID.randomUUID(),
                "ACC-FP-D-" + UUID.randomUUID(),
                new BigDecimal("500"), "USD", Instant.now(), FlagReason.LARGE_VOLUME);

        UUID caseId = coordinator.startInvestigation(tx);
        drain(caseId);

        var instance = caseInstanceCache.get(caseId);
        var ctx = instance.getCaseContext();
        var triage = (Map<String, Object>) ctx.get("investigationTriage");
        assertThat(triage.get("decision")).isEqualTo("FALSE_POSITIVE");
        assertThat(ctx.get("sarNarrative")).isNull();
        assertThat(ctx.get("complianceTaskId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void inconclusivePath_pepMatch_gateApproved_investigationCleared() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-INC-" + UUID.randomUUID(),
                "ACC-INC-O-" + UUID.randomUUID(),
                "ACC-INC-D-" + UUID.randomUUID(),
                new BigDecimal("15000"), "USD", Instant.now(), FlagReason.PEP_MATCH);

        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var instance = caseInstanceCache.get(caseId);
        var ctx = instance.getCaseContext();
        var triage = (Map<String, Object>) ctx.get("investigationTriage");
        assertThat(triage.get("decision")).isEqualTo("INCONCLUSIVE");
        assertThat(ctx.get("sarNarrative")).isNull();
        assertThat(ctx.get("complianceTaskId")).isNull();
    }
}
