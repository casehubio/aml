package io.casehub.aml.query;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class InvestigationSummaryRepository {

    @Inject EntityManager em;

    public Optional<InvestigationSummaryView> findByCaseId(UUID caseId) {
        return em.createNamedQuery("InvestigationSummaryView.findByCaseId", InvestigationSummaryView.class)
                .setParameter("caseId", caseId)
                .getResultStream().findFirst();
    }

    public List<InvestigationSummaryView> listByStatus(String status, int offset, int limit) {
        return em.createNamedQuery("InvestigationSummaryView.listByStatus", InvestigationSummaryView.class)
                .setParameter("status", status)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<InvestigationSummaryView> listAll(int offset, int limit) {
        return em.createNamedQuery("InvestigationSummaryView.listAll", InvestigationSummaryView.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public long countByStatus(String status) {
        return em.createNamedQuery("InvestigationSummaryView.countByStatus", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    public long count() {
        return em.createNamedQuery("InvestigationSummaryView.count", Long.class)
                .getSingleResult();
    }

    public void persist(InvestigationSummaryView view) {
        em.persist(view);
    }
}
