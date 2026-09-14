package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.InvestigationDetail;
import io.casehub.aml.api.mcp.StalledInvestigation;
import io.casehub.aml.compliance.InvestigationStallDetector;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.engine.AmlInvestigationFindingsService;
import io.casehub.aml.engine.AmlInvestigationGatesService;
import io.casehub.aml.engine.AmlInvestigationOutcomeService;
import io.casehub.aml.engine.AmlInvestigationRoutingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlInvestigationApiImplTest {

    @Mock AmlInvestigationOutcomeService outcomeService;
    @Mock AmlInvestigationFindingsService findingsService;
    @Mock AmlInvestigationGatesService gatesService;
    @Mock AmlInvestigationRoutingService routingService;
    @Mock InvestigationStallDetector stallDetector;
    @InjectMocks AmlInvestigationApiImpl impl;

    @Test
    void getInvestigation_returnsDetail() {
        UUID caseId = UUID.randomUUID();
        when(outcomeService.resolveInvestigation(caseId))
                .thenReturn(Optional.of(new InvestigationResolution(
                        InvestigationStatus.COMPLETED,
                        new InvestigationOutcome("sar-filed", null), null)));

        InvestigationDetail detail = impl.getInvestigation(caseId);

        assertNotNull(detail);
        assertEquals(caseId, detail.caseId());
        assertEquals("COMPLETED", detail.status());
        assertEquals("sar-filed", detail.outcome());
    }

    @Test
    void getInvestigation_returnsNullWhenNotFound() {
        UUID caseId = UUID.randomUUID();
        when(outcomeService.resolveInvestigation(caseId)).thenReturn(Optional.empty());

        InvestigationDetail detail = impl.getInvestigation(caseId);

        assertNull(detail);
    }

    @Test
    void listStalled_delegatesToDetector() {
        var stalled = new InvestigationStallDetector.StalledInvestigation(
                UUID.randomUUID(), "entity-resolution-agent", Duration.ofHours(5));
        when(stallDetector.detectStalled()).thenReturn(List.of(stalled));

        var result = impl.listStalled();

        assertEquals(1, result.size());
        assertEquals(stalled.caseId(), result.get(0).caseId());
        verify(stallDetector).detectStalled();
    }
}
