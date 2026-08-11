package io.casehub.aml.compliance;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.engine.internal.context.CaseContextImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationStallDetectorTest {

    @Test
    void waiting_case_past_threshold_is_stalled() {
        var cache = new TestCache();
        var detector = new InvestigationStallDetector(cache);

        CaseInstance stalled = instance(CaseStatus.WAITING,
                Instant.now().minus(5, ChronoUnit.HOURS).toString());
        cache.add(stalled);

        List<InvestigationStallDetector.StalledInvestigation> result = detector.detectStalled();
        assertEquals(1, result.size());
        assertEquals(stalled.getUuid(), result.get(0).caseId());
    }

    @Test
    void waiting_case_within_threshold_is_not_stalled() {
        var cache = new TestCache();
        var detector = new InvestigationStallDetector(cache);

        CaseInstance fresh = instance(CaseStatus.WAITING,
                Instant.now().minus(1, ChronoUnit.HOURS).toString());
        cache.add(fresh);

        assertTrue(detector.detectStalled().isEmpty());
    }

    @Test
    void completed_case_is_never_stalled() {
        var cache = new TestCache();
        var detector = new InvestigationStallDetector(cache);

        CaseInstance done = instance(CaseStatus.COMPLETED,
                Instant.now().minus(10, ChronoUnit.HOURS).toString());
        cache.add(done);

        assertTrue(detector.detectStalled().isEmpty());
    }

    @Test
    void no_dispatch_timestamp_is_not_stalled() {
        var cache = new TestCache();
        var detector = new InvestigationStallDetector(cache);

        CaseInstance noTs = instance(CaseStatus.WAITING, null);
        cache.add(noTs);

        assertTrue(detector.detectStalled().isEmpty());
    }

    private CaseInstance instance(CaseStatus state, String lastDispatch) {
        var ci = new CaseInstance();
        ci.setUuid(UUID.randomUUID());
        ci.setState(state);
        Map<String, Object> ctx = lastDispatch != null
                ? Map.of("_lastWorkerDispatchedAt", lastDispatch)
                : Map.of();
        ci.setCaseContext(new CaseContextImpl(ctx));
        return ci;
    }

    private static class TestCache implements CaseInstanceCache {
        private final java.util.List<CaseInstance> instances = new java.util.ArrayList<>();
        void add(CaseInstance i) { instances.add(i); }
        @Override public void put(CaseInstance instance) { instances.add(instance); }
        @Override public CaseInstance get(UUID id) {
            return instances.stream().filter(i -> id.equals(i.getUuid())).findFirst().orElse(null);
        }
        @Override public void clear() { instances.clear(); }
        @Override public List<CaseInstance> getAll() { return List.copyOf(instances); }
    }
}
