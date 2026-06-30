package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.SpecialistFindingResponse;
import io.casehub.api.engine.CaseHubRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests specialist findings assembly from CaseHub context.
 */
class AmlInvestigationFindingsServiceTest {

    @Mock
    private CaseHubRuntime caseHubRuntime;

    private AmlInvestigationFindingsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AmlInvestigationFindingsService();
        service.caseHubRuntime = caseHubRuntime;
    }

    @Test
    void getFindings_allSpecialistsCompleted_returnsAllResults() {
        // Given: case with all specialist findings in context
        UUID caseId = UUID.randomUUID();

        Map<String, Object> entityResolution = Map.of(
                "entityId", "entity-123",
                "ownershipChain", "Direct → Corporate Entity",
                "entityType", "CORPORATE",
                "riskScore", 0.35
        );

        Map<String, Object> patternAnalysis = Map.of(
                "structuringDetected", false,
                "description", "No structuring pattern detected"
        );

        Map<String, Object> osintScreening = Map.of(
                "declined", false,
                "pepHit", false,
                "sanctionsHit", false,
                "screeningLevel", "ENHANCED"
        );

        Map<String, Object> sarDraft = Map.of(
                "sarNarrative", "SAR narrative for transaction tx-123. Entity type: CORPORATE..."
        );

        when(caseHubRuntime.query(caseId, "entityResolution"))
                .thenReturn(CompletableFuture.completedFuture(entityResolution));
        when(caseHubRuntime.query(caseId, "patternAnalysis"))
                .thenReturn(CompletableFuture.completedFuture(patternAnalysis));
        when(caseHubRuntime.query(caseId, "osintScreening"))
                .thenReturn(CompletableFuture.completedFuture(osintScreening));
        when(caseHubRuntime.query(caseId, "sarNarrative"))
                .thenReturn(CompletableFuture.completedFuture(sarDraft));

        // When: fetching findings
        InvestigationFindingsResponse response = service.getFindings(caseId)
                .toCompletableFuture().join();

        // Then: all specialists show completed with results
        assertNotNull(response);

        SpecialistFindingResponse entityFinding = response.entityResolution();
        assertEquals("COMPLETED", entityFinding.status());
        assertNotNull(entityFinding.result());
        assertEquals("entity-123", ((Map<?, ?>) entityFinding.result()).get("entityId"));

        SpecialistFindingResponse patternFinding = response.patternAnalysis();
        assertEquals("COMPLETED", patternFinding.status());
        assertNotNull(patternFinding.result());
        assertEquals(false, ((Map<?, ?>) patternFinding.result()).get("structuringDetected"));

        SpecialistFindingResponse osintFinding = response.osintScreening();
        assertEquals("COMPLETED", osintFinding.status());
        assertNotNull(osintFinding.result());
        assertEquals(false, ((Map<?, ?>) osintFinding.result()).get("declined"));

        SpecialistFindingResponse sarFinding = response.sarNarrative();
        assertEquals("COMPLETED", sarFinding.status());
        assertNotNull(sarFinding.result());
        assertTrue(((Map<?, ?>) sarFinding.result()).get("sarNarrative").toString().contains("SAR narrative"));
    }

    @Test
    void getFindings_specialistPending_returnsPendingStatus() {
        // Given: case where osint hasn't executed yet
        UUID caseId = UUID.randomUUID();

        Map<String, Object> entityResolution = Map.of(
                "entityId", "entity-123",
                "ownershipChain", "Direct → Corporate Entity",
                "entityType", "CORPORATE",
                "riskScore", 0.35
        );

        when(caseHubRuntime.query(caseId, "entityResolution"))
                .thenReturn(CompletableFuture.completedFuture(entityResolution));
        when(caseHubRuntime.query(caseId, "patternAnalysis"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(caseHubRuntime.query(caseId, "osintScreening"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(caseHubRuntime.query(caseId, "sarNarrative"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When: fetching findings
        InvestigationFindingsResponse response = service.getFindings(caseId)
                .toCompletableFuture().join();

        // Then: completed specialist shows result, others show pending
        assertEquals("COMPLETED", response.entityResolution().status());
        assertEquals("PENDING", response.patternAnalysis().status());
        assertEquals("PENDING", response.osintScreening().status());
        assertEquals("PENDING", response.sarNarrative().status());
    }

    @Test
    void getFindings_specialistDeclined_returnsDeclinedStatus() {
        // Given: case where osint declined
        UUID caseId = UUID.randomUUID();

        Map<String, Object> entityResolution = Map.of(
                "entityId", "entity-123",
                "ownershipChain", "Direct → Corporate Entity",
                "entityType", "CORPORATE",
                "riskScore", 0.35
        );

        Map<String, Object> osintScreening = Map.of(
                "declined", true,
                "reason", "insufficient clearance for PEP database access",
                "pepHit", false,
                "sanctionsHit", false
        );

        when(caseHubRuntime.query(caseId, "entityResolution"))
                .thenReturn(CompletableFuture.completedFuture(entityResolution));
        when(caseHubRuntime.query(caseId, "patternAnalysis"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(caseHubRuntime.query(caseId, "osintScreening"))
                .thenReturn(CompletableFuture.completedFuture(osintScreening));
        when(caseHubRuntime.query(caseId, "sarNarrative"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When: fetching findings
        InvestigationFindingsResponse response = service.getFindings(caseId)
                .toCompletableFuture().join();

        // Then: declined specialist shows result with declined=true
        SpecialistFindingResponse osintFinding = response.osintScreening();
        assertEquals("COMPLETED", osintFinding.status()); // Still COMPLETED, but result contains declined=true
        Map<?, ?> result = (Map<?, ?>) osintFinding.result();
        assertEquals(true, result.get("declined"));
        assertEquals("insufficient clearance for PEP database access", result.get("reason"));
    }
}
