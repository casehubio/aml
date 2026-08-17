package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.runtime.privacy.LedgerErasureService.ErasureResult;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.neocortex.memory.CaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AmlErasureService {

    private final LedgerErasureService ledgerErasureService;
    private final CaseMemoryStore memoryStore;
    private final CurrentPrincipal principal;
    private final AmlLedgerService ledgerService;

    @Inject
    public AmlErasureService(
            final LedgerErasureService ledgerErasureService,
            final CaseMemoryStore memoryStore,
            final CurrentPrincipal principal,
            final AmlLedgerService ledgerService) {
        this.ledgerErasureService = ledgerErasureService;
        this.memoryStore = memoryStore;
        this.principal = principal;
        this.ledgerService = ledgerService;
    }

    public ActorErasureResult erase(final String actorId, final ErasureReason reason) {
        final ErasureResult ledgerResult = ledgerErasureService.erase(actorId, reason);
        return new ActorErasureResult(
                ledgerResult.rawActorId(),
                ledgerResult.mappingFound(),
                ledgerResult.affectedEntryCount(),
                ledgerResult.receiptEntryId().orElse(null));
    }

    public EntityErasureResult eraseEntity(final String entityId, final ErasureReason reason) {
        return eraseEntity(entityId, principal.tenancyId(), reason);
    }

    public EntityErasureResult eraseEntity(final String entityId, final String tenantId,
            final ErasureReason reason) {
        final int memoriesErased = memoryStore.eraseEntity(entityId, tenantId);
        final UUID receiptEntryId = ledgerService.writeEntityErasure(
                entityId, reason, memoriesErased,
                principal.actorId(), principal.actorType());
        return new EntityErasureResult(entityId, memoriesErased, receiptEntryId);
    }

    public CrossTenantErasureResult eraseEntityAcrossTenants(final String entityId,
            final Set<String> tenantIds, final ErasureReason reason) {
        final int memoriesErased = memoryStore.eraseEntityAcrossTenants(entityId, tenantIds);
        final UUID receiptEntryId = ledgerService.writeEntityErasure(
                entityId, reason, memoriesErased,
                principal.actorId(), principal.actorType());
        return new CrossTenantErasureResult(entityId, tenantIds.size(), memoriesErased, receiptEntryId);
    }
}
