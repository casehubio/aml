package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.ErasureReason;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that enforces BSA/AML data retention policy.
 *
 * <p>Identifies human actors whose most recent ledger entry is older than the
 * configured retention window and triggers identity erasure (pseudonymisation)
 * via {@link AmlErasureService} with {@link ErasureReason#RETENTION_EXPIRED}.
 *
 * <p>Default retention period is 2555 days (~7 years), per BSA/AML record-keeping
 * requirements (31 CFR 1010.430). The schedule and retention window are both
 * configurable via application properties.
 *
 * <p>Only {@code HUMAN} actors are targeted — {@code SYSTEM} and {@code AGENT}
 * actors are not natural persons and have no GDPR retention obligation.
 */
@ApplicationScoped
public class RetentionExpiryJob {

    private static final Logger LOG = Logger.getLogger(RetentionExpiryJob.class);

    private final AmlErasureService erasureService;
    private final int retentionDays;

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Inject
    public RetentionExpiryJob(
            AmlErasureService erasureService,
            @ConfigProperty(name = "casehub.aml.retention.days", defaultValue = "2555") int retentionDays) {
        this.erasureService = erasureService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "{casehub.aml.retention.cron}")
    @Transactional
    void enforceRetention() {
        final Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        LOG.infof("Retention expiry check: cutoff=%s (retention=%d days)", cutoff, retentionDays);
        final List<String> actors = findExpiredHumanActors(cutoff);
        processRetention(actors);
    }

    void processRetention(final List<String> actorIds) {
        if (actorIds.isEmpty()) {
            LOG.debug("Retention expiry: no actors past retention window");
            return;
        }
        LOG.infof("Retention expiry: processing %d actor(s)", actorIds.size());
        int erased = 0;
        int skipped = 0;
        int failed = 0;
        for (final String actorId : actorIds) {
            try {
                final ActorErasureResult result = erasureService.erase(actorId, ErasureReason.RETENTION_EXPIRED);
                if (result.mappingFound()) {
                    erased++;
                    LOG.infof("Retention erasure for actor '%s': %d entries affected",
                            actorId, result.affectedEntryCount());
                } else {
                    skipped++;
                    LOG.debugf("Retention erasure for actor '%s': already erased or no mapping", actorId);
                }
            } catch (final Exception e) {
                failed++;
                LOG.warnf(e, "Retention erasure failed for actor '%s'", actorId);
            }
        }
        LOG.infof("Retention expiry complete: %d erased, %d skipped, %d failed", erased, skipped, failed);
    }

    @SuppressWarnings("unchecked")
    List<String> findExpiredHumanActors(final Instant cutoff) {
        return em.createNativeQuery(
                "SELECT actor_id FROM ledger_entry " +
                "WHERE actor_type = 'HUMAN' " +
                "GROUP BY actor_id " +
                "HAVING MAX(occurred_at) < :cutoff")
            .setParameter("cutoff", cutoff)
            .getResultList();
    }
}
