package io.casehub.aml.query;

import io.quarkus.panache.common.Page;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for InvestigationSummaryView JPA entity and repository.
 * <p>
 * Verifies:
 * - Entity persistence on the default datasource
 * - Unique constraint on caseId
 * - Query by status with pagination
 * - Query all with pagination
 * - Status updates
 */
@QuarkusTest
class InvestigationSummaryViewTest {

    @Inject
    InvestigationSummaryRepository repository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void persist_validEntity_succeeds() {
        // Given
        UUID caseId = UUID.randomUUID();
        InvestigationSummaryView summary = new InvestigationSummaryView(
                caseId,
                "TXN-001",
                "ACC-A",
                "ACC-B",
                new BigDecimal("10000.50"),
                "USD",
                "Structuring"
        );

        // When
        repository.persist(summary);
        em.flush();

        // Then
        Optional<InvestigationSummaryView> found = repository.findByCaseId(caseId);
        assertTrue(found.isPresent());
        assertEquals(caseId, found.get().caseId());
        assertEquals("TXN-001", found.get().transactionId());
        assertEquals("ACC-A", found.get().originAccount());
        assertEquals("ACC-B", found.get().destinationAccount());
        assertEquals(0, new BigDecimal("10000.50").compareTo(found.get().amount()));
        assertEquals("USD", found.get().currency());
        assertEquals("Structuring", found.get().flagReason());
        assertEquals("IN_PROGRESS", found.get().status());
        assertNull(found.get().outcomeType());
        assertNotNull(found.get().createdAt());
        assertNotNull(found.get().id());
    }

    @Test
    @Transactional
    void persist_duplicateCaseId_fails() {
        // Given
        UUID caseId = UUID.randomUUID();
        InvestigationSummaryView first = new InvestigationSummaryView(
                caseId,
                "TXN-001",
                "ACC-A",
                "ACC-B",
                new BigDecimal("1000"),
                "USD",
                "Smurfing"
        );
        repository.persist(first);
        em.flush();
        em.clear();

        InvestigationSummaryView duplicate = new InvestigationSummaryView(
                caseId,  // Same caseId
                "TXN-002",
                "ACC-C",
                "ACC-D",
                new BigDecimal("2000"),
                "EUR",
                "Layering"
        );

        // When/Then
        repository.persist(duplicate);
        assertThrows(Exception.class, () -> em.flush());
    }

    @Test
    @Transactional
    void listByStatus_returnsMatchingEntries() {
        // Given
        UUID case1 = UUID.randomUUID();
        UUID case2 = UUID.randomUUID();
        UUID case3 = UUID.randomUUID();

        InvestigationSummaryView inProgress1 = new InvestigationSummaryView(
                case1, "TXN-001", "ACC-A", "ACC-B", new BigDecimal("1000"), "USD", "Structuring"
        );
        InvestigationSummaryView inProgress2 = new InvestigationSummaryView(
                case2, "TXN-002", "ACC-C", "ACC-D", new BigDecimal("2000"), "EUR", "Smurfing"
        );
        InvestigationSummaryView completed = new InvestigationSummaryView(
                case3, "TXN-003", "ACC-E", "ACC-F", new BigDecimal("3000"), "GBP", "Layering"
        );
        completed.updateStatus("COMPLETED");
        completed.updateOutcomeType("SAR_FILED");

        repository.persist(inProgress1);
        repository.persist(inProgress2);
        repository.persist(completed);
        em.flush();

        // When
        List<InvestigationSummaryView> inProgressList = repository.listByStatus("IN_PROGRESS", Page.ofSize(10));
        List<InvestigationSummaryView> completedList = repository.listByStatus("COMPLETED", Page.ofSize(10));

        // Then
        assertEquals(2, inProgressList.size());
        assertEquals(1, completedList.size());
        assertEquals("SAR_FILED", completedList.get(0).outcomeType());
    }

    @Test
    @Transactional
    void listAll_returnsAllEntries_sortedByCreatedAtDesc() {
        // Given
        UUID case1 = UUID.randomUUID();
        UUID case2 = UUID.randomUUID();

        InvestigationSummaryView summary1 = new InvestigationSummaryView(
                case1, "TXN-001", "ACC-A", "ACC-B", new BigDecimal("1000"), "USD", "Structuring"
        );
        InvestigationSummaryView summary2 = new InvestigationSummaryView(
                case2, "TXN-002", "ACC-C", "ACC-D", new BigDecimal("2000"), "EUR", "Smurfing"
        );

        repository.persist(summary1);
        em.flush();
        em.clear();

        // Wait briefly to ensure createdAt differs
        try { Thread.sleep(10); } catch (InterruptedException e) { }

        repository.persist(summary2);
        em.flush();

        // When
        List<InvestigationSummaryView> all = repository.listAll(Page.ofSize(10));

        // Then
        assertEquals(2, all.size());
        // Most recent first (summary2)
        assertEquals(case2, all.get(0).caseId());
        assertEquals(case1, all.get(1).caseId());
    }

    @Test
    @Transactional
    void updateStatus_changesStatusField() {
        // Given
        UUID caseId = UUID.randomUUID();
        InvestigationSummaryView summary = new InvestigationSummaryView(
                caseId, "TXN-001", "ACC-A", "ACC-B", new BigDecimal("1000"), "USD", "Structuring"
        );
        repository.persist(summary);
        em.flush();
        em.clear();

        // When
        InvestigationSummaryView fetched = repository.findByCaseId(caseId).orElseThrow();
        fetched.updateStatus("COMPLETED");
        fetched.updateOutcomeType("SAR_DECLINED");
        em.flush();
        em.clear();

        // Then
        InvestigationSummaryView updated = repository.findByCaseId(caseId).orElseThrow();
        assertEquals("COMPLETED", updated.status());
        assertEquals("SAR_DECLINED", updated.outcomeType());
    }

    @Test
    @Transactional
    void countByStatus_returnsCorrectCount() {
        // Given
        InvestigationSummaryView inProgress = new InvestigationSummaryView(
                UUID.randomUUID(), "TXN-001", "ACC-A", "ACC-B", new BigDecimal("1000"), "USD", "Structuring"
        );
        InvestigationSummaryView completed = new InvestigationSummaryView(
                UUID.randomUUID(), "TXN-002", "ACC-C", "ACC-D", new BigDecimal("2000"), "EUR", "Smurfing"
        );
        completed.updateStatus("COMPLETED");

        repository.persist(inProgress);
        repository.persist(completed);
        em.flush();

        // When/Then
        assertEquals(1, repository.countByStatus("IN_PROGRESS"));
        assertEquals(1, repository.countByStatus("COMPLETED"));
        assertEquals(0, repository.countByStatus("CANCELLED"));
    }
}
