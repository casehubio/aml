package io.casehub.aml.trust;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlTrustScoreSeederTest {

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Inject
    TrustScoreSource trustScoreSource;

    @Test
    void sar_drafting_senior_seeded_with_high_trust() {
        final var score = trustRepo.findCapabilityScore("sar-drafting-agent-senior", "sar-drafting");
        assertTrue(score.isPresent(), "CAPABILITY score must exist for sar-drafting-agent-senior");
        assertEquals(ScoreType.CAPABILITY, score.get().scoreType);
        assertEquals(0.90, score.get().trustScore, 0.01);
        assertEquals(10, score.get().decisionCount);
    }

    @Test
    void osint_senior_seeded_with_high_trust() {
        final var score = trustRepo.findCapabilityScore("osint-screening-agent-senior", "osint-screening");
        assertTrue(score.isPresent());
        assertEquals(0.818, score.get().trustScore, 0.01);
    }

    @Test
    void osint_junior_seeded_with_low_trust() {
        final var score = trustRepo.findCapabilityScore("osint-screening-agent", "osint-screening");
        assertTrue(score.isPresent());
        assertEquals(0.30, score.get().trustScore, 0.01);
    }

    @Test
    void trust_score_source_reflects_seeded_scores() {
        assertTrue(trustScoreSource.capabilityScore("sar-drafting-agent-senior", "sar-drafting").isPresent());
        assertEquals(0.90,
                trustScoreSource.capabilityScore("sar-drafting-agent-senior", "sar-drafting").getAsDouble(),
                0.01);
    }

    @Test
    void seeding_is_idempotent() {
        final var scoreBefore = trustRepo.findCapabilityScore("sar-drafting-agent-senior", "sar-drafting");
        final var scoreAfter = trustRepo.findCapabilityScore("sar-drafting-agent-senior", "sar-drafting");
        assertEquals(scoreBefore.get().trustScore, scoreAfter.get().trustScore, 0.0001);
    }

    @Test
    void compliance_review_opening_seeded_with_high_trust() {
        final var score = trustRepo.findCapabilityScore(
                "compliance-review-opening-agent", "compliance-review-opening");
        assertTrue(score.isPresent(),
                "CAPABILITY score must exist for compliance-review-opening-agent");
        assertEquals(0.90, score.get().trustScore, 0.01);
    }
}
