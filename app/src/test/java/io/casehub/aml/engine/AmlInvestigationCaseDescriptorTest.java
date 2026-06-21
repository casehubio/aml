package io.casehub.aml.engine;

import io.casehub.api.model.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmlInvestigationCaseDescriptorTest {

    // null deps are safe: workers() builds lambda closures but does not invoke them
    private final AmlInvestigationCaseDescriptor descriptor =
            new AmlInvestigationCaseDescriptor(null, null);

    @Test
    void workers_returnsSevenDistinctWorkers() {
        final List<Worker> workers = descriptor.workers();
        assertEquals(7, workers.size(), "Descriptor must declare exactly 7 workers");
        final Set<String> names = workers.stream().map(Worker::getName).collect(Collectors.toSet());
        assertEquals(7, names.size(), "All worker names must be distinct");
        assertEquals(Set.of(
                "entity-resolution-agent",
                "pattern-analysis-agent",
                "osint-screening-agent",
                "osint-screening-agent-senior",
                "senior-analyst-agent",
                "sar-drafting-agent-junior",
                "sar-drafting-agent-senior"), names);
    }

    @Test
    void each_worker_declares_exactly_one_capability() {
        for (final Worker w : descriptor.workers()) {
            assertEquals(1, w.getCapabilities().size(),
                    "Worker " + w.getName() + " must declare exactly one capability");
        }
    }

    @Test
    void capability_names_match_expected_tags() {
        final var capByWorker = descriptor.workers().stream()
                .collect(Collectors.toMap(
                        Worker::getName,
                        w -> w.getCapabilities().get(0).getName()));

        assertEquals("entity-resolution",   capByWorker.get("entity-resolution-agent"));
        assertEquals("pattern-analysis",    capByWorker.get("pattern-analysis-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent-senior"));
        assertEquals("senior-analyst-review", capByWorker.get("senior-analyst-agent"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-junior"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-senior"));
    }

    @Test
    void each_worker_has_non_null_function() {
        for (final Worker w : descriptor.workers()) {
            assertNotNull(w.getFunction(),
                    "Worker " + w.getName() + " must have a non-null function");
        }
    }
}
