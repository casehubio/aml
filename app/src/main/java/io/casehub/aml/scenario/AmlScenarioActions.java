package io.casehub.aml.scenario;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.trust.AmlTrustScoreSeeder;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.internal.model.CaseStatus;
import io.casehub.engine.common.spi.CaseInstanceCache;
import io.casehub.pages.scenario.client.ScenarioAction;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AmlScenarioActions {

    private static final Logger LOG = Logger.getLogger(AmlScenarioActions.class);

    @Inject AmlEngineCoordinator coordinator;
    @Inject AmlTrustScoreSeeder trustSeeder;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject EntityManager em;

    @ScenarioAction("start-investigation")
    public Map<String, Object> startInvestigation(Map<String, Object> params) {
        BigDecimal amount = new BigDecimal(String.valueOf(params.getOrDefault("amount", "150000")));
        String flagReasonStr = String.valueOf(params.getOrDefault("flagReason", "HIGH_RISK_JURISDICTION"));
        String origin = String.valueOf(params.getOrDefault("originAccount", "ACC-DEMO-001"));
        String dest = String.valueOf(params.getOrDefault("destinationAccount", "ACC-DEMO-002"));
        String currency = String.valueOf(params.getOrDefault("currency", "USD"));

        FlagReason flagReason = FlagReason.valueOf(flagReasonStr);
        SuspiciousTransaction txn = new SuspiciousTransaction(
                UUID.randomUUID().toString(), origin, dest, amount, currency, flagReason);

        UUID caseId = coordinator.startInvestigation(txn);
        LOG.infof("Scenario: started investigation caseId=%s flagReason=%s", caseId, flagReason);
        return Map.of("caseId", caseId.toString());
    }

    @Transactional
    @ScenarioAction("approve-gate")
    public Map<String, Object> approveGate(Map<String, Object> params) {
        String caseId = requireParam(params, "caseId");
        String actionType = String.valueOf(params.getOrDefault("actionType", "sar.filing"));

        List<WorkItemEntity> gates = em.createQuery(
                "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix AND CAST(w.status AS string) = :status",
                WorkItemEntity.class)
                .setParameter("prefix", "case:" + caseId + "/gate:%")
                .setParameter("status", WorkItemStatus.PENDING.name())
                .getResultList();

        for (WorkItemEntity gate : gates) {
            if (gate.callerRef.contains(actionType) || gates.size() == 1) {
                gate.status = WorkItemStatus.COMPLETED;
                gate.completedAt = java.time.Instant.now();
                LOG.infof("Scenario: approved gate workItemId=%s actionType=%s", gate.id(), actionType);
                return Map.of("workItemId", gate.id().toString(), "approved", true);
            }
        }
        return Map.of("approved", false, "reason", "No matching pending gate found");
    }

    @ScenarioAction("seed-trust-scores")
    public Map<String, Object> seedTrustScores(Map<String, Object> params) {
        trustSeeder.seedInitialScores();
        LOG.info("Scenario: seeded trust scores");
        return Map.of("seeded", true);
    }

    @ScenarioAction("wait-for-completion")
    public Map<String, Object> waitForCompletion(Map<String, Object> params) {
        String caseIdStr = requireParam(params, "caseId");
        UUID caseId = UUID.fromString(caseIdStr);
        int timeoutMs = params.containsKey("timeout") ? Integer.parseInt(String.valueOf(params.get("timeout"))) : 30000;

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            CaseInstance instance = caseInstanceCache.get(caseId);
            if (instance != null && instance.getState() == CaseStatus.COMPLETED) {
                return Map.of("status", "COMPLETED", "caseId", caseIdStr);
            }
            if (instance != null && instance.getState() == CaseStatus.FAILED) {
                return Map.of("status", "FAILED", "caseId", caseIdStr);
            }
            try { TimeUnit.MILLISECONDS.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        CaseInstance finalState = caseInstanceCache.get(caseId);
        String status = finalState != null && finalState.getState() != null ? finalState.getState().name() : "UNKNOWN";
        return Map.of("status", status, "caseId", caseIdStr, "timedOut", true);
    }

    private static String requireParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return String.valueOf(value);
    }
}
