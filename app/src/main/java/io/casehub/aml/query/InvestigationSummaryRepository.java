package io.casehub.aml.query;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link InvestigationSummaryView}.
 * <p>
 * Provides query methods for the AML investigation list endpoint (Task 3).
 */
@ApplicationScoped
public class InvestigationSummaryRepository
        implements PanacheRepositoryBase<InvestigationSummaryView, UUID> {

    /**
     * Find an investigation summary by case ID.
     *
     * @param caseId engine case instance ID
     * @return the summary if it exists, empty otherwise
     */
    public Optional<InvestigationSummaryView> findByCaseId(UUID caseId) {
        return find("caseId", caseId).firstResultOptional();
    }

    /**
     * List investigations by status, sorted by creation time (most recent first).
     *
     * @param status investigation status (e.g. "IN_PROGRESS", "COMPLETED")
     * @param page pagination parameters
     * @return matching summaries
     */
    public List<InvestigationSummaryView> listByStatus(String status, Page page) {
        return find("status", Sort.descending("createdAt"), status).page(page).list();
    }

    /**
     * List all investigations, sorted by creation time (most recent first).
     *
     * @param page pagination parameters
     * @return all summaries
     */
    public List<InvestigationSummaryView> listAll(Page page) {
        return findAll(Sort.descending("createdAt")).page(page).list();
    }

    /**
     * Count investigations by status.
     *
     * @param status investigation status
     * @return count
     */
    public long countByStatus(String status) {
        return count("status", status);
    }
}
