package io.casehub.aml.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.api.model.*;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing AML investigation metrics.
 * Aggregates data from InvestigationSummaryView, TrustScoreSource, and WorkItem
 * for the Operations view dashboards.
 */
@ApplicationScoped
public class AmlMetricsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<AgentCapability> KNOWN_AGENTS = List.of(
            new AgentCapability("sar-drafting-agent-senior", "sar-drafting"),
            new AgentCapability("osint-screening-agent-senior", "osint-screening"),
            new AgentCapability("osint-screening-agent", "osint-screening"),
            new AgentCapability("entity-resolution-agent", "entity-resolution"),
            new AgentCapability("pattern-analysis-agent", "pattern-analysis"),
            new AgentCapability("senior-analyst-agent", "senior-analyst-review"),
            new AgentCapability("compliance-review-opening-agent", "compliance-review-opening")
                                                                     );

    @Inject
    EntityManager em;

    @Inject
    TrustScoreSource trustScoreSource;

    /**
     * Compute throughput metrics from investigation summary view using aggregate queries.
     */
    public ThroughputMetrics getThroughputMetrics() {
        long total = em.createQuery(
            "SELECT COUNT(i) FROM InvestigationSummaryView i", Long.class
        ).getSingleResult();

        Map<String, Long> byStatus = aggregateToMap(
            em.createQuery(
                "SELECT i.status, COUNT(i) FROM InvestigationSummaryView i GROUP BY i.status",
                Object[].class
            ).getResultList()
        );

        Map<String, Long> byFlagReason = aggregateToMap(
            em.createQuery(
                "SELECT i.flagReason, COUNT(i) FROM InvestigationSummaryView i GROUP BY i.flagReason",
                Object[].class
            ).getResultList()
        );

        Map<String, Long> byOutcomeType = aggregateToMap(
            em.createQuery(
                "SELECT i.outcomeType, COUNT(i) FROM InvestigationSummaryView i WHERE i.outcomeType IS NOT NULL GROUP BY i.outcomeType",
                Object[].class
            ).getResultList()
        );

        return new ThroughputMetrics(total, byStatus, byFlagReason, byOutcomeType);
    }

    /**
     * Compute trust score metrics for all known agents.
     */
    public TrustScoreMetrics getTrustScoreMetrics() {
        List<AgentTrustScore> scores = KNOWN_AGENTS.stream()
            .map(ac -> {
                OptionalDouble score = trustScoreSource.capabilityScore(ac.agentId(), ac.capabilityTag());
                return new AgentTrustScore(
                    ac.agentId(),
                    ac.capabilityTag(),
                    score.isPresent() ? score.getAsDouble() : null
                );
            })
            .collect(Collectors.toList());

        return new TrustScoreMetrics(scores);
    }

    /**
     * Compute gate metrics from WorkItems with callerRef pattern matching gates.
     * Status counts use aggregate queries; action type and approval time still load
     * entities because actionType lives in the JSON payload (not a DB column).
     */
    public GateMetrics getGateMetrics() {
        long total = em.createQuery(
            "SELECT COUNT(w) FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix", Long.class
        ).setParameter("prefix", "case:%/gate:%").getSingleResult();

        Map<String, Long> byStatus = aggregateToMap(
            em.createQuery(
                "SELECT COALESCE(CAST(w.status AS string), 'UNKNOWN'), COUNT(w) FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix GROUP BY w.status",
                Object[].class
            ).setParameter("prefix", "case:%/gate:%").getResultList()
        );

        // actionType lives in JSON payload — must load entities for extraction
        TypedQuery<WorkItemEntity> gateQuery = em.createQuery(
            "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix",
            WorkItemEntity.class
                                                             );
        gateQuery.setParameter("prefix", "case:%/gate:%");
        List<WorkItemEntity> gates = gateQuery.getResultList();

        Map<String, Long> byActionType = gates.stream()
            .map(this::extractActionType)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                actionType -> actionType,
                Collectors.counting()
            ));

        Double avgApprovalTime = gates.stream()
            .filter(w -> w.status != null && w.status.isTerminal())
            .filter(w -> w.completedAt != null && w.createdAt != null)
            .mapToDouble(w -> Duration.between(w.createdAt, w.completedAt).toSeconds())
            .average()
            .orElse(Double.NaN);

        return new GateMetrics(
            total,
            byActionType,
            byStatus,
            Double.isNaN(avgApprovalTime) ? null : avgApprovalTime
        );
    }

    /**
     * Extract actionType from WorkItem payload JSON.
     * Returns null if payload cannot be parsed or actionType is missing.
     */
    private String extractActionType(WorkItemEntity workItem) {
        try {
            JsonNode payload = MAPPER.readTree(workItem.payload);
            return payload.path("actionType").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public InterventionMetrics getInterventionMetrics() {
        long gateRejections = em.createQuery(
            "SELECT COUNT(w) FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix AND CAST(w.status AS string) = 'REJECTED'", Long.class
        ).setParameter("prefix", "case:%/gate:%").getSingleResult();

        long escalations = em.createQuery(
            "SELECT COUNT(w) FROM WorkItemEntity w WHERE w.callerRef LIKE :escPrefix", Long.class
        ).setParameter("escPrefix", "case:%/escalation:%").getSingleResult();

        List<InterventionMetrics.RecentIntervention> recent = new ArrayList<>();

        TypedQuery<WorkItemEntity> rejectedGates = em.createQuery(
            "SELECT w FROM WorkItemEntity w WHERE w.callerRef LIKE :prefix AND CAST(w.status AS string) = 'REJECTED' ORDER BY w.completedAt DESC",
            WorkItemEntity.class
        );
        rejectedGates.setParameter("prefix", "case:%/gate:%");
        rejectedGates.setMaxResults(10);
        for (WorkItemEntity wi : rejectedGates.getResultList()) {
            String caseId = extractCaseIdFromCallerRef(wi.callerRef);
            recent.add(new InterventionMetrics.RecentIntervention(
                "GATE_REJECTION", caseId != null ? caseId : "", extractActionType(wi), "", wi.completedAt != null ? wi.completedAt.toString() : ""));
        }

        return new InterventionMetrics(
            (int) escalations,
            0,
            0,
            (int) gateRejections,
            0.0,
            recent
        );
    }

    private static final java.util.regex.Pattern GATE_CALLER_REF =
        java.util.regex.Pattern.compile("^case:(.+)/gate:.+$");

    private String extractCaseIdFromCallerRef(String callerRef) {
        if (callerRef == null) return null;
        java.util.regex.Matcher m = GATE_CALLER_REF.matcher(callerRef);
        return m.matches() ? m.group(1) : null;
    }

    private Map<String, Long> aggregateToMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return result;
    }

    private record AgentCapability(String agentId, String capabilityTag) {}
}
