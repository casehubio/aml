package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
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
    void seed_sarCasesHaveFullPlanTrace() {
        var store = new CapturingStore();
        new CbrSyntheticSeeder(store).seed(50, "t");
        var sarCase = store.stored.stream()
                .filter(c -> "SAR_WARRANTED".equals(c.outcome()))
                .findFirst().orElseThrow();
        assertTrue(sarCase.planTrace().size() >= 6,
                "SAR path should have at least 6 trace steps, got " + sarCase.planTrace().size());
    }

    @Test
    void seed_clearedCasesHaveShorterTrace() {
        var store = new CapturingStore();
        new CbrSyntheticSeeder(store).seed(50, "t");
        var fpCase = store.stored.stream()
                .filter(c -> "FALSE_POSITIVE".equals(c.outcome()))
                .findFirst().orElseThrow();
        assertEquals(4, fpCase.planTrace().size(),
                "Cleared path should have 4 trace steps");
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
        final List<PlanCbrCase> stored = new ArrayList<>();

        @Override
        public String store(CbrCase cbrCase, String caseType, String entityId,
                            MemoryDomain domain, String tenantId, String sourceId, Path scope) {
            if (cbrCase instanceof PlanCbrCase p) {
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
        public void supersede(String caseId, String tenantId, String reason, String supersededBy)                                 {}

        @Override
        public void reinstate(String caseId, String tenantId)                                                                     {}

        @Override
        public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId)           {return null;}

        @Override
        public List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) {return List.of();}
    }
}
