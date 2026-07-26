package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.aml.api.model.FlowEdge;
import io.casehub.aml.api.model.FlowNode;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.api.model.event.CaseHubEventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reconstructs the investigation flow DAG from the engine's event log for visualization.
 * Shows which specialists were dispatched, in what order, which ran in parallel, and their
 * outcomes.
 *
 * <p><strong>Parallel detection algorithm:</strong> Two consecutive {@code WORKER_SCHEDULED}
 * events in the event log with no intervening {@code WORKER_EXECUTION_COMPLETED} are parallel.
 * Group consecutive WORKER_SCHEDULED events that are not separated by WORKER_EXECUTION_COMPLETED.
 *
 * <p><strong>Edge direction:</strong> {@code from → to} means "worker at {@code from} completed
 * before worker at {@code to} was scheduled." The edge connects the most recently completed
 * worker to the next scheduled worker.
 */
@ApplicationScoped
public class AmlInvestigationFlowService {

    @Inject
    CaseHubRuntime caseHubRuntime;

    @Inject
    AmlTrustAttestationRepository attestationRepository;

    public InvestigationFlowResponse getInvestigationFlow(UUID caseId) {
        var events = caseHubRuntime.eventLog(caseId, Set.of(
                CaseHubEventType.WORKER_SCHEDULED,
                CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                CaseHubEventType.WORKER_EXECUTION_FAILED));
        return buildFlowResponse(caseId, events);
    }

    private InvestigationFlowResponse buildFlowResponse(UUID caseId, List<CaseEventLogRecord> events) {
        // Load all attestations for this case once — map by (capabilityTag, workerId) for O(1) lookup
        Map<String, AmlTrustRoutingAttestation> attestationMap = buildAttestationMap(caseId);

        List<FlowNode> nodes = new ArrayList<>();
        List<FlowEdge> edges = new ArrayList<>();
        List<List<Integer>> parallelGroups = new ArrayList<>();

        // Track the last completed worker index for edge construction
        Integer lastCompletedIndex = null;

        // Track consecutive WORKER_SCHEDULED events for parallel group detection
        List<Integer> currentParallelGroup = new ArrayList<>();

        for (CaseEventLogRecord event : events) {
            if (event.eventType() == CaseHubEventType.WORKER_SCHEDULED) {
                String workerId = extractWorkerId(event);
                String capabilityTag = extractCapabilityTag(event);
                Instant timestamp = event.timestamp();

                // Lookup trust score from attestation
                String key = capabilityTag + ":" + workerId;
                Double trustScore = attestationMap.containsKey(key)
                        ? attestationMap.get(key).trustScoreAtRouting
                        : null;

                // Create node with "scheduled" status initially
                FlowNode node = new FlowNode(capabilityTag, workerId, trustScore, "scheduled", timestamp);
                int nodeIndex = nodes.size();
                nodes.add(node);

                // Add to current parallel group
                currentParallelGroup.add(nodeIndex);

                // Create edge from last completed worker to this scheduled worker
                if (lastCompletedIndex != null) {
                    edges.add(new FlowEdge(lastCompletedIndex, nodeIndex));
                }

            } else if (event.eventType() == CaseHubEventType.WORKER_EXECUTION_COMPLETED
                    || event.eventType() == CaseHubEventType.WORKER_EXECUTION_FAILED) {

                // Finalize parallel group if it has multiple workers
                if (currentParallelGroup.size() > 1) {
                    parallelGroups.add(new ArrayList<>(currentParallelGroup));
                }
                currentParallelGroup.clear();

                // Find the node that corresponds to this completion/failure
                String workerId = extractWorkerId(event);
                int nodeIndex = findNodeIndexByWorkerId(nodes, workerId);

                if (nodeIndex >= 0) {
                    // Update node status
                    FlowNode oldNode = nodes.get(nodeIndex);
                    String newStatus = event.eventType() == CaseHubEventType.WORKER_EXECUTION_COMPLETED
                            ? "completed" : "failed";
                    FlowNode updatedNode = new FlowNode(
                            oldNode.capabilityTag(),
                            oldNode.workerId(),
                            oldNode.trustScoreAtRouting(),
                            newStatus,
                            oldNode.timestamp());
                    nodes.set(nodeIndex, updatedNode);

                    // Track for edge construction (both completed and failed maintain continuity)
                    lastCompletedIndex = nodeIndex;
                }
            }
        }

        // Finalize any remaining parallel group
        if (currentParallelGroup.size() > 1) {
            parallelGroups.add(new ArrayList<>(currentParallelGroup));
        }

        return new InvestigationFlowResponse(nodes, edges, parallelGroups);
    }

    private Map<String, AmlTrustRoutingAttestation> buildAttestationMap(UUID caseId) {
        List<AmlTrustRoutingAttestation> attestations = attestationRepository.findByInvestigationCaseId(caseId);
        Map<String, AmlTrustRoutingAttestation> map = new HashMap<>();
        for (AmlTrustRoutingAttestation att : attestations) {
            String key = att.capabilityTag + ":" + att.selectedWorkerId;
            map.put(key, att);
        }
        return map;
    }

    private String extractWorkerId(CaseEventLogRecord event) {
        JsonNode metadata = event.metadata();
        if (metadata != null && metadata.has("workerName")) {
            return metadata.get("workerName").asText();
        }
        // Fallback: some events may have workerId at the root level (engine internal)
        return "unknown";
    }

    private String extractCapabilityTag(CaseEventLogRecord event) {
        JsonNode metadata = event.metadata();
        if (metadata != null && metadata.has("capabilityName")) {
            return metadata.get("capabilityName").asText();
        }
        return "unknown";
    }

    private int findNodeIndexByWorkerId(List<FlowNode> nodes, String workerId) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).workerId().equals(workerId)) {
                return i;
            }
        }
        return -1; // Not found
    }
}
