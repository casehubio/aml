package io.casehub.aml.query;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class InvestigationTriageRiskScoreObserver {

    private static final Logger LOG = Logger.getLogger(InvestigationTriageRiskScoreObserver.class);
    private static final String TRIAGE_CAPABILITY = "investigation-triage";

    @Inject InvestigationSummaryService summaryService;
    @Inject CaseHubRuntime caseHubRuntime;

    void onTriageComplete(@ObservesAsync WorkerDecisionEvent event) {
        if (!TRIAGE_CAPABILITY.equals(event.capabilityTag())) {
            return;
        }

        try {
            Object triageResult = caseHubRuntime.query(event.caseId(), "investigationTriage");
            if (triageResult instanceof Map<?, ?> resultMap) {
                Object riskScoreObj = resultMap.get("riskScore");
                if (riskScoreObj instanceof Number num) {
                    summaryService.updateRiskScore(event.caseId(), num.doubleValue());
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to capture riskScore for case %s after triage completion",
                event.caseId());
        }
    }
}
