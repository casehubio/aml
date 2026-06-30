package io.casehub.aml.api.model;

import java.time.Instant;

/**
 * A single specialist worker node in the investigation flow graph.
 *
 * @param capabilityTag the capability this worker provides (e.g., "entity-resolution", "osint-screening")
 * @param workerId the selected worker ID (e.g., "osint-screening-agent-senior")
 * @param trustScoreAtRouting trust score at the time of routing (null if not trust-routed or attestation missing)
 * @param status worker status: "scheduled", "completed", or "failed"
 * @param timestamp when the worker was scheduled
 */
public record FlowNode(
    String capabilityTag,
    String workerId,
    Double trustScoreAtRouting,
    String status,
    Instant timestamp
) {}
