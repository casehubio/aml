package io.casehub.aml.api.model;

import java.util.List;

/**
 * Trust score metrics for all known AML agents.
 * Fetched from {@code TrustScoreSource} SPI for agent/capability pairs
 * registered in the trust routing policy.
 *
 * @param scores Trust scores for each agent-capability pair
 */
public record TrustScoreMetrics(
    List<AgentTrustScore> scores
) {
}
