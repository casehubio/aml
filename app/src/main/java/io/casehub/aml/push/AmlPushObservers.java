package io.casehub.aml.push;

import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.pages.push.EventBroadcaster;
import io.casehub.work.api.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class AmlPushObservers {

    private static final Logger LOG = Logger.getLogger(AmlPushObservers.class);

    @Inject EventBroadcaster broadcaster;

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) return;
        try {
            broadcaster.broadcast("investigation:status", Map.of(
                    "caseId", event.caseId().toString(),
                    "status", event.caseStatus(),
                    "updatedAt", Instant.now().toString()));
        } catch (Exception e) {
            LOG.debugf(e, "Push broadcast failed for investigation:status");
        }
    }

    void onWorkItemLifecycle(@ObservesAsync WorkItemLifecycleEvent event) {
        if (event.workItem() == null) return;
        try {
            broadcaster.broadcast("work-item:lifecycle", Map.of(
                    "workItemId", event.workItem().id().toString(),
                    "status", event.workItem().status().name(),
                    "updatedAt", Instant.now().toString()));
        } catch (Exception e) {
            LOG.debugf(e, "Push broadcast failed for work-item:lifecycle");
        }
    }

    void onWorkerDecision(@ObservesAsync WorkerDecisionEvent event) {
        try {
            broadcaster.broadcast("worker-task:decision", Map.of(
                    "caseId", event.caseId().toString(),
                    "workerId", event.workerId(),
                    "capabilityTag", event.capabilityTag() != null ? event.capabilityTag() : "",
                    "updatedAt", Instant.now().toString()));

            if (event.selectionContext() != null) {
                broadcaster.broadcast("trust-score:update", Map.of(
                        "agentId", event.workerId(),
                        "capabilityTag", event.capabilityTag() != null ? event.capabilityTag() : "",
                        "updatedAt", Instant.now().toString()));
            }
        } catch (Exception e) {
            LOG.debugf(e, "Push broadcast failed for worker-task:decision");
        }
    }

    void onGateDecision(@ObservesAsync WorkItemLifecycleEvent event) {
        if (event.workItem() == null) return;
        String callerRef = event.workItem().callerRef;
        if (callerRef == null || !callerRef.contains("/gate:")) return;
        if (!event.workItem().status().isTerminal()) return;
        try {
            broadcaster.broadcast("gate:decision", Map.of(
                    "workItemId", event.workItem().id().toString(),
                    "status", event.workItem().status().name(),
                    "callerRef", callerRef,
                    "decidedAt", Instant.now().toString()));
        } catch (Exception e) {
            LOG.debugf(e, "Push broadcast failed for gate:decision");
        }
    }
}
