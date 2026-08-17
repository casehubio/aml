package io.casehub.aml.compliance;

import java.util.UUID;

public record CrossTenantErasureResult(
        String entityId,
        int tenantsRequested,
        int memoriesErased,
        UUID receiptEntryId) {}
