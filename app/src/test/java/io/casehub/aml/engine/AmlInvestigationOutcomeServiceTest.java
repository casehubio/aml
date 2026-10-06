package io.casehub.aml.engine;

import io.casehub.aml.domain.FailureContext;
import io.casehub.aml.domain.FailureEvent;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmlInvestigationOutcomeServiceTest {

    @Test
    void returns_null_when_no_officer_review_entry() {
        final AmlInvestigationOutcomeService service = serviceWith(List.of());
        assertNull(service.resolveOutcome(UUID.randomUUID()));
    }

    @Test
    void returns_sar_filed_for_approved_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void returns_gate_rejected_for_rejected_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void returns_decision_not_recorded_for_unknown_system_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("decision-not-recorded", outcome.type());
    }

    @Test
    void prefers_human_entry_over_system_entry_in_race() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(humanEntry, systemEntry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void prefers_human_entry_regardless_of_list_order() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        // SYSTEM entry first in list
        final AmlInvestigationOutcomeService service = serviceWith(List.of(systemEntry, humanEntry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void resolveInvestigation_cache_hit_completed_returns_completed_with_outcome() {
        final UUID caseId = UUID.randomUUID();
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final CaseInstance instance = completedInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.COMPLETED, result.get().status());
        assertNotNull(result.get().outcome());
        assertEquals("sar-filed", result.get().outcome().type());
    }

    @Test
    void resolveInvestigation_cache_hit_not_completed_returns_in_progress() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = inProgressInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.IN_PROGRESS, result.get().status());
        assertNull(result.get().outcome());
    }

    @Test
    void resolveInvestigation_cache_miss_repo_hit_returns_completed() {
        final UUID caseId = UUID.randomUUID();
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.HUMAN);
        final CaseInstance instance = completedInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry), null, instance);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.COMPLETED, result.get().status());
        assertEquals("gate-rejected", result.get().outcome().type());
    }

    @Test
    void resolveInvestigation_cache_miss_repo_miss_returns_empty() {
        final UUID caseId = UUID.randomUUID();
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), null, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isEmpty());
    }

    @Test
    void prefers_higher_sequenceNumber_among_human_entries() {
        final AmlSarOfficerReviewedLedgerEntry older = officerEntry("REJECTED", ActorType.HUMAN);
        older.sequenceNumber = 3;
        final AmlSarOfficerReviewedLedgerEntry newer = officerEntry("APPROVED", ActorType.HUMAN);
        newer.sequenceNumber = 7;
        final AmlInvestigationOutcomeService service = serviceWith(List.of(older, newer));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void system_only_rejected_returns_gate_rejected() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void resolveInvestigation_faulted_goal_triggered_returns_failure_context_with_goal() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.FAULTED);
        final Instant faultTime = Instant.parse("2026-06-29T10:00:00Z");

        final EventLog faultedEvent = eventLog(caseId, CaseHubEventType.CASE_FAULTED,
                null, faultTime, goalMetadata("pattern-agent-failed", "failure"));
        final EventLog workerFailed = eventLog(caseId, CaseHubEventType.WORKER_EXECUTION_FAILED,
                "pattern-analysis", faultTime.minusSeconds(5), errorMetadata("Connection refused"));

        final AmlInvestigationOutcomeService service = serviceWith(
                List.of(), instance, null, List.of(faultedEvent, workerFailed));
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);

        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.FAILED, result.get().status());
        assertNull(result.get().outcome());
        assertNotNull(result.get().failureContext());

        final FailureContext ctx = result.get().failureContext();
        assertEquals("pattern-agent-failed", ctx.triggerGoalName());
        assertEquals("failure", ctx.triggerGoalKind());
        assertEquals(faultTime, ctx.occurredAt());
        assertEquals(1, ctx.failureEvents().size());
        assertEquals("WORKER_EXECUTION_FAILED", ctx.failureEvents().get(0).eventType());
        assertEquals("pattern-analysis", ctx.failureEvents().get(0).workerId());
        assertEquals("Connection refused", ctx.failureEvents().get(0).detail());
    }

    @Test
    void resolveInvestigation_faulted_retries_exhausted_disambiguates_two_faulted_events() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.FAULTED);
        final Instant t1 = Instant.parse("2026-06-29T10:00:00Z");
        final Instant t2 = t1.plusSeconds(1);

        final EventLog retriesFaulted = eventLog(caseId, CaseHubEventType.CASE_FAULTED,
                "pattern-analysis", t1, workerRetriesMetadata("pattern-analysis"));
        final EventLog statusChangeFaulted = eventLog(caseId, CaseHubEventType.CASE_FAULTED,
                null, t2, statusChangeMetadata("RUNNING", "FAULTED"));
        final EventLog workerFailed = eventLog(caseId, CaseHubEventType.WORKER_EXECUTION_FAILED,
                "pattern-analysis", t1.minusSeconds(5), errorMetadata("Timeout"));

        final AmlInvestigationOutcomeService service = serviceWith(
                List.of(), instance, null, List.of(retriesFaulted, statusChangeFaulted, workerFailed));
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);

        assertTrue(result.isPresent());
        final FailureContext ctx = result.get().failureContext();
        assertNull(ctx.triggerGoalName());
        assertNull(ctx.triggerGoalKind());
        assertEquals(t1, ctx.occurredAt());
        assertEquals(1, ctx.failureEvents().size());
    }

    @Test
    void resolveInvestigation_cancelled_returns_failure_context() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.CANCELLED);
        final Instant cancelTime = Instant.parse("2026-06-29T12:00:00Z");

        final EventLog cancelledEvent = eventLog(caseId, CaseHubEventType.CASE_CANCELLED,
                null, cancelTime, statusChangeMetadata("RUNNING", "CANCELLED"));

        final AmlInvestigationOutcomeService service = serviceWith(
                List.of(), instance, null, List.of(cancelledEvent));
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);

        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.CANCELLED, result.get().status());
        assertNull(result.get().outcome());
        assertNotNull(result.get().failureContext());
        assertEquals(cancelTime, result.get().failureContext().occurredAt());
        assertTrue(result.get().failureContext().failureEvents().isEmpty());
    }

    @Test
    void resolveInvestigation_suspended_returns_no_failure_context() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.SUSPENDED);
        final AmlInvestigationOutcomeService service = serviceWith(
                List.of(), instance, null, List.of());
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);

        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.SUSPENDED, result.get().status());
        assertNull(result.get().outcome());
        assertNull(result.get().failureContext());
    }

    @Test
    void resolveInvestigation_completed_returns_no_failure_context() {
        final UUID caseId = UUID.randomUUID();
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final CaseInstance instance = completedInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(
                List.of(entry), instance, null, List.of());
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);

        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.COMPLETED, result.get().status());
        assertNotNull(result.get().outcome());
        assertNull(result.get().failureContext());
    }

    @Test
    void resolveInvestigation_starting_returns_in_progress() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.STARTING);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.IN_PROGRESS, result.get().status());
    }

    @Test
    void resolveInvestigation_waiting_returns_in_progress() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = instanceWithState(caseId, CaseStatus.WAITING);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.IN_PROGRESS, result.get().status());
    }

    private static CaseInstance instanceWithState(UUID caseId, CaseStatus state) {
        final CaseInstance instance = new CaseInstance();
        instance.setUuid(caseId);
        instance.setState(state);
        return instance;
    }

    private static CaseInstance completedInstance(final UUID caseId) {
        return instanceWithState(caseId, CaseStatus.COMPLETED);
    }

    private static CaseInstance inProgressInstance(final UUID caseId) {
        return instanceWithState(caseId, CaseStatus.RUNNING);
    }

    private static AmlSarOfficerReviewedLedgerEntry officerEntry(final String decision,
            final ActorType actorType) {
        final AmlSarOfficerReviewedLedgerEntry entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.reviewDecision = decision;
        entry.actorType = actorType;
        return entry;
    }

    private static AmlInvestigationOutcomeService serviceWith(final List<LedgerEntry> entries) {
        return serviceWith(entries, null, null, List.of());
    }

    private static AmlInvestigationOutcomeService serviceWith(
            final List<LedgerEntry> entries,
            final CaseInstance cacheResult,
            final CaseInstance repoResult) {
        return serviceWith(entries, cacheResult, repoResult, List.of());
    }

    private static AmlInvestigationOutcomeService serviceWith(
            final List<LedgerEntry> entries,
            final CaseInstance cacheResult,
            final CaseInstance repoResult,
            final List<EventLog> eventLogs) {
        final LedgerEntryRepository repo = new LedgerEntryRepository() {
            @Override
            public LedgerEntry save(LedgerEntry entry, String tenancyId) {
                return null;
            }

            @Override
            public List<LedgerEntry> findBySubjectId(UUID subjectId, String tenancyId) {
                return entries;
            }

            @Override
            public List<LedgerEntry> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public Optional<LedgerEntry> findLatestBySubjectId(UUID subjectId, String tenancyId) {
                return Optional.empty();
            }

            @Override
            public Optional<LedgerEntry> findEntryById(UUID id, String tenancyId) {
                return Optional.empty();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId, String tenancyId) {
                return List.of();
            }

            @Override
            public LedgerAttestation saveAttestation(LedgerAttestation attestation, String tenancyId) {
                return null;
            }

            @Override
            public List<LedgerEntry> findByActorId(String actorId, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerEntry> findByActorRole(String actorRole, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerEntry> findCausedBy(UUID entryId, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String capabilityTag, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String capabilityTag, String tenancyId) {
                return List.of();
            }
        };
        final CaseInstanceCache cache = new CaseInstanceCache() {
            @Override
            public void put(CaseInstance caseInstance) {}

            @Override
            public CaseInstance get(UUID caseId) {
                return cacheResult;
            }

            @Override
            public void clear() {}

            @Override
            public List<CaseInstance> getAll() {
                return List.of();
            }
        };
        final CaseInstanceRepository caseRepo = new CaseInstanceRepository() {
            @Override
            public Optional<CaseInstance> findByUuid(UUID uuid, String tenancyId) {
                return Optional.ofNullable(repoResult);
            }

            @Override
            public CaseInstance save(CaseInstance i, String t) {
                return null;
            }

            @Override
            public CaseInstance update(CaseInstance i, String t) {
                return null;
            }

            @Override
            public void updateStateAndAppendEvent(CaseInstance i, EventLog e, String t) {
            }
        };
        final EventLogRepository eventLogRepo = new EventLogRepository() {
            @Override
            public void append(EventLog e, String t) { }

            @Override
            public Long appendAndReturnId(EventLog e, String t) { return 1L; }

            @Override
            public EventLog findById(Long id, String t) { return null; }

            @Override
            public List<EventLog> findSchedulingEvents(UUID c, String w, Instant a, String t) {
                return List.of();
            }

            @Override
            public List<EventLog> findByCaseAndTypes(UUID c, Collection<CaseHubEventType> types, String t) {
                return eventLogs.stream()
                        .filter(e -> types.contains(e.getEventType()))
                        .toList();
            }

            @Override
            public List<EventLog> findByCaseAndWorkerAndType(UUID c, String w, CaseHubEventType t, String ten) {
                return List.of();
            }

            @Override
            public List<EventLog> findByWorkerAndType(String w, CaseHubEventType t, String ten) {
                return List.of();
            }

            @Override
            public List<EventLog> findByCaseWithFilters(UUID c, Collection<CaseHubEventType> et,
                    Collection<EventStreamType> st, String t) {
                return List.of();
            }
        };
        return new AmlInvestigationOutcomeService(repo, cache, caseRepo, eventLogRepo);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static EventLog eventLog(UUID caseId, CaseHubEventType type,
            String workerId, Instant timestamp, ObjectNode metadata) {
        final EventLog log = new EventLog();
        log.setCaseId(caseId);
        log.setEventType(type);
        log.setStreamType(EventStreamType.CASE);
        log.setWorkerId(workerId);
        log.setTimestamp(timestamp);
        log.setMetadata(metadata);
        return log;
    }

    private static ObjectNode goalMetadata(String goalName, String goalKind) {
        return MAPPER.createObjectNode()
                .put("oldStatus", "RUNNING").put("newStatus", "FAULTED")
                .put("goalName", goalName).put("goalKind", goalKind);
    }

    private static ObjectNode statusChangeMetadata(String oldStatus, String newStatus) {
        return MAPPER.createObjectNode()
                .put("oldStatus", oldStatus).put("newStatus", newStatus);
    }

    private static ObjectNode workerRetriesMetadata(String workerId) {
        return MAPPER.createObjectNode()
                .put("workerId", workerId).put("inputDataHash", "abc123");
    }

    private static ObjectNode errorMetadata(String errorMessage) {
        return MAPPER.createObjectNode()
                .put("inputDataHash", "abc123").put("errorMessage", errorMessage);
    }
}
