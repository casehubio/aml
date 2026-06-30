package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.api.model.GateDecisionResponse;
import io.casehub.aml.api.model.InvestigationGatesResponse;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Queries oversight gate decisions for AML investigations.
 * Gates are WorkItems created by {@code ActionGateWorkItemHandler} with
 * {@code callerRef = "case:{caseId}/gate:{gateId}"}.
 */
@ApplicationScoped
public class AmlInvestigationGatesService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    EntityManager em;

    /**
     * Returns all gate decisions for the given investigation.
     * Results are ordered by creation time (oldest first).
     *
     * @param caseId Investigation case ID
     * @return Gate decisions response with all gates (pending, approved, rejected)
     */
    public InvestigationGatesResponse getGates(final UUID caseId) {
        String callerRefPrefix = "case:" + caseId + "/gate:";

        TypedQuery<WorkItem> query = em.createQuery(
            "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :prefix ORDER BY w.createdAt ASC",
            WorkItem.class
        );
        query.setParameter("prefix", callerRefPrefix + "%");

        List<WorkItem> workItems = query.getResultList();

        List<GateDecisionResponse> gates = workItems.stream()
            .map(this::toGateDecisionResponse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return new InvestigationGatesResponse(gates);
    }

    /**
     * Converts a WorkItem to a GateDecisionResponse.
     * Returns null if the payload cannot be parsed (invalid gate WorkItem).
     */
    private GateDecisionResponse toGateDecisionResponse(final WorkItem workItem) {
        try {
            JsonNode payload = MAPPER.readTree(workItem.payload);

            String actionTypeString = payload.path("actionType").asText(null);
            String description = payload.path("description").asText(null);
            boolean reversible = payload.path("reversible").asBoolean(false);

            // Derive gatePolicy from actionType via AmlActionType enum
            String gatePolicy = AmlActionType.fromActionType(actionTypeString)
                .map(amlActionType -> amlActionType.gatePolicy().name())
                .orElse("UNKNOWN");

            // Parse candidateGroups from WorkItem.candidateGroups (comma-separated string)
            List<String> candidateGroups = parseCandidateGroups(workItem.candidateGroups);

            // Determine approvedBy and approvedAt based on status
            String approvedBy = null;
            Instant approvedAt = null;
            if (workItem.status != null && workItem.status.isTerminal()) {
                approvedBy = workItem.assigneeId; // Last assigned actor who completed/rejected
                approvedAt = workItem.completedAt;
            }

            return new GateDecisionResponse(
                workItem.id,
                actionTypeString,
                gatePolicy,
                reversible,
                description,
                candidateGroups,
                workItem.status != null ? workItem.status.name() : "UNKNOWN",
                approvedBy,
                approvedAt,
                workItem.expiresAt
            );

        } catch (Exception e) {
            // Invalid payload — skip this WorkItem
            return null;
        }
    }

    /**
     * Parses candidateGroups from CSV string to List.
     * Returns empty list if null or empty.
     */
    private List<String> parseCandidateGroups(final String candidateGroupsCsv) {
        if (candidateGroupsCsv == null || candidateGroupsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(candidateGroupsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
