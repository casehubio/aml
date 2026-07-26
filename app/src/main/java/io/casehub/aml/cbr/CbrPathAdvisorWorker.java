package io.casehub.aml.cbr;

import io.casehub.aml.ledger.AmlCbrAdvisoryLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.worker.api.Worker;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CbrPathAdvisorWorker {

    private static final Logger LOG = Logger.getLogger(CbrPathAdvisorWorker.class);

    private CbrPathAdvisorWorker() {}

    @SuppressWarnings("unchecked")
    public static Worker create(LedgerEntryRepository ledgerRepository, CurrentPrincipal principal,
                                SarNarrativeSeeder seeder) {
        return Worker.builder()
                     .name("cbr-path-advisor-agent")
                     .capabilityName("cbr-path-advisor")
                     .fn((Map<String, Object>) null)
                     .apply((input, scope) -> {
                         try {
                             return io.casehub.worker.api.WorkerResult.of(
                                     doAdvise(input, ledgerRepository, principal, scope.caseId(), seeder));
                         } catch (Exception e) {
                             LOG.warnf(e, "CBR path advisor failed — returning fallback");
                             return io.casehub.worker.api.WorkerResult.of(Map.of(
                                     "caseCount", 0,
                                     "error", true,
                                     "errorReason", "advisor failed — proceeding without CBR advice",
                                     "similarSarNarratives", List.of()));
                         }
                     })
                     .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> doAdvise(Map<String, Object> input,
                                                LedgerEntryRepository ledgerRepository,
                                                CurrentPrincipal principal,
                                                UUID caseId,
                                                SarNarrativeSeeder seeder) {
        final var experiences = (List<Map<String, Object>>) input.get("cbrExperiences");
        if (experiences == null || experiences.isEmpty()) {
            return Map.of("caseCount", 0, "similarSarNarratives", List.of());
        }

        final var capabilityStats = new LinkedHashMap<String, CapabilityStats>();
        final var outcomeCounts   = new HashMap<String, Integer>();
        double    totalScore      = 0;
        double    minScore        = Double.MAX_VALUE;
        int       count           = 0;

        for (var experience : experiences) {
            final double score = experience.get("similarityScore") instanceof Number n
                                 ? n.doubleValue() : 0.0;
            final String outcome   = (String) experience.get("outcome");
            final var    planTrace = (List<Map<String, Object>>) experience.get("planTrace");

            totalScore += score;
            if (score < minScore) {minScore = score;}
            count++;

            if (outcome != null) {
                outcomeCounts.merge(outcome, 1, Integer::sum);
            }

            if (planTrace != null) {
                for (var step : planTrace) {
                    final String capabilityName = (String) step.get("capabilityName");
                    final String stepOutcome    = (String) step.get("stepOutcome");
                    if (capabilityName != null) {
                        capabilityStats.computeIfAbsent(capabilityName, k -> new CapabilityStats())
                                       .record(stepOutcome);
                    }
                }
            }
        }

        final double avgSimilarity = count > 0 ? totalScore / count : 0;
        final double confidence    = avgSimilarity * Math.min(1.0, (double) count / 5.0);

        final var capabilities = new LinkedHashMap<String, Object>();
        for (var entry : capabilityStats.entrySet()) {
            final var stats = entry.getValue();
            capabilities.put(entry.getKey(), Map.of(
                    "frequency", (double) stats.count / count,
                    "outcomes", Map.copyOf(stats.outcomes)));
        }

        String predominantOutcome = null;
        double predominantFreq    = 0;
        for (var entry : outcomeCounts.entrySet()) {
            final double freq = (double) entry.getValue() / count;
            if (freq > predominantFreq) {
                predominantFreq    = freq;
                predominantOutcome = entry.getKey();
            }
        }

        final var result = new LinkedHashMap<String, Object>();
        result.put("caseCount", count);
        result.put("minSimilarity", count > 0 ? minScore : 0.0);
        result.put("avgSimilarity", avgSimilarity);
        result.put("capabilities", capabilities);
        if (predominantOutcome != null) {
            result.put("predominantOutcome", predominantOutcome);
            result.put("predominantOutcomeFrequency", predominantFreq);
        }
        result.put("confidence", confidence);

        List<?> narratives;
        try {
            narratives = seeder.extract(experiences);
        } catch (Exception e) {
            LOG.warnf(e, "Narrative seeding failed — proceeding without seeds");
            narratives = List.of();
        }
        result.put("similarSarNarratives", narratives);

        writeLedgerEntry(result, ledgerRepository, principal, caseId, capabilityStats, count);

        return result;
    }

    private static void writeLedgerEntry(Map<String, Object> advice,
                                         LedgerEntryRepository ledgerRepository,
                                         CurrentPrincipal principal,
                                         UUID caseId,
                                         Map<String, CapabilityStats> capabilityStats,
                                         int caseCount) {
        try {
            final String tenantId = principal.tenancyId() != null
                                    ? principal.tenancyId()
                                    : TenancyConstants.DEFAULT_TENANT_ID;

            final var entry = new AmlCbrAdvisoryLedgerEntry();
            entry.caseCount                   = (int) advice.get("caseCount");
            entry.avgSimilarity               = (double) advice.get("avgSimilarity");
            entry.confidence                  = (double) advice.get("confidence");
            entry.predominantOutcome          = (String) advice.get("predominantOutcome");
            entry.predominantOutcomeFrequency = advice.get("predominantOutcomeFrequency") instanceof Number n
                                                ? n.doubleValue() : null;
            entry.recommendedCapabilities     = capabilityStats.entrySet().stream()
                                                               .filter(e -> (double) e.getValue().count / caseCount > 0.5)
                                                               .map(Map.Entry::getKey)
                                                               .collect(Collectors.joining(","));
            entry.subjectId                   = UUID.nameUUIDFromBytes(
                    ("aml-cbr-advisory:" + caseId).getBytes(StandardCharsets.UTF_8));
            entry.actorId                     = "aml-cbr-advisor";
            entry.tenancyId                   = tenantId;
            entry.entryType                   = LedgerEntryType.ATTESTATION;

            QuarkusTransaction.requiringNew().run(() -> ledgerRepository.save(entry, tenantId));
            LOG.infof("CBR advisory ledger entry written: caseId=%s caseCount=%d confidence=%.2f",
                      caseId, entry.caseCount, entry.confidence);
        } catch (Exception e) {
            LOG.warnf(e, "Advisory ledger entry write failed — non-fatal");
        }
    }

    private static final class CapabilityStats {
        int count;
        final Map<String, Integer> outcomes = new HashMap<>();

        void record(String outcome) {
            count++;
            if (outcome != null) {
                outcomes.merge(outcome, 1, Integer::sum);
            }
        }
    }
}
