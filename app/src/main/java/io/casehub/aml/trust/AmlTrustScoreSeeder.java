package io.casehub.aml.trust;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * Seeds initial Bayesian Beta trust scores for AML workers at application startup.
 *
 * <p>Uses {@code @Observes @Priority(20) StartupEvent} rather than {@code @Startup @PostConstruct}
 * to ensure seeding runs AFTER {@link io.casehub.engine.internal.engine.DefaultCaseDefinitionRegistry}
 * registers case definitions (priority=10). This avoids any Vert.x/CDI initialization ordering
 * conflict that can prevent case definitions from being found when the first investigation starts.
 */
@ApplicationScoped
public class AmlTrustScoreSeeder {

    private record WorkerSeed(String workerId, String capabilityTag, int alpha, int beta) {}

    private static final List<WorkerSeed> SEEDS = List.of(
            new WorkerSeed("sar-drafting-agent-senior", "sar-drafting", 9, 1),
            new WorkerSeed("osint-screening-agent-senior", "osint-screening", 9, 2),
            new WorkerSeed("osint-screening-agent", "osint-screening", 3, 7),
            new WorkerSeed("entity-resolution-agent", "entity-resolution", 8, 2),
            new WorkerSeed("pattern-analysis-agent", "pattern-analysis", 8, 2),
            // alpha=10/beta=1: score≈0.909 > accept_zone(0.80+0.10=0.90) — avoids borderline escalation.
            // Previous (8,2) → score=0.80 = threshold, within borderline margin → always escalated.
            new WorkerSeed("senior-analyst-agent", "senior-analyst-review", 10, 1),
            new WorkerSeed("compliance-review-opening-agent", "compliance-review-opening", 9, 1)
                                                         );

    private final ActorTrustScoreRepository trustRepo;

    @Inject
    public AmlTrustScoreSeeder(final ActorTrustScoreRepository trustRepo) {
        this.trustRepo = trustRepo;
    }

    @Transactional
    void onStart(@Observes @Priority(20) StartupEvent ev) {
        for (final WorkerSeed ws : SEEDS) {
            if (trustRepo.findCapabilityScore(ws.workerId(), ws.capabilityTag()).isEmpty()) {
                final int obs = ws.alpha() + ws.beta();
                final double trustScore = (double) ws.alpha() / obs;
                trustRepo.upsert(
                        ws.workerId(),
                        ScoreType.CAPABILITY,
                        ws.capabilityTag(),
                        null,
                        ActorType.SYSTEM,
                        trustScore,
                        obs,
                        0,
                        ws.alpha(),
                        ws.beta(),
                        ws.alpha(),
                        ws.beta(),
                        Instant.now());
            }
        }
    }
}
