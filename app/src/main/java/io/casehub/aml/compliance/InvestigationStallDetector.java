package io.casehub.aml.compliance;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class InvestigationStallDetector {

    private static final Logger LOG = Logger.getLogger(InvestigationStallDetector.class);
    static final Duration STALL_THRESHOLD = Duration.ofHours(4);

    private final CaseInstanceCache caseInstanceCache;

    @Inject
    public InvestigationStallDetector(CaseInstanceCache caseInstanceCache) {
        this.caseInstanceCache = caseInstanceCache;
    }

    public List<StalledInvestigation> detectStalled() {
        return caseInstanceCache.getAll().stream()
                .filter(i -> i.getState() == CaseStatus.WAITING)
                .filter(this::isStalled)
                .map(i -> new StalledInvestigation(
                        i.getUuid(),
                        i.getWaitingForWorkId(),
                        Duration.between(stalledSince(i), Instant.now())))
                .toList();
    }

    private boolean isStalled(CaseInstance instance) {
        Instant since = stalledSince(instance);
        return since != null && Duration.between(since, Instant.now()).compareTo(STALL_THRESHOLD) > 0;
    }

    private Instant stalledSince(CaseInstance instance) {
        Object ts = instance.getCaseContext().get("_lastWorkerDispatchedAt");
        if (ts instanceof String s) {
            try {
                return Instant.parse(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public record StalledInvestigation(java.util.UUID caseId, String waitingForWorkId, Duration stalledFor) {}
}
