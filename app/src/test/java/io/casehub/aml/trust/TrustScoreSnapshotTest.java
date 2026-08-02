package io.casehub.aml.trust;

import io.casehub.aml.domain.TrustScoreSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for trust score snapshot capture and history query.
 *
 * <p>Trust scores are seeded at startup by {@link AmlTrustScoreSeeder}, so
 * {@link TrustScoreSnapshotService#captureSnapshots()} will find scores to snapshot
 * without additional setup.
 */
@QuarkusTest
class TrustScoreSnapshotTest {

    @Inject
    TrustScoreSnapshotService snapshotService;

    @Inject
    TrustScoreSnapshotRepository snapshotRepo;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setUp() {
        em.createQuery("DELETE FROM TrustScoreSnapshot").executeUpdate();
    }

    @Test
    void captureSnapshots_persists_all_known_agents() {
        snapshotService.captureSnapshots();

        List<TrustScoreSnapshot> sarSeniorHistory = snapshotRepo.findByAgentAndCapability(
            "sar-drafting-agent-senior", "sar-drafting");
        assertThat(sarSeniorHistory, hasSize(1));

        TrustScoreSnapshot snapshot = sarSeniorHistory.get(0);
        assertThat(snapshot.id(), is(notNullValue()));
        assertThat(snapshot.agentId(), is("sar-drafting-agent-senior"));
        assertThat(snapshot.capability(), is("sar-drafting"));
        assertThat(snapshot.score(), is(closeTo(0.9, 0.01)));
        assertThat(snapshot.alpha(), is(closeTo(9.0, 0.5)));
        assertThat(snapshot.beta(), is(closeTo(1.0, 0.5)));
        assertThat(snapshot.snapshotTimestamp(), is(notNullValue()));
    }

    @Test
    void captureSnapshots_captures_multiple_agents() {
        snapshotService.captureSnapshots();

        // Verify a different agent is also captured
        List<TrustScoreSnapshot> osintHistory = snapshotRepo.findByAgentAndCapability(
            "osint-screening-agent-senior", "osint-screening");
        assertThat(osintHistory, hasSize(1));
        assertThat(osintHistory.get(0).score(), is(greaterThan(0.0)));
    }

    @Test
    void repeated_captures_accumulate_snapshots() {
        snapshotService.captureSnapshots();
        snapshotService.captureSnapshots();

        List<TrustScoreSnapshot> history = snapshotRepo.findByAgentAndCapability(
            "sar-drafting-agent-senior", "sar-drafting");
        assertThat(history, hasSize(2));
    }

    @Test
    void getHistory_returns_ordered_by_timestamp() {
        snapshotService.captureSnapshots();
        snapshotService.captureSnapshots();

        List<TrustScoreSnapshot> history = snapshotService.getHistory(
            "sar-drafting-agent-senior", "sar-drafting");
        assertThat(history, hasSize(2));
        assertThat(history.get(0).snapshotTimestamp(),
            is(lessThanOrEqualTo(history.get(1).snapshotTimestamp())));
    }

    @Test
    void getHistory_returns_empty_for_unknown_agent() {
        List<TrustScoreSnapshot> history = snapshotService.getHistory(
            "nonexistent-agent", "nonexistent-capability");
        assertThat(history, is(empty()));
    }

    @Test
    void rest_endpoint_returns_history() {
        snapshotService.captureSnapshots();

        RestAssured
            .given()
                .accept(ContentType.JSON)
                .queryParam("agentId", "sar-drafting-agent-senior")
                .queryParam("capability", "sar-drafting")
            .when()
                .get("/api/metrics/trust-scores/history")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .body("[0].agentId", is("sar-drafting-agent-senior"))
                .body("[0].capability", is("sar-drafting"))
                .body("[0].score", is(notNullValue()))
                .body("[0].alpha", is(notNullValue()))
                .body("[0].beta", is(notNullValue()))
                .body("[0].snapshotTimestamp", is(notNullValue()));
    }

    @Test
    void rest_endpoint_returns_empty_for_unknown_agent() {
        RestAssured
            .given()
                .accept(ContentType.JSON)
                .queryParam("agentId", "unknown-agent")
                .queryParam("capability", "unknown")
            .when()
                .get("/api/metrics/trust-scores/history")
            .then()
                .statusCode(200)
                .body("$.size()", is(0));
    }
}
