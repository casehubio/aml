package io.casehub.aml.engine;

import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class AmlInvestigationCaseDescriptorTest {

    // null deps are safe: workers() builds lambda closures but does not invoke them
    private final AmlInvestigationCaseDescriptor descriptor =
            new AmlInvestigationCaseDescriptor(null, null);

    @Test
    void workers_returnsEightDistinctWorkers() {
        final List<Worker> workers = descriptor.workers();
        assertEquals(8, workers.size(), "Descriptor must declare exactly 8 workers");
        final Set<String> names = workers.stream().map(Worker::name).collect(Collectors.toSet());
        assertEquals(8, names.size(), "All worker names must be distinct");
        assertEquals(Set.of(
                "entity-resolution-agent",
                "pattern-analysis-agent",
                "osint-screening-agent",
                "osint-screening-agent-senior",
                "senior-analyst-agent",
                "sar-drafting-agent-junior",
                "sar-drafting-agent-senior",
                "compliance-review-opening-agent"), names);
    }

    @Test
    void each_worker_declares_exactly_one_capability() {
        for (final Worker w : descriptor.workers()) {
            assertEquals(1, w.capabilityNames().size(),
                    "Worker " + w.name() + " must declare exactly one capability");
        }
    }

    @Test
    void capability_names_match_expected_tags() {
        final var capByWorker = descriptor.workers().stream()
                .collect(Collectors.toMap(
                        Worker::name,
                        w -> w.capabilityNames().iterator().next()));

        assertEquals("entity-resolution",   capByWorker.get("entity-resolution-agent"));
        assertEquals("pattern-analysis",    capByWorker.get("pattern-analysis-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent-senior"));
        assertEquals("senior-analyst-review", capByWorker.get("senior-analyst-agent"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-junior"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-senior"));
        assertEquals("compliance-review-opening", capByWorker.get("compliance-review-opening-agent"));
    }

    @Test
    void each_worker_has_non_null_function() {
        for (final Worker w : descriptor.workers()) {
            assertNotNull(w.function(),
                    "Worker " + w.name() + " must have a non-null function");
        }
    }

    @Test
    void worker_execution_model_classification_is_exhaustive() {
        final Set<String> FLOW_WORKERS = Set.of(
                "entity-resolution-agent",
                "pattern-analysis-agent",
                "osint-screening-agent",
                "osint-screening-agent-senior",
                "senior-analyst-agent",
                "compliance-review-opening-agent");

        final Set<String> SYNC_WORKERS = Set.of(
                "sar-drafting-agent-junior",
                "sar-drafting-agent-senior");

        for (final Worker w : descriptor.workers()) {
            if (FLOW_WORKERS.contains(w.name())) {
                assertInstanceOf(FlowWorkerFunction.class, w.function(),
                        "Worker " + w.name() + " must use FlowWorkerFunction.");
            } else if (SYNC_WORKERS.contains(w.name())) {
                assertInstanceOf(WorkerFunction.Sync.class, w.function(),
                        "Worker " + w.name() + " must use WorkerFunction.Sync (PlannedAction support).");
            } else {
                fail("Worker " + w.name() + " is unclassified — add it to FLOW_WORKERS or SYNC_WORKERS.");
            }
        }
    }
}
