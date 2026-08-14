package io.casehub.aml.supervisor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupervisorDecisionTest {

    @Test
    void valid_construction() {
        var decision = new SupervisorDecision(
                List.of("pattern-analysis"), List.of("osint-screening"),
                "pattern first", false);
        assertThat(decision.selectedBindings()).containsExactly("pattern-analysis");
        assertThat(decision.suppressedBindings()).containsExactly("osint-screening");
        assertThat(decision.rationale()).isEqualTo("pattern first");
        assertThat(decision.earlyTermination()).isFalse();
    }

    @Test
    void empty_selected_throws() {
        assertThatThrownBy(() -> new SupervisorDecision(
                List.of(), List.of("osint-screening"), "no bindings", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_selected_throws() {
        assertThatThrownBy(() -> new SupervisorDecision(
                null, List.of(), "reason", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_suppressed_throws() {
        assertThatThrownBy(() -> new SupervisorDecision(
                List.of("a"), null, "reason", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_rationale_throws() {
        assertThatThrownBy(() -> new SupervisorDecision(
                List.of("a"), List.of(), null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void early_termination_with_selected_bindings_succeeds() {
        var decision = new SupervisorDecision(
                List.of("investigation-triage"), List.of("pattern-analysis"),
                "evidence sufficient", true);
        assertThat(decision.earlyTermination()).isTrue();
        assertThat(decision.selectedBindings()).containsExactly("investigation-triage");
    }

    @Test
    void lists_are_defensive_copies() {
        var selected = new java.util.ArrayList<>(List.of("a"));
        var suppressed = new java.util.ArrayList<>(List.of("b"));
        var decision = new SupervisorDecision(selected, suppressed, "r", false);
        selected.add("c");
        suppressed.add("d");
        assertThat(decision.selectedBindings()).containsExactly("a");
        assertThat(decision.suppressedBindings()).containsExactly("b");
    }
}
