package io.casehub.aml.supervisor;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AmlSupervisorPendingStore {

    private final ConcurrentHashMap<UUID, PendingSupervisorDecision> pending =
            new ConcurrentHashMap<>();

    public void put(PendingSupervisorDecision decision) {
        pending.put(decision.caseId(), decision);
    }

    public PendingSupervisorDecision take(UUID caseId) {
        return pending.remove(caseId);
    }
}
