package io.casehub.aml.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.api.model.InvestigationRoutingResponse;
import io.casehub.aml.api.model.InvestigationRoutingResponse.AlternativeCandidate;
import io.casehub.aml.api.model.InvestigationRoutingResponse.RoutingDecision;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AmlInvestigationRoutingService {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationRoutingService.class);

    @Inject AmlWorkerDecisionRepository decisionRepository;
    @Inject ObjectMapper objectMapper;

    public InvestigationRoutingResponse getRoutingDecisions(UUID caseId) {
        List<WorkerDecisionEntry> entries = decisionRepository.findAllByCaseId(caseId);
        List<RoutingDecision> decisions = entries.stream()
                .map(this::toRoutingDecision)
                .toList();
        return new InvestigationRoutingResponse(decisions);
    }

    private RoutingDecision toRoutingDecision(WorkerDecisionEntry entry) {
        List<AlternativeCandidate> alternatives = Collections.emptyList();
        String rationale = null;

        if (entry.routingRationale != null) {
            try {
                Map<String, Object> ctx = objectMapper.readValue(
                        entry.routingRationale, new TypeReference<>() {});
                rationale = (String) ctx.get("rationale");
                Object alts = ctx.get("alternatives");
                if (alts instanceof List<?> altList) {
                    alternatives = altList.stream()
                            .filter(Map.class::isInstance)
                            .map(a -> {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> altMap = (Map<String, Object>) a;
                                String wid = String.valueOf(altMap.get("workerId"));
                                Double score = altMap.get("score") instanceof Number n ? n.doubleValue() : null;
                                return new AlternativeCandidate(wid, score);
                            })
                            .toList();
                }
            } catch (Exception e) {
                LOG.debugf(e, "Failed to parse routing rationale for entry %s", entry.getId());
            }
        }

        return new RoutingDecision(
                entry.capabilityTag,
                entry.workerId,
                entry.trustScoreAtRouting,
                alternatives,
                rationale);
    }
}
