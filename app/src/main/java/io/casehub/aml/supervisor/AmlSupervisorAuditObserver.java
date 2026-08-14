package io.casehub.aml.supervisor;

import io.casehub.aml.ledger.AmlSupervisorDecisionLedgerEntry;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AmlSupervisorAuditObserver {

    private static final Logger LOG = Logger.getLogger(AmlSupervisorAuditObserver.class);

    private final AmlSupervisorPendingStore pendingStore;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Inject
    public AmlSupervisorAuditObserver(
            AmlSupervisorPendingStore pendingStore,
            LedgerEntryRepository ledgerEntryRepository) {
        this.pendingStore = pendingStore;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void onWorkerDecision(@ObservesAsync WorkerDecisionEvent event) {
        PendingSupervisorDecision pending = pendingStore.take(event.caseId());
        if (pending == null) return;

        try {
            var entry = new AmlSupervisorDecisionLedgerEntry();
            entry.id = UUID.randomUUID();
            entry.selectedBindings = String.join(",", pending.decision().selectedBindings());
            entry.suppressedBindings = String.join(",", pending.decision().suppressedBindings());
            entry.rationale = pending.decision().rationale();
            entry.earlyTermination = pending.decision().earlyTermination();
            entry.eligibleCount = pending.eligibleCount();
            entry.degraded = pending.degraded();
            entry.subjectId = UUID.nameUUIDFromBytes(
                    ("aml-supervisor:" + event.caseId()).getBytes(StandardCharsets.UTF_8));
            entry.tenancyId = pending.tenancyId() != null
                    ? pending.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;
            entry.actorId = "aml-supervisor";
            entry.actorType = ActorType.SYSTEM;
            entry.entryType = LedgerEntryType.EVENT;
            entry.occurredAt = Instant.now();

            ledgerEntryRepository.save(entry, entry.tenancyId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to write supervisor audit entry for case %s",
                    event.caseId());
        }
    }
}
