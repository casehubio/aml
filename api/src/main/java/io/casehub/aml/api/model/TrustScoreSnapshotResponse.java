package io.casehub.aml.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * REST response DTO for a single trust score snapshot.
 *
 * @param id                snapshot identifier
 * @param agentId           agent identifier (e.g. "sar-drafting-agent-senior")
 * @param capability        capability tag (e.g. "sar-drafting")
 * @param alpha             Bayesian Beta alpha parameter at snapshot time
 * @param beta              Bayesian Beta beta parameter at snapshot time
 * @param score             pre-computed mean: alpha / (alpha + beta)
 * @param snapshotTimestamp  when the snapshot was captured
 */
public record TrustScoreSnapshotResponse(
    UUID id,
    String agentId,
    String capability,
    double alpha,
    double beta,
    double score,
    Instant snapshotTimestamp
) {
}
