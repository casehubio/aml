package io.casehub.aml.cbr;

import io.casehub.aml.domain.CaseProfile;
import io.casehub.aml.domain.EntityType;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.JurisdictionRisk;
import io.casehub.aml.domain.NetworkComplexity;
import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.api.path.Path;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CbrSyntheticSeeder {

    private static final Logger LOG = Logger.getLogger(CbrSyntheticSeeder.class);

    private static final long SEED = 99L;

    private static final FlagReason[] FLAG_REASONS = FlagReason.values();
    private static final EntityType[] ENTITY_TYPES = EntityType.values();
    private static final JurisdictionRisk[] JURISDICTIONS = JurisdictionRisk.values();
    private static final NetworkComplexity[] NETWORKS = NetworkComplexity.values();

    private static final Map<FlagReason, int[]> AMOUNT_RANGES = Map.of(
            FlagReason.STRUCTURING, new int[]{5_000, 15_000},
            FlagReason.LAYERING, new int[]{20_000, 200_000},
            FlagReason.SMURFING, new int[]{3_000, 12_000},
            FlagReason.ROUND_TRIP, new int[]{50_000, 500_000},
            FlagReason.PEP_MATCH, new int[]{10_000, 500_000},
            FlagReason.HIGH_RISK_JURISDICTION, new int[]{25_000, 1_000_000},
            FlagReason.VELOCITY_ANOMALY, new int[]{10_000, 100_000},
            FlagReason.LARGE_VOLUME, new int[]{100_000, 5_000_000});

    private final CbrCaseMemoryStore cbrStore;

    public CbrSyntheticSeeder(CbrCaseMemoryStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    public SeedResult seed(int targetCount, String tenantId) {
        var random = new Random(SEED);
        var flagReasonCoverage = new LinkedHashMap<String, Integer>();
        var entityTypeCoverage = new LinkedHashMap<String, Integer>();
        var outcomeCoverage = new LinkedHashMap<String, Integer>();
        int seeded = 0;

        for (int i = 0; i < targetCount; i++) {
            var flagReason = FLAG_REASONS[i % FLAG_REASONS.length];
            var entityType = ENTITY_TYPES[i % ENTITY_TYPES.length];
            var jurisdiction = JURISDICTIONS[i % JURISDICTIONS.length];
            var network = pickNetwork(entityType, random);
            var amount = randomAmount(flagReason, random);
            int priorIncidents = random.nextInt(6);
            var outcome = pickOutcome(random);

            var profile = CaseProfile.complete(flagReason, amount, priorIncidents,
                    entityType, jurisdiction, network);
            var features = new LinkedHashMap<>(profile.toFeatures());

            boolean pepOrHighRisk = entityType == EntityType.PEP
                    || flagReason == FlagReason.HIGH_RISK_JURISDICTION;
            var traces = buildTraces(outcome, pepOrHighRisk);

            String problem = String.format("Flagged transaction TX-SYN-%04d: %s, amount %s USD",
                    i, flagReason.name(), amount.toPlainString());
            String solution = traces.stream()
                    .map(t -> t.bindingName() + "→" + t.workerName() + "(SUCCESS)")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(direct-verdict)");

            var cbrCase = new PlanCbrCase(problem, solution,
                    outcome.name(), null, features, traces, null, null);

            String entityId = UUID.nameUUIDFromBytes(
                    ("synthetic-cbr:" + i).getBytes(StandardCharsets.UTF_8)).toString();
            String sourceId = new UUID(random.nextLong(), random.nextLong()).toString();

            try {
                cbrStore.store(cbrCase, AmlCbrSchema.CASE_TYPE, entityId,
                        AmlMemoryDomains.CBR, tenantId, sourceId, Path.root());
                seeded++;
            } catch (Exception e) {
                LOG.warnf(e, "Failed to store synthetic case %d — skipping", i);
            }

            flagReasonCoverage.merge(flagReason.name(), 1, Integer::sum);
            entityTypeCoverage.merge(entityType.name(), 1, Integer::sum);
            outcomeCoverage.merge(outcome.name(), 1, Integer::sum);
        }

        LOG.infof("Synthetic CBR seeding complete: %d/%d cases stored", seeded, targetCount);
        return new SeedResult(seeded, flagReasonCoverage, entityTypeCoverage, outcomeCoverage);
    }

    private static TriageDecision pickOutcome(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.55) return TriageDecision.SAR_WARRANTED;
        if (roll < 0.85) return TriageDecision.FALSE_POSITIVE;
        return TriageDecision.INCONCLUSIVE;
    }

    private static NetworkComplexity pickNetwork(EntityType entityType, Random random) {
        if (entityType == EntityType.SHELL_COMPANY) {
            return random.nextDouble() < 0.7 ? NetworkComplexity.LARGE_NETWORK : NetworkComplexity.SMALL_NETWORK;
        }
        return NETWORKS[random.nextInt(NETWORKS.length)];
    }

    private static BigDecimal randomAmount(FlagReason flagReason, Random random) {
        int[] range = AMOUNT_RANGES.get(flagReason);
        int amount = range[0] + random.nextInt(range[1] - range[0]);
        return BigDecimal.valueOf(amount);
    }

    private static List<PlanTrace> buildTraces(TriageDecision outcome, boolean pepOrHighRisk) {
        var traces = new ArrayList<PlanTrace>();
        int idx = 0;
        traces.add(trace("entity-resolution", "entity-resolution-agent", idx++));
        if (pepOrHighRisk) {
            traces.add(trace("senior-analyst-review", "senior-analyst-review-agent", idx++));
        }
        traces.add(trace("pattern-analysis", "pattern-analysis-agent", idx++));
        traces.add(trace("osint-screening", "osint-screening-agent", idx++));
        traces.add(trace("investigation-triage", "investigation-triage-agent", idx++));
        if (outcome == TriageDecision.SAR_WARRANTED) {
            traces.add(trace("sar-drafting", "sar-drafting-agent-senior", idx++));
            traces.add(trace("compliance-review-opening", "compliance-review-opening-agent", idx));
        }
        return List.copyOf(traces);
    }

    private static PlanTrace trace(String bindingName, String workerName, int index) {
        return new PlanTrace(bindingName, bindingName, workerName, "SUCCESS", index, Map.of(), null);
    }
}
