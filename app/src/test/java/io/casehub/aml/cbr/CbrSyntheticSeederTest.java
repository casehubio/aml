package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbrSyntheticSeederTest {

    @Test
    void seed_producesCorrectCount() {
        var store = new CapturingStore();
        var seeder = new CbrSyntheticSeeder(store);
        var result = seeder.seed(50, "test-tenant");
        assertEquals(50, result.seeded());
        assertEquals(50, store.stored.size());
    }

    @Test
    void seed_coversAllFlagReasons() {
        var store = new CapturingStore();
        var result = new CbrSyntheticSeeder(store).seed(50, "t");
        assertEquals(8, result.flagReasonCoverage().size());
    }

    @Test
    void seed_coversAllEntityTypes() {
        var store = new CapturingStore();
        var result = new CbrSyntheticSeeder(store).seed(50, "t");
        assertEquals(4, result.entityTypeCoverage().size());
    }

    @Test
    void seed_coversAllOutcomes() {
        var store = new CapturingStore();
        var result = new CbrSyntheticSeeder(store).seed(50, "t");
        assertEquals(3, result.outcomeCoverage().size());
        assertTrue(result.outcomeCoverage().containsKey("SAR_WARRANTED"));
        assertTrue(result.outcomeCoverage().containsKey("FALSE_POSITIVE"));
        assertTrue(result.outcomeCoverage().containsKey("INCONCLUSIVE"));
    }

    @Test
    void seed_isDeterministic() {
        var store1 = new CapturingStore();
        var store2 = new CapturingStore();
        new CbrSyntheticSeeder(store1).seed(20, "t");
        new CbrSyntheticSeeder(store2).seed(20, "t");
        for (int i = 0; i < 20; i++) {
            assertEquals(store1.stored.get(i).outcome(), store2.stored.get(i).outcome());
        }
    }

    @Test
    void seed_sarCasesHaveFullSolutionTrace() {
        var store = new CapturingStore();
        new CbrSyntheticSeeder(store).seed(50, "t");
        var sarCase = store.stored.stream()
                .filter(c -> "SAR_WARRANTED".equals(c.outcome()))
                .findFirst().orElseThrow();
        long steps = sarCase.solution().chars().filter(ch -> ch == '→').count();
        assertTrue(steps >= 6,
                "SAR path solution should have at least 6 steps, got " + steps);
    }

    @Test
    void seed_clearedCasesHaveShorterSolutionTrace() {
        var store = new CapturingStore();
        new CbrSyntheticSeeder(store).seed(50, "t");
        var fpCase = store.stored.stream()
                .filter(c -> "FALSE_POSITIVE".equals(c.outcome()))
                .findFirst().orElseThrow();
        long steps = fpCase.solution().chars().filter(ch -> ch == '→').count();
        assertEquals(4, steps,
                "Cleared path solution should have 4 steps");
    }

    @Test
    void seed_featuresContainExpectedKeys() {
        var store = new CapturingStore();
        new CbrSyntheticSeeder(store).seed(10, "t");
        var first = store.stored.get(0);
        assertTrue(first.features().containsKey("flag_reason"));
        assertTrue(first.features().containsKey("transaction_amount"));
        assertTrue(first.features().containsKey("prior_incident_count"));
        assertTrue(first.features().containsKey("entity_type"));
        assertTrue(first.features().containsKey("jurisdiction_risk"));
        assertTrue(first.features().containsKey("network_complexity"));
    }

    private static class CapturingStore implements CbrCaseMemoryStore {
        final List<FeatureVectorCbrCase> stored = new ArrayList<>();

        @Override
        public String store(CbrCase cbrCase, String caseType, String entityId,
                            MemoryDomain domain, String tenantId, String sourceId, Path scope) {
            if (cbrCase instanceof FeatureVectorCbrCase p) {
                stored.add(p);
            }
            return UUID.randomUUID().toString();
        }

        @Override
        public <T extends CbrCase> List<ScoredCbrCase<T>> retrieveSimilar(CbrQuery query, Class<T> caseClass) {
            return List.of();
        }

        @Override
        public void registerSchema(CbrFeatureSchema schema)                                                                       {}

        @Override
        public Integer erase(io.casehub.neocortex.memory.EraseRequest request)                                                {return 0;}

        @Override
        public Integer eraseEntity(String entityId, String tenantId)                                                              {return 0;}

        @Override
        public Integer eraseByScope(Path scope, String tenantId)                                                                  {return 0;}

        @Override
        public void recordOutcome(String caseId, String tenantId, io.casehub.neocortex.memory.cbr.CbrOutcome outcome)             {}

        @Override
        public Integer purge(CbrRetentionPolicy policy)                                                                           {return 0;}

        @Override
        public boolean supersede(String caseId, String tenantId, String reason, String supersededBy)                              {return true;}

        @Override
        public boolean reinstate(String caseId, String tenantId)                                                                  {return true;}

        @Override
        public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId)           {return null;}

        @Override
        public List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) {return List.of();}

        @Override
        public List<String> findCaseIds(String tenantId, MemoryDomain domain, String caseType, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> filters) {return List.of();}

        @Override
        public int supersedeMatching(String tenantId, MemoryDomain domain, String caseType, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> filters, String reason) {return 0;}

        @Override
        public int supersedeAll(java.util.Collection<String> caseIds, String tenantId, String reason) {return 0;}

        @Override
        public int reinstateMatching(String tenantId, MemoryDomain domain, String caseType, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> filters) {return 0;}

        @Override
        public int reinstateAll(java.util.Collection<String> caseIds, String tenantId) {return 0;}
    }
}
