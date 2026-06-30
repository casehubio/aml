package io.casehub.aml.engine;

import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.Worker;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class AmlOversightCaseHubTest {

    @Inject
    AmlOversightCaseHub caseHub;

    @Test
    void definitionLoads() {
        final var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-aml", def.getNamespace());
        assertEquals("aml-oversight-investigation", def.getName());
    }

    @Test
    void hasThreeWorkers() {
        final var workers = caseHub.getDefinition().getWorkers();
        final var names = workers.stream().map(Worker::name).collect(Collectors.toSet());
        assertEquals(3, workers.size(), "Exactly 3 oversight workers expected");
        assertEquals(Set.of(
                "oversight-entity-resolution-agent",
                "oversight-entity-link-proposal-agent",
                "oversight-investigation-summary-agent"), names);
    }

    @Test
    void each_worker_declares_exactly_one_capability() {
        for (final Worker w : caseHub.getDefinition().getWorkers()) {
            assertEquals(1, w.capabilityNames().size(),
                    "Worker " + w.name() + " must declare exactly one capability");
        }
    }

    @Test
    void capability_names_match_expected_tags() {
        final var capByWorker = caseHub.getDefinition().getWorkers().stream()
                .collect(Collectors.toMap(
                        Worker::name,
                        w -> w.capabilityNames().iterator().next()));

        assertEquals("entity-resolution", capByWorker.get("oversight-entity-resolution-agent"));
        assertEquals("entity-link-proposal", capByWorker.get("oversight-entity-link-proposal-agent"));
        assertEquals("investigation-summary", capByWorker.get("oversight-investigation-summary-agent"));
    }

    @Test
    void worker_execution_model_classification() {
        final Set<String> flowWorkers = Set.of(
                "oversight-entity-resolution-agent",
                "oversight-investigation-summary-agent");
        final Set<String> syncWorkers = Set.of(
                "oversight-entity-link-proposal-agent");

        for (final Worker w : caseHub.getDefinition().getWorkers()) {
            if (flowWorkers.contains(w.name())) {
                assertInstanceOf(FlowWorkerFunction.class, w.function(),
                        "Worker " + w.name() + " must use FlowWorkerFunction (PP-20260531)");
            } else if (syncWorkers.contains(w.name())) {
                assertInstanceOf(WorkerFunction.Sync.class, w.function(),
                        "Worker " + w.name() + " must remain Sync until engine#564 ships");
            } else {
                fail("Worker " + w.name() + " is unclassified — add it to flowWorkers or syncWorkers.");
            }
        }
    }
}
