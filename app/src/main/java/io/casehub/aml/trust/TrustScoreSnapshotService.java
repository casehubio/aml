package io.casehub.aml.trust;

import io.casehub.aml.domain.TrustScoreSnapshot;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures periodic trust score snapshots for historical trend analysis.
 *
 * <p>Reads current Bayesian Beta scores from {@link ActorTrustScoreRepository}
 * for all known AML agents and persists them as {@link TrustScoreSnapshot}
 * entities on the default datasource. Snapshots are captured hourly by default.
 *
 * <p>The history query method provides ordered snapshots for the workbench UI
 * trend display.
 */
@ApplicationScoped
public class TrustScoreSnapshotService {

    private static final Logger LOG = Logger.getLogger(TrustScoreSnapshotService.class);

    /**
     * Agent/capability pairs from trust-routing.yaml — same set as
     * {@code AmlMetricsService.KNOWN_AGENTS}.
     */
    private static final List<AgentCapability> KNOWN_AGENTS = List.of(
        new AgentCapability("sar-drafting-agent-senior", "sar-drafting"),
        new AgentCapability("osint-screening-agent-senior", "osint-screening"),
        new AgentCapability("osint-screening-agent", "osint-screening"),
        new AgentCapability("entity-resolution-agent", "entity-resolution"),
        new AgentCapability("pattern-analysis-agent", "pattern-analysis"),
        new AgentCapability("senior-analyst-agent", "senior-analyst-review"),
        new AgentCapability("compliance-review-opening-agent", "compliance-review-opening")
    );

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Inject
    TrustScoreSnapshotRepository snapshotRepo;

    /**
     * Capture current trust scores for all known agents and persist as snapshots.
     * Scheduled to run every hour.
     */
    @Scheduled(every = "1h")
    public void captureSnapshots() {
        final Instant now = Instant.now();
        final List<TrustScoreSnapshot> snapshots = new ArrayList<>();

        for (final AgentCapability ac : KNOWN_AGENTS) {
            final var scoreOpt = trustRepo.findCapabilityScore(ac.agentId(), ac.capabilityTag());
            if (scoreOpt.isPresent()) {
                final var score = scoreOpt.get();
                snapshots.add(new TrustScoreSnapshot(
                    ac.agentId(),
                    ac.capabilityTag(),
                    score.alpha,
                    score.beta,
                    score.trustScore,
                    now
                ));
            }
        }

        if (!snapshots.isEmpty()) {
            snapshotRepo.saveAll(snapshots);
        }
        LOG.debugf("Captured %d trust score snapshots", snapshots.size());
    }

    /**
     * Retrieve historical snapshots for an agent/capability pair, ordered by timestamp.
     *
     * @param agentId     agent identifier
     * @param capability  capability tag
     * @return snapshots ordered by {@code snapshotTimestamp} ascending
     */
    public List<TrustScoreSnapshot> getHistory(String agentId, String capability) {
        return snapshotRepo.findByAgentAndCapability(agentId, capability);
    }

    private record AgentCapability(String agentId, String capabilityTag) {}
}
