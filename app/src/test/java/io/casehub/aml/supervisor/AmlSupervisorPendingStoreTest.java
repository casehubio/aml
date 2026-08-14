package io.casehub.aml.supervisor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmlSupervisorPendingStoreTest {

    private final AmlSupervisorPendingStore store = new AmlSupervisorPendingStore();

    @Test
    void put_and_take_returns_decision() {
        UUID caseId = UUID.randomUUID();
        var decision = new SupervisorDecision(
                List.of("pattern-analysis"), List.of(), "reason", false);
        var pending = new PendingSupervisorDecision(decision, 2, false, caseId, "t1");
        store.put(pending);
        assertThat(store.take(caseId)).isEqualTo(pending);
    }

    @Test
    void take_removes_entry() {
        UUID caseId = UUID.randomUUID();
        var decision = new SupervisorDecision(
                List.of("a"), List.of(), "r", false);
        store.put(new PendingSupervisorDecision(decision, 1, false, caseId, "t1"));
        store.take(caseId);
        assertThat(store.take(caseId)).isNull();
    }

    @Test
    void take_unknown_returns_null() {
        assertThat(store.take(UUID.randomUUID())).isNull();
    }

    @Test
    void put_overwrites_previous() {
        UUID caseId = UUID.randomUUID();
        var d1 = new PendingSupervisorDecision(
                new SupervisorDecision(List.of("a"), List.of(), "first", false),
                1, false, caseId, "t1");
        var d2 = new PendingSupervisorDecision(
                new SupervisorDecision(List.of("b"), List.of(), "second", false),
                2, false, caseId, "t1");
        store.put(d1);
        store.put(d2);
        assertThat(store.take(caseId).decision().rationale()).isEqualTo("second");
    }
}
