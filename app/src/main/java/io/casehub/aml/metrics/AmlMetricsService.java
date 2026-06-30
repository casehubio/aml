package io.casehub.aml.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.api.model.*;
import io.casehub.aml.query.InvestigationSummaryView;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.work.runtime.model.WorkItem;
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

    /**
     * Agent/capability pairs from trust-routing.yaml.
     * These are the agents whose trust scores we report.
     */
    private static final List<AgentCapability> KNOWN_AGENTS = List.of(
        new AgentCapability("sar-drafting-agent-senior", "sar-drafting"),
        new AgentCapability("sar-drafting-agent-junior", "sar-drafting"),
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
     * Compute throughput metrics from investigation summary view.
     */
    public ThroughputMetrics getThroughputMetrics() {
        TypedQuery<InvestigationSummaryView> query = em.createQuery(
            "SELECT i FROM InvestigationSummaryView i",
            InvestigationSummaryView.class
        );
        List<InvestigationSummaryView> investigations = query.getResultList();

        long total = investigations.size();

        Map<String, Long> byStatus = investigations.stream()
            .collect(Collectors.groupingBy(
                InvestigationSummaryView::status,
                Collectors.counting()
            ));

        Map<String, Long> byFlagReason = investigations.stream()
            .collect(Collectors.groupingBy(
                InvestigationSummaryView::flagReason,
                Collectors.counting()
            ));

        Map<String, Long> byOutcomeType = investigations.stream()
            .filter(i -> i.outcomeType() != null)
            .collect(Collectors.groupingBy(
                InvestigationSummaryView::outcomeType,
                Collectors.counting()
            ));

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
     */
    public GateMetrics getGateMetrics() {
        TypedQuery<WorkItem> query = em.createQuery(
            "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :prefix",
            WorkItem.class
        );
        query.setParameter("prefix", "case:%/gate:%");

        List<WorkItem> gates = query.getResultList();

        long total = gates.size();

        // Group by action type from payload
        Map<String, Long> byActionType = gates.stream()
            .map(this::extractActionType)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                actionType -> actionType,
                Collectors.counting()
            ));

        // Group by status
        Map<String, Long> byStatus = gates.stream()
            .collect(Collectors.groupingBy(
                w -> w.status != null ? w.status.name() : "UNKNOWN",
                Collectors.counting()
            ));

        // Calculate average approval time for completed gates
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
    private String extractActionType(WorkItem workItem) {
        try {
            JsonNode payload = MAPPER.readTree(workItem.payload);
            return payload.path("actionType").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private record AgentCapability(String agentId, String capabilityTag) {}
}
