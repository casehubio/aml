package io.casehub.aml.engine;

import io.casehub.aml.api.model.FlowNode;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.casehub.aml.query.InvestigationSummaryView;
import io.quarkus.panache.common.Page;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AmlWorkerTaskResourceTest {

    @Test
    void findsMatchingTasksBeyondTheFirstHundredInvestigations() {
        var repository = mock(InvestigationSummaryRepository.class);
        var flow = mock(AmlInvestigationFlowService.class);
        var resource = new AmlWorkerTaskResource();
        resource.summaryRepository = repository;
        resource.flowService = flow;
        List<InvestigationSummaryView> summaries = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            summaries.add(new InvestigationSummaryView(UUID.randomUUID(), "tx-" + i, "origin", "dest",
                    BigDecimal.TEN, "USD", "STRUCTURING"));
        }
        UUID lastCaseId = summaries.getLast().caseId();
        when(repository.listByStatus(eq("IN_PROGRESS"), any(Page.class))).thenAnswer(call -> {
            Page page = call.getArgument(1);
            int start = Math.min(page.index * page.size, summaries.size());
            return summaries.subList(start, Math.min(start + page.size, summaries.size()));
        });
        when(flow.getInvestigationFlow(any(UUID.class))).thenAnswer(call ->
                new InvestigationFlowResponse(call.getArgument(0).equals(lastCaseId)
                        ? List.of(new FlowNode("sar-drafting", "worker", null, "scheduled", Instant.now()),
                                  new FlowNode("osint-screening", "worker", null, "scheduled", Instant.now()))
                        : List.of(), List.of(), List.of()));

        var tasks = resource.listWorkerTasks("sar-drafting");

        assertEquals(1, tasks.size());
        assertEquals(lastCaseId.toString(), tasks.getFirst().caseId());
        assertEquals("sar-drafting", tasks.getFirst().capabilityTag());
        verify(repository, times(2)).listByStatus(eq("IN_PROGRESS"), any(Page.class));
        verify(flow, times(101)).getInvestigationFlow(any(UUID.class));
    }

    @Test
    void emptyQueueStopsWithoutLoadingFlows() {
        var resource = new AmlWorkerTaskResource();
        resource.summaryRepository = mock(InvestigationSummaryRepository.class);
        resource.flowService = mock(AmlInvestigationFlowService.class);

        assertTrue(resource.listWorkerTasks(null).isEmpty());
        verify(resource.summaryRepository).listByStatus(eq("IN_PROGRESS"), any(Page.class));
        verifyNoInteractions(resource.flowService);
    }
}
