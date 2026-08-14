package io.casehub.aml;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AmlInvestigationResourceTest {

    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject WorkItemService workItemService;

    private static final String VALID_TX = """
            {
              "id": "TXN-001",
              "originAccountId": "ACC-A",
              "destinationAccountId": "ACC-B",
              "amount": 100000,
              "currency": "USD",
              "timestamp": "2024-03-15T10:00:00Z",
              "flagReason": "STRUCTURING"
            }
            """;

    @Test
    void postInvestigation_validTransaction_returns200WithSummaryAndWorkItem() {
        String taskId = given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200)
                .body("summary.transaction.id",        equalTo("TXN-001"))
                .body("summary.entityResolution.type",  equalTo("Completed"))
                .body("summary.patternAnalysis.type",   equalTo("Completed"))
                .body("summary.osintScreening",         notNullValue())
                .body("summary.sarNarrative",          notNullValue())
                .body("complianceReviewTaskId",        notNullValue())
        .extract().path("complianceReviewTaskId");

        WorkItem workItem = workItemService.findById(UUID.fromString(taskId))
                                                 .orElseThrow(() -> new AssertionError("WorkItem not found: " + taskId));
        assertEquals("compliance-officers", workItem.candidateGroups());
        assertNotNull(workItem.claimDeadline(), "claimDeadline must not be null");

        Instant now = Instant.now();
        assertTrue(workItem.claimDeadline().isAfter(now), "claimDeadline must be in the future");
        assertTrue(workItem.claimDeadline().isBefore(now.plus(31, ChronoUnit.DAYS)),
                "claimDeadline must be within the 30-day FinCEN SLA window");
    }

    @Test
    void postInvestigation_osintIsDeclined_notAnError() {
        // Layer 3: OSINT agent DECLINEs (insufficient clearance for PEP database).
        // The investigation completes — DECLINE is a formal scope boundary, not a failure.
        given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200)
                .body("summary.osintScreening.type",   equalTo("Declined"))
                .body("summary.osintScreening.reason", containsString("clearance"))
                .body("complianceReviewTaskId",        notNullValue());
    }

    @Test
    void postInvestigation_persistsQhorusCommandAndReplyForEachSpecialist() {
        // Layer 3 teaching point: every specialist dispatch creates a formal COMMAND
        // commitment in qhorus, and a DONE or DECLINE closes it.
        given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200);

        // entity-resolution: COMMAND + DONE (always completes)
        assertChannelHasMessageType("entity-resolution", MessageType.COMMAND);
        assertChannelHasMessageType("entity-resolution", MessageType.DONE);

        // pattern-analysis: COMMAND + DONE (always completes)
        assertChannelHasMessageType("pattern-analysis", MessageType.COMMAND);
        assertChannelHasMessageType("pattern-analysis", MessageType.DONE);

        // osint-screening: COMMAND + DECLINE (OsintScreeningBehaviour always declines)
        assertChannelHasMessageType("osint-screening", MessageType.COMMAND);
        assertChannelHasMessageType("osint-screening", MessageType.DECLINE);
    }

    @Test
    void postInvestigation_malformedJson_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("not-valid-json")
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(400);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertChannelHasMessageType(final String channelName, final MessageType expected) {
        final var channel = channelService.findByName(channelName);
        assertTrue(channel.isPresent(), "Channel must exist after investigation: " + channelName);

        // pollAfter from 0 with a generous limit — EventType messages are excluded by default
        // but COMMAND/DONE/DECLINE are not EVENT, so they are included.
        // @ApplicationScoped InMemoryMessageStore accumulates across test methods in the same
        // Quarkus test session (GE-20260512-e552f7), so "at least one" is the correct assertion.
        final List<Message> messages = messageService.pollAfter(channel.get().id(), 0L, 200);
        assertTrue(
                messages.stream().anyMatch(m -> expected == m.messageType()),
                "Channel '" + channelName + "' must contain at least one " + expected + " message"
                        + " — found: " + messages.stream().map(m -> m.messageType().name()).toList());
    }
}
