package io.casehub.aml.api.mcp;

import java.time.Instant;
import java.util.UUID;

public record AuditTrailEntry(
        UUID id,
        String dtype,
        String actorId,
        String actorType,
        Instant timestamp,
        long sequenceNumber,
        UUID causedByEntryId,
        String digest
) {}
