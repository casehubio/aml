package io.casehub.aml.api.model;

/**
 * Trust score for a single agent-capability pair.
 *
 * @param agentId Agent identifier (e.g., "sar-drafting-agent-senior")
 * @param capabilityTag Capability tag (e.g., "sar-drafting")
 * @param score Trust score (0.0 to 1.0), or null if no observations yet
 */
public record AgentTrustScore(
    String agentId,
    String capabilityTag,
    Double score
) {
}
