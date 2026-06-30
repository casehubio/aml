package io.casehub.aml.query;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InvestigationSummaryService}.
 * <p>
 * These are unit tests for the service layer — they verify createSummary, updateStatus,
 * and updateOutcome methods commit independently and produce the expected row state.
 */
@QuarkusTest
class InvestigationSummaryServiceTest {

    @Inject InvestigationSummaryService service;
    @Inject InvestigationSummaryRepository repository;
    @Inject EntityManager em;

    @Test
    @Transactional
    void createSummary_persistsRowWithStatusInProgress() {
        UUID caseId = UUID.randomUUID();
        SuspiciousTransaction txn = new SuspiciousTransaction(
            "TX-001", "ACC-001", "ACC-002",
            new BigDecimal("15000.00"), "USD", Instant.now(), "high-velocity"
        );

        service.createSummary(caseId, txn);
        em.flush();
        em.clear();

        InvestigationSummaryView summary = repository.findByCaseId(caseId).orElseThrow();
        assertEquals(caseId, summary.caseId());
        assertEquals("IN_PROGRESS", summary.status());
        assertNull(summary.outcomeType());
        assertEquals("TX-001", summary.transactionId());
        assertEquals("ACC-001", summary.originAccount());
        assertEquals("ACC-002", summary.destinationAccount());
        assertEquals(0, new BigDecimal("15000.00").compareTo(summary.amount()));
        assertEquals("USD", summary.currency());
        assertEquals("high-velocity", summary.flagReason());
        assertNotNull(summary.createdAt());
    }

    @Test
    @Transactional
    void updateStatus_changesStatusField() {
        UUID caseId = UUID.randomUUID();
        SuspiciousTransaction txn = new SuspiciousTransaction(
            "TX-002", "ACC-003", "ACC-004",
            new BigDecimal("5000.00"), "USD", Instant.now(), "structuring"
        );
        service.createSummary(caseId, txn);
        em.flush();
        em.clear();

        service.updateStatus(caseId, "COMPLETED");
        em.flush();
        em.clear();

        InvestigationSummaryView summary = repository.findByCaseId(caseId).orElseThrow();
        assertEquals("COMPLETED", summary.status());
    }

    @Test
    @Transactional
    void updateStatus_doesNothingWhenCaseNotFound() {
        UUID unknownCaseId = UUID.randomUUID();

        // Should not throw — just a no-op
        service.updateStatus(unknownCaseId, "COMPLETED");
    }

    @Test
    @Transactional
    void updateOutcome_setsOutcomeType() {
        UUID caseId = UUID.randomUUID();
        SuspiciousTransaction txn = new SuspiciousTransaction(
            "TX-003", "ACC-005", "ACC-006",
            new BigDecimal("25000.00"), "USD", Instant.now(), "round-amount"
        );
        service.createSummary(caseId, txn);
        em.flush();
        em.clear();

        service.updateOutcome(caseId, "SAR_FILED");
        em.flush();
        em.clear();

        InvestigationSummaryView summary = repository.findByCaseId(caseId).orElseThrow();
        assertEquals("SAR_FILED", summary.outcomeType());
    }

    @Test
    @Transactional
    void updateOutcome_doesNothingWhenCaseNotFound() {
        UUID unknownCaseId = UUID.randomUUID();

        // Should not throw — just a no-op
        service.updateOutcome(unknownCaseId, "SAR_DECLINED");
    }

    @Test
    @Transactional
    void updateStatus_thenUpdateOutcome_bothChangesVisible() {
        UUID caseId = UUID.randomUUID();
        SuspiciousTransaction txn = new SuspiciousTransaction(
            "TX-004", "ACC-007", "ACC-008",
            new BigDecimal("100000.00"), "USD", Instant.now(), "pep-involvement"
        );
        service.createSummary(caseId, txn);
        em.flush();
        em.clear();

        service.updateStatus(caseId, "COMPLETED");
        service.updateOutcome(caseId, "ESCALATED");
        em.flush();
        em.clear();

        InvestigationSummaryView summary = repository.findByCaseId(caseId).orElseThrow();
        assertEquals("COMPLETED", summary.status());
        assertEquals("ESCALATED", summary.outcomeType());
    }
}
