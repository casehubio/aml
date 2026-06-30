package io.casehub.aml.engine;

import io.casehub.aml.domain.FailureContext;
import io.casehub.aml.domain.FailureEvent;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmlInvestigationOutcomeService {

    private static final Comparator<AmlSarOfficerReviewedLedgerEntry> HUMAN_FIRST_LATEST_SEQ =
            Comparator.<AmlSarOfficerReviewedLedgerEntry, Integer>comparing(
                    e -> e.actorType == ActorType.HUMAN ? 0 : 1)
            .thenComparing(e -> e.sequenceNumber, Comparator.reverseOrder());

    private final LedgerEntryRepository ledgerEntryRepository;
    private final CaseInstanceCache caseInstanceCache;
    private final CaseInstanceRepository caseInstanceRepository;
    private final EventLogRepository eventLogRepository;

    @Inject
    public AmlInvestigationOutcomeService(
            final LedgerEntryRepository ledgerEntryRepository,
            final CaseInstanceCache caseInstanceCache,
            final CaseInstanceRepository caseInstanceRepository,
            final EventLogRepository eventLogRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.caseInstanceCache = caseInstanceCache;
        this.caseInstanceRepository = caseInstanceRepository;
        this.eventLogRepository = eventLogRepository;
    }

    public Optional<InvestigationResolution> resolveInvestigation(final UUID caseId) {
        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            instance = caseInstanceRepository
                    .findByUuid(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                    .await().indefinitely();
        }
        if (instance == null) {
            return Optional.empty();
        }

        InvestigationStatus status = switch (instance.getState()) {
            case STARTING, RUNNING, WAITING -> InvestigationStatus.IN_PROGRESS;
            case COMPLETED -> InvestigationStatus.COMPLETED;
            case FAULTED -> InvestigationStatus.FAILED;
            case CANCELLED -> InvestigationStatus.CANCELLED;
            case SUSPENDED -> InvestigationStatus.SUSPENDED;
        };

        return switch (status) {
            case COMPLETED -> Optional.of(new InvestigationResolution(
                    status, resolveOutcome(caseId), null));
            case FAILED, CANCELLED -> Optional.of(new InvestigationResolution(
                    status, null, resolveFailureContext(caseId)));
            case IN_PROGRESS, SUSPENDED -> Optional.of(new InvestigationResolution(
                    status, null, null));
        };
    }

    public InvestigationOutcome resolveOutcome(final UUID caseId) {
        return ledgerEntryRepository
                .findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID).stream()
                .filter(AmlSarOfficerReviewedLedgerEntry.class::isInstance)
                .map(AmlSarOfficerReviewedLedgerEntry.class::cast)
                .min(HUMAN_FIRST_LATEST_SEQ)
                .map(e -> InvestigationOutcome.fromReviewDecision(e.reviewDecision, e.rejectionReason))
                .orElse(null);
    }

    FailureContext resolveFailureContext(final UUID caseId) {
        final List<EventLog> events = eventLogRepository.findByCaseAndTypes(
                caseId,
                List.of(CaseHubEventType.CASE_FAULTED, CaseHubEventType.CASE_CANCELLED,
                        CaseHubEventType.WORKER_EXECUTION_FAILED,
                        CaseHubEventType.WORKER_OUTCOME_FAILED,
                        CaseHubEventType.ACTION_GATE_REJECTED,
                        CaseHubEventType.ACTION_GATE_EXPIRED),
                TenancyConstants.DEFAULT_TENANT_ID)
                .await().indefinitely();

        String triggerGoalName = null;
        String triggerGoalKind = null;
        Instant occurredAt = null;

        for (final EventLog e : events) {
            if (e.getEventType() == CaseHubEventType.CASE_FAULTED
                    || e.getEventType() == CaseHubEventType.CASE_CANCELLED) {
                if (occurredAt == null || e.getTimestamp().isBefore(occurredAt)) {
                    occurredAt = e.getTimestamp();
                }
                if (triggerGoalName == null && e.getMetadata() != null
                        && e.getMetadata().has("goalName")) {
                    triggerGoalName = e.getMetadata().get("goalName").asText();
                    triggerGoalKind = e.getMetadata().has("goalKind")
                            ? e.getMetadata().get("goalKind").asText() : null;
                }
            }
        }

        final List<FailureEvent> failureEvents = events.stream()
                .filter(e -> e.getEventType() != CaseHubEventType.CASE_FAULTED
                        && e.getEventType() != CaseHubEventType.CASE_CANCELLED)
                .sorted(Comparator.comparing(EventLog::getTimestamp))
                .map(e -> new FailureEvent(
                        e.getEventType().name(),
                        e.getWorkerId(),
                        e.getTimestamp(),
                        extractDetail(e)))
                .toList();

        return new FailureContext(triggerGoalName, triggerGoalKind, failureEvents,
                occurredAt != null ? occurredAt : Instant.now());
    }

    private String extractDetail(final EventLog e) {
        if (e.getMetadata() == null) return null;
        if (e.getMetadata().has("errorMessage")) {
            return e.getMetadata().get("errorMessage").asText();
        }
        if (e.getMetadata().has("reason")) {
            return e.getMetadata().get("reason").asText();
        }
        return null;
    }
}
