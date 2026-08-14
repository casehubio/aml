package io.casehub.aml.supervisor;

import io.casehub.aml.ledger.AmlSupervisorDecisionLedgerEntry;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AmlSupervisorAuditObserverTest {

    @Test
    void writes_ledger_entry_from_pending_store() {
        var store = new AmlSupervisorPendingStore();
        var repo = mock(LedgerEntryRepository.class);
        var observer = new AmlSupervisorAuditObserver(store, repo);

        UUID caseId = UUID.randomUUID();
        var decision = new SupervisorDecision(
                List.of("pattern-analysis"), List.of("osint-screening"), "reason", false);
        store.put(new PendingSupervisorDecision(decision, 2, false, caseId, "t1"));

        observer.onWorkerDecision(new WorkerDecisionEvent(
                caseId, "t1", "pattern-analysis-agent", "pattern-analysis", null));

        verify(repo).save(any(AmlSupervisorDecisionLedgerEntry.class), eq("t1"));
    }

    @Test
    void ledger_entry_has_correct_fields() {
        var store = new AmlSupervisorPendingStore();
        var repo = mock(LedgerEntryRepository.class);
        var observer = new AmlSupervisorAuditObserver(store, repo);

        UUID caseId = UUID.randomUUID();
        var decision = new SupervisorDecision(
                List.of("pattern-analysis", "osint-screening"), List.of("sar-drafting"),
                "parallel first", true);
        store.put(new PendingSupervisorDecision(decision, 3, false, caseId, "t1"));

        observer.onWorkerDecision(new WorkerDecisionEvent(
                caseId, "t1", "worker", "cap", null));

        var captor = ArgumentCaptor.forClass(AmlSupervisorDecisionLedgerEntry.class);
        verify(repo).save(captor.capture(), eq("t1"));
        var entry = captor.getValue();
        assertThat(entry.selectedBindings).isEqualTo("pattern-analysis,osint-screening");
        assertThat(entry.suppressedBindings).isEqualTo("sar-drafting");
        assertThat(entry.rationale).isEqualTo("parallel first");
        assertThat(entry.earlyTermination).isTrue();
        assertThat(entry.eligibleCount).isEqualTo(3);
        assertThat(entry.degraded).isFalse();
    }

    @Test
    void no_op_when_no_pending_decision() {
        var store = new AmlSupervisorPendingStore();
        var repo = mock(LedgerEntryRepository.class);
        var observer = new AmlSupervisorAuditObserver(store, repo);

        observer.onWorkerDecision(new WorkerDecisionEvent(
                UUID.randomUUID(), "t1", "worker", "cap", null));

        verify(repo, never()).save(any(), any());
    }

    @Test
    void degraded_decision_writes_degraded_entry() {
        var store = new AmlSupervisorPendingStore();
        var repo = mock(LedgerEntryRepository.class);
        var observer = new AmlSupervisorAuditObserver(store, repo);

        UUID caseId = UUID.randomUUID();
        var decision = new SupervisorDecision(
                List.of("a", "b"), List.of(), "LLM unavailable — degraded to choreography", false);
        store.put(new PendingSupervisorDecision(decision, 2, true, caseId, "t1"));

        observer.onWorkerDecision(new WorkerDecisionEvent(
                caseId, "t1", "worker", "a", null));

        var captor = ArgumentCaptor.forClass(AmlSupervisorDecisionLedgerEntry.class);
        verify(repo).save(captor.capture(), eq("t1"));
        assertThat(captor.getValue().degraded).isTrue();
    }
}
