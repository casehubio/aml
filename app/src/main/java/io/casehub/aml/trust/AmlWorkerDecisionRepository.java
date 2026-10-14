package io.casehub.aml.trust;

import io.casehub.ledger.model.WorkerDecisionEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmlWorkerDecisionRepository {

    @PersistenceContext
    EntityManager em;

    /**
     * Returns the latest {@link WorkerDecisionEntry} for a case and capability by sequenceNumber.
     *
     * <p>Ordering by sequenceNumber assumes the highest sequence number is the most recent
     * execution. For the tutorial (one worker selected per capability per case, no retries),
     * this is always correct. In a production extension with retry paths, a failed attempt
     * could have a higher sequence number than the successful one — add a status filter if needed.
     */
    @Transactional(TxType.SUPPORTS)
    public Optional<WorkerDecisionEntry> findLatestByCaseIdAndCapability(
            final UUID caseId, final String capabilityTag) {
        return em.createQuery(
                "SELECT w FROM WorkerDecisionEntry w" +
                " WHERE w.caseId = :caseId AND w.capabilityTag = :cap" +
                " ORDER BY w.sequenceNumber DESC",
                WorkerDecisionEntry.class)
                .setParameter("caseId", caseId)
                .setParameter("cap", capabilityTag)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Transactional(TxType.SUPPORTS)
    public List<WorkerDecisionEntry> findAllByCaseId(final UUID caseId) {
        return em.createQuery(
                "SELECT w FROM WorkerDecisionEntry w WHERE w.caseId = :caseId ORDER BY w.sequenceNumber ASC",
                WorkerDecisionEntry.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }
}
