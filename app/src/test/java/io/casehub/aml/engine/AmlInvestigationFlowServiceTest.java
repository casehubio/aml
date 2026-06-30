package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.aml.api.model.FlowEdge;
import io.casehub.aml.api.model.FlowNode;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests investigation flow graph reconstruction from engine event log.
 */
class AmlInvestigationFlowServiceTest {

    @Mock
    private CaseHubRuntime caseHubRuntime;

    @Mock
    private AmlTrustAttestationRepository attestationRepository;

    private AmlInvestigationFlowService flowService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        flowService = new AmlInvestigationFlowService();
        flowService.caseHubRuntime = caseHubRuntime;
        flowService.attestationRepository = attestationRepository;
        objectMapper = new ObjectMapper();
    }

    @Test
    void investigationFlow_sequentialWorkers_buildsCorrectGraph() {
        // Given: a case with sequential worker events
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> events = new ArrayList<>();

        // entity-resolution scheduled
        events.add(createScheduledEvent("entity-resolution", "entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:00Z")));
        // entity-resolution completed
        events.add(createCompletedEvent("entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:05Z")));

        // pattern-analysis scheduled
        events.add(createScheduledEvent("pattern-analysis", "pattern-analysis-agent",
                Instant.parse("2024-01-01T10:00:06Z")));
        // pattern-analysis completed
        events.add(createCompletedEvent("pattern-analysis-agent",
                Instant.parse("2024-01-01T10:00:10Z")));

        when(caseHubRuntime.eventLog(eq(caseId), any()))
                .thenReturn(CompletableFuture.completedFuture(events));

        // Trust attestations
        AmlTrustRoutingAttestation att1 = new AmlTrustRoutingAttestation();
        att1.capabilityTag = "entity-resolution";
        att1.selectedWorkerId = "entity-resolution-agent";
        att1.trustScoreAtRouting = 0.85;

        AmlTrustRoutingAttestation att2 = new AmlTrustRoutingAttestation();
        att2.capabilityTag = "pattern-analysis";
        att2.selectedWorkerId = "pattern-analysis-agent";
        att2.trustScoreAtRouting = 0.90;

        when(attestationRepository.findByInvestigationCaseId(caseId))
                .thenReturn(List.of(att1, att2));

        // When: reconstructing flow
        InvestigationFlowResponse flow = flowService.getInvestigationFlow(caseId)
                .toCompletableFuture().join();

        // Then: two nodes with sequential edge
        assertEquals(2, flow.nodes().size());

        FlowNode node1 = flow.nodes().get(0);
        assertEquals("entity-resolution", node1.capabilityTag());
        assertEquals("entity-resolution-agent", node1.workerId());
        assertEquals(0.85, node1.trustScoreAtRouting());
        assertEquals("completed", node1.status());

        FlowNode node2 = flow.nodes().get(1);
        assertEquals("pattern-analysis", node2.capabilityTag());
        assertEquals("pattern-analysis-agent", node2.workerId());
        assertEquals(0.90, node2.trustScoreAtRouting());
        assertEquals("completed", node2.status());

        // Edge from node 0 to node 1
        assertEquals(1, flow.edges().size());
        FlowEdge edge = flow.edges().get(0);
        assertEquals(0, edge.from());
        assertEquals(1, edge.to());

        // No parallel groups (all sequential)
        assertTrue(flow.parallelGroups().isEmpty());
    }

    @Test
    void investigationFlow_parallelWorkers_detectsParallelism() {
        // Given: a case with parallel worker events
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> events = new ArrayList<>();

        // entity-resolution scheduled
        events.add(createScheduledEvent("entity-resolution", "entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:00Z")));
        // entity-resolution completed
        events.add(createCompletedEvent("entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:05Z")));

        // pattern-analysis and osint-screening scheduled in parallel (consecutive WORKER_SCHEDULED)
        events.add(createScheduledEvent("pattern-analysis", "pattern-analysis-agent",
                Instant.parse("2024-01-01T10:00:06Z")));
        events.add(createScheduledEvent("osint-screening", "osint-screening-agent",
                Instant.parse("2024-01-01T10:00:06Z")));

        // pattern-analysis completed
        events.add(createCompletedEvent("pattern-analysis-agent",
                Instant.parse("2024-01-01T10:00:10Z")));
        // osint-screening completed
        events.add(createCompletedEvent("osint-screening-agent",
                Instant.parse("2024-01-01T10:00:12Z")));

        when(caseHubRuntime.eventLog(eq(caseId), any()))
                .thenReturn(CompletableFuture.completedFuture(events));
        when(attestationRepository.findByInvestigationCaseId(caseId))
                .thenReturn(List.of());

        // When: reconstructing flow
        InvestigationFlowResponse flow = flowService.getInvestigationFlow(caseId)
                .toCompletableFuture().join();

        // Then: three nodes
        assertEquals(3, flow.nodes().size());

        // Parallel group contains nodes 1 and 2 (pattern-analysis and osint-screening)
        assertEquals(1, flow.parallelGroups().size());
        List<Integer> parallelGroup = flow.parallelGroups().get(0);
        assertEquals(2, parallelGroup.size());
        assertTrue(parallelGroup.contains(1));
        assertTrue(parallelGroup.contains(2));

        // Edges: entity-resolution (0) to pattern-analysis (1) and to osint-screening (2)
        assertEquals(2, flow.edges().size());
        assertTrue(flow.edges().stream().anyMatch(e -> e.from() == 0 && e.to() == 1));
        assertTrue(flow.edges().stream().anyMatch(e -> e.from() == 0 && e.to() == 2));
    }

    @Test
    void investigationFlow_failedWorker_statusIsFailed() {
        // Given: a case with a failed worker
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> events = new ArrayList<>();

        // entity-resolution scheduled
        events.add(createScheduledEvent("entity-resolution", "entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:00Z")));
        // entity-resolution failed
        events.add(createFailedEvent("entity-resolution-agent",
                Instant.parse("2024-01-01T10:00:05Z")));

        when(caseHubRuntime.eventLog(eq(caseId), any()))
                .thenReturn(CompletableFuture.completedFuture(events));
        when(attestationRepository.findByInvestigationCaseId(caseId))
                .thenReturn(List.of());

        // When: reconstructing flow
        InvestigationFlowResponse flow = flowService.getInvestigationFlow(caseId)
                .toCompletableFuture().join();

        // Then: one node with failed status
        assertEquals(1, flow.nodes().size());
        FlowNode node = flow.nodes().get(0);
        assertEquals("failed", node.status());
    }

    @Test
    void investigationFlow_noAttestations_trustScoreIsNull() {
        // Given: a case with no trust attestations
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> events = List.of(
                createScheduledEvent("entity-resolution", "entity-resolution-agent",
                        Instant.parse("2024-01-01T10:00:00Z")));

        when(caseHubRuntime.eventLog(eq(caseId), any()))
                .thenReturn(CompletableFuture.completedFuture(events));
        when(attestationRepository.findByInvestigationCaseId(caseId))
                .thenReturn(List.of());

        // When: reconstructing flow
        InvestigationFlowResponse flow = flowService.getInvestigationFlow(caseId)
                .toCompletableFuture().join();

        // Then: trust score is null
        assertEquals(1, flow.nodes().size());
        assertNull(flow.nodes().get(0).trustScoreAtRouting());
    }

    // Helper methods to create event log records

    private CaseEventLogRecord createScheduledEvent(String capabilityTag, String workerId, Instant timestamp) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("workerName", workerId);
        metadata.put("capabilityName", capabilityTag);
        metadata.put("inputDataHash", "hash123");

        return new CaseEventLogRecord(
                CaseHubEventType.WORKER_SCHEDULED,
                EventStreamType.CASE,
                timestamp,
                objectMapper.createObjectNode(), // payload
                metadata);
    }

    private CaseEventLogRecord createCompletedEvent(String workerId, Instant timestamp) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("workerName", workerId);

        return new CaseEventLogRecord(
                CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                EventStreamType.CASE,
                timestamp,
                objectMapper.createObjectNode(), // payload
                metadata);
    }

    private CaseEventLogRecord createFailedEvent(String workerId, Instant timestamp) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("workerName", workerId);

        return new CaseEventLogRecord(
                CaseHubEventType.WORKER_EXECUTION_FAILED,
                EventStreamType.CASE,
                timestamp,
                objectMapper.createObjectNode(), // payload
                metadata);
    }
}
