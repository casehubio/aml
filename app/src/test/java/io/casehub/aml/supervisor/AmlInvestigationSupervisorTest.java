package io.casehub.aml.supervisor;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.planning.plan.CasePlanModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmlInvestigationSupervisorTest {

    @Test
    void id_returns_aml_supervisor() {
        var supervisor = new AmlInvestigationSupervisor(
                mock(AmlSupervisorLlmAdapter.class), new AmlSupervisorPendingStore());
        assertThat(supervisor.id()).isEqualTo("aml-supervisor");
    }

    @Test
    void select_delegates_to_adapter_and_returns_validated_subset() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(true);
        var decision = new SupervisorDecision(
                List.of("pattern-analysis"), List.of("osint-screening"), "reason", false);
        when(adapter.consult(any(), any(), any())).thenReturn(decision);

        var store = new AmlSupervisorPendingStore();
        var supervisor = new AmlInvestigationSupervisor(adapter, store);

        Binding b1 = mockBinding("pattern-analysis");
        Binding b2 = mockBinding("osint-screening");
        var ctx = mockContext();

        List<Binding> result = supervisor.select(null, ctx, List.of(b1, b2));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("pattern-analysis");
    }

    @Test
    void select_passthrough_when_adapter_unavailable() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(false);
        var supervisor = new AmlInvestigationSupervisor(adapter, new AmlSupervisorPendingStore());

        Binding b1 = mockBinding("pattern-analysis");
        List<Binding> result = supervisor.select(null, mockContext(), List.of(b1));
        assertThat(result).containsExactly(b1);
        verify(adapter, never()).consult(any(), any(), any());
    }

    @Test
    void select_passthrough_on_empty_eligible() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(true);
        var supervisor = new AmlInvestigationSupervisor(adapter, new AmlSupervisorPendingStore());

        List<Binding> result = supervisor.select(null, mockContext(), List.of());
        assertThat(result).isEmpty();
        verify(adapter, never()).consult(any(), any(), any());
    }

    @Test
    void select_fallback_on_adapter_exception() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(true);
        when(adapter.consult(any(), any(), any()))
                .thenThrow(new InvalidSupervisorResponseException("fail"));

        var store = new AmlSupervisorPendingStore();
        var supervisor = new AmlInvestigationSupervisor(adapter, store);

        Binding b1 = mockBinding("a");
        Binding b2 = mockBinding("b");
        var ctx = mockContext();

        List<Binding> result = supervisor.select(null, ctx, List.of(b1, b2));
        assertThat(result).containsExactly(b1, b2);

        var pending = store.take(ctx.caseId());
        assertThat(pending).isNotNull();
        assertThat(pending.degraded()).isTrue();
        assertThat(pending.decision().rationale()).contains("degraded");
    }

    @Test
    void select_fallback_on_hallucinated_binding() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(true);
        var decision = new SupervisorDecision(
                List.of("nonexistent"), List.of(), "reason", false);
        when(adapter.consult(any(), any(), any())).thenReturn(decision);

        var store = new AmlSupervisorPendingStore();
        var supervisor = new AmlInvestigationSupervisor(adapter, store);

        Binding b1 = mockBinding("pattern-analysis");
        var ctx = mockContext();

        List<Binding> result = supervisor.select(null, ctx, List.of(b1));
        assertThat(result).containsExactly(b1);
        assertThat(store.take(ctx.caseId()).degraded()).isTrue();
    }

    @Test
    void select_stores_pending_decision_on_success() {
        var adapter = mock(AmlSupervisorLlmAdapter.class);
        when(adapter.isAvailable()).thenReturn(true);
        var decision = new SupervisorDecision(
                List.of("pattern-analysis"), List.of(), "reason", false);
        when(adapter.consult(any(), any(), any())).thenReturn(decision);

        var store = new AmlSupervisorPendingStore();
        var supervisor = new AmlInvestigationSupervisor(adapter, store);
        var ctx = mockContext();

        supervisor.select(null, ctx, List.of(mockBinding("pattern-analysis")));

        var pending = store.take(ctx.caseId());
        assertThat(pending).isNotNull();
        assertThat(pending.degraded()).isFalse();
        assertThat(pending.eligibleCount()).isEqualTo(1);
        assertThat(pending.decision().selectedBindings()).containsExactly("pattern-analysis");
    }

    private Binding mockBinding(String name) {
        Binding b = mock(Binding.class);
        when(b.getName()).thenReturn(name);
        return b;
    }

    private PlanExecutionContext mockContext() {
        return new PlanExecutionContext(
                UUID.randomUUID(), null, null,
                CaseStatus.RUNNING, "t1",
                List.of(), null, null);
    }
}
