package io.casehub.aml.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Point-in-time snapshot of a Bayesian Beta trust score for an agent/capability pair.
 *
 * <p>Captured periodically by {@code TrustScoreSnapshotService} to provide historical
 * trend data for the AML workbench UI. Lives on the default datasource — this is an
 * AML application concern, not a ledger entry.
 *
 * <p>The {@code score} field is the pre-computed mean: {@code alpha / (alpha + beta)}.
 */
@Entity
@Table(name = "trust_score_snapshot")
public class TrustScoreSnapshot {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "capability", nullable = false, length = 100)
    private String capability;

    @Column(name = "alpha", nullable = false)
    private double alpha;

    @Column(name = "beta", nullable = false)
    private double beta;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "snapshot_timestamp", nullable = false)
    private Instant snapshotTimestamp;

    /** JPA-required no-arg constructor. */
    protected TrustScoreSnapshot() {}

    /**
     * Create a new trust score snapshot.
     *
     * @param agentId           agent identifier (e.g. "sar-drafting-agent-senior")
     * @param capability        capability tag (e.g. "sar-drafting")
     * @param alpha             Bayesian Beta alpha parameter
     * @param beta              Bayesian Beta beta parameter
     * @param score             pre-computed mean: alpha / (alpha + beta)
     * @param snapshotTimestamp  when the snapshot was captured
     */
    public TrustScoreSnapshot(String agentId, String capability,
            double alpha, double beta, double score, Instant snapshotTimestamp) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.capability = capability;
        this.alpha = alpha;
        this.beta = beta;
        this.score = score;
        this.snapshotTimestamp = snapshotTimestamp;
    }

    // ────────────────────────────────────────────────────────────────────
    // Getters — record-style naming (no "get" prefix)
    // ────────────────────────────────────────────────────────────────────

    public UUID id() { return id; }

    public String agentId() { return agentId; }

    public String capability() { return capability; }

    public double alpha() { return alpha; }

    public double beta() { return beta; }

    public double score() { return score; }

    public Instant snapshotTimestamp() { return snapshotTimestamp; }
}
