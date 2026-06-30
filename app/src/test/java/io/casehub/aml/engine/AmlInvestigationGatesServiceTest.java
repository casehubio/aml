package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.aml.api.model.GateDecisionResponse;
import io.casehub.aml.api.model.InvestigationGatesResponse;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests gate decision querying via {@link AmlInvestigationGatesService}.
 */
@QuarkusTest
class AmlInvestigationGatesServiceTest {

    @Inject
    AmlInvestigationGatesService gatesService;

    @Inject
    WorkItemService workItemService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getGates_noGates_returnsEmptyList() {
        // Given: investigation with no gates
        UUID caseId = UUID.randomUUID();

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: empty list returned
        assertNotNull(response);
        assertNotNull(response.gates());
        assertTrue(response.gates().isEmpty());
    }

    @Test
    void getGates_singlePendingGate_returnsGateWithNullApproval() {
        // Given: investigation with one pending SAR filing gate
        UUID caseId = UUID.randomUUID();
        long gateId = 1L;

        String callerRef = "case:" + caseId + "/gate:" + gateId;
        String payload = buildGatePayload("sar.filing", "SAR submission requires MLRO approval", false);

        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
            .title("SAR Filing Gate")
            .candidateGroups("aml-mlro")
            .createdBy("casehub-engine")
            .payload(payload)
            .callerRef(callerRef)
            .priority(WorkItemPriority.HIGH)
            .build();

        workItemService.create(request);

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: single gate with PENDING status, no approval
        assertNotNull(response);
        assertEquals(1, response.gates().size());

        GateDecisionResponse gate = response.gates().get(0);
        assertEquals("sar.filing", gate.actionType());
        assertEquals("ALWAYS", gate.gatePolicy());
        assertFalse(gate.reversible());
        assertEquals("SAR submission requires MLRO approval", gate.description());
        assertEquals(1, gate.candidateGroups().size());
        assertTrue(gate.candidateGroups().contains("aml-mlro"));
        assertEquals("PENDING", gate.status());
        assertNull(gate.approvedBy());
        assertNull(gate.approvedAt());
    }

    @Test
    void getGates_multipleGates_orderedByCreationTime() {
        // Given: investigation with multiple gates (different action types)
        UUID caseId = UUID.randomUUID();

        // Gate 1: SAR Filing (created first)
        String payload1 = buildGatePayload("sar.filing", "SAR submission", false);
        String callerRef1 = "case:" + caseId + "/gate:1";
        WorkItemCreateRequest req1 = WorkItemCreateRequest.builder()
            .title("SAR Filing Gate")
            .candidateGroups("aml-mlro")
            .createdBy("casehub-engine")
            .payload(payload1)
            .callerRef(callerRef1)
            .build();
        workItemService.create(req1);

        // Gate 2: Account Restriction (created second)
        String payload2 = buildGatePayload("account.restriction", "Account restriction requires approval", true);
        String callerRef2 = "case:" + caseId + "/gate:2";
        WorkItemCreateRequest req2 = WorkItemCreateRequest.builder()
            .title("Account Restriction Gate")
            .candidateGroups("aml-compliance")
            .createdBy("casehub-engine")
            .payload(payload2)
            .callerRef(callerRef2)
            .build();
        workItemService.create(req2);

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: gates ordered by creation time (oldest first)
        assertNotNull(response);
        assertEquals(2, response.gates().size());

        GateDecisionResponse gate1 = response.gates().get(0);
        assertEquals("sar.filing", gate1.actionType());
        assertEquals("ALWAYS", gate1.gatePolicy());
        assertFalse(gate1.reversible());

        GateDecisionResponse gate2 = response.gates().get(1);
        assertEquals("account.restriction", gate2.actionType());
        assertEquals("RISK_SCORE_THRESHOLD", gate2.gatePolicy());
        assertTrue(gate2.reversible());
    }

    @Test
    void getGates_approvedGate_includesApprovalDetails() {
        // Given: investigation with approved gate
        UUID caseId = UUID.randomUUID();
        String payload = buildGatePayload("sar.filing", "SAR submission", false);
        String callerRef = "case:" + caseId + "/gate:1";

        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
            .title("SAR Filing Gate")
            .candidateGroups("aml-mlro")
            .createdBy("casehub-engine")
            .payload(payload)
            .callerRef(callerRef)
            .build();

        WorkItem workItem = workItemService.create(request);

        // Approve the gate
        workItemService.claim(workItem.id, "test-mlro");
        workItemService.start(workItem.id, "test-mlro");
        workItemService.complete(workItem.id, "test-mlro", "approved", "approve");

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: gate shows approval details
        assertNotNull(response);
        assertEquals(1, response.gates().size());

        GateDecisionResponse gate = response.gates().get(0);
        assertEquals("sar.filing", gate.actionType());
        assertEquals("COMPLETED", gate.status());
        assertEquals("test-mlro", gate.approvedBy());
        assertNotNull(gate.approvedAt());
    }

    @Test
    void getGates_rejectedGate_includesRejectionDetails() {
        // Given: investigation with rejected gate
        UUID caseId = UUID.randomUUID();
        String payload = buildGatePayload("entity.link.creation", "Entity link requires approval", true);
        String callerRef = "case:" + caseId + "/gate:1";

        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
            .title("Entity Link Gate")
            .candidateGroups("aml-compliance")
            .createdBy("casehub-engine")
            .payload(payload)
            .callerRef(callerRef)
            .build();

        WorkItem workItem = workItemService.create(request);

        // Reject the gate
        workItemService.claim(workItem.id, "test-compliance");
        workItemService.start(workItem.id, "test-compliance");
        workItemService.reject(workItem.id, "test-compliance", "Insufficient evidence", "reject");

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: gate shows rejection details
        assertNotNull(response);
        assertEquals(1, response.gates().size());

        GateDecisionResponse gate = response.gates().get(0);
        assertEquals("entity.link.creation", gate.actionType());
        assertEquals("REJECTED", gate.status());
        assertEquals("test-compliance", gate.approvedBy());
        assertNotNull(gate.approvedAt());
    }

    @Test
    void getGates_unknownActionType_returnsUnknownPolicy() {
        // Given: gate with unknown action type
        UUID caseId = UUID.randomUUID();
        String payload = buildGatePayload("unknown.action", "Unknown action type", false);
        String callerRef = "case:" + caseId + "/gate:1";

        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
            .title("Unknown Action Gate")
            .candidateGroups("aml-compliance")
            .createdBy("casehub-engine")
            .payload(payload)
            .callerRef(callerRef)
            .build();

        workItemService.create(request);

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: gate policy is UNKNOWN
        assertNotNull(response);
        assertEquals(1, response.gates().size());

        GateDecisionResponse gate = response.gates().get(0);
        assertEquals("unknown.action", gate.actionType());
        assertEquals("UNKNOWN", gate.gatePolicy());
    }

    @Test
    void getGates_invalidPayload_skipsGate() {
        // Given: gate with invalid JSON payload
        UUID caseId = UUID.randomUUID();
        String callerRef = "case:" + caseId + "/gate:1";

        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
            .title("Invalid Gate")
            .candidateGroups("aml-compliance")
            .createdBy("casehub-engine")
            .payload("not valid json")
            .callerRef(callerRef)
            .build();

        workItemService.create(request);

        // When: querying gates
        InvestigationGatesResponse response = gatesService.getGates(caseId);

        // Then: invalid gate is skipped (empty list)
        assertNotNull(response);
        assertTrue(response.gates().isEmpty());
    }

    /**
     * Builds gate payload JSON matching {@code ActionGateWorkItemHandler} format.
     */
    private String buildGatePayload(String actionType, String description, boolean reversible) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("actionType", actionType);
            root.put("description", description);
            root.put("reversible", reversible);
            root.set("context", MAPPER.createObjectNode()); // Empty context
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build gate payload", e);
        }
    }
}
