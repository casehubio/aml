package io.casehub.aml.trust;

import io.casehub.aml.domain.TrustScoreSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * JPA repository for {@link TrustScoreSnapshot} entities.
 *
 * <p>Uses the default persistence unit (not qhorus) — trust score snapshots
 * are an AML domain concern, not ledger entries.
 */
@ApplicationScoped
public class TrustScoreSnapshotRepository {

    @Inject
    EntityManager em;

    @Transactional
    public void saveAll(List<TrustScoreSnapshot> snapshots) {
        for (TrustScoreSnapshot s : snapshots) {
            em.persist(s);
        }
    }

    /**
     * Find all snapshots for an agent/capability pair, ordered by timestamp ascending.
     */
    public List<TrustScoreSnapshot> findByAgentAndCapability(String agentId, String capability) {
        return em.createQuery(
                "SELECT s FROM TrustScoreSnapshot s" +
                " WHERE s.agentId = :agentId AND s.capability = :capability" +
                " ORDER BY s.snapshotTimestamp ASC",
                TrustScoreSnapshot.class)
            .setParameter("agentId", agentId)
            .setParameter("capability", capability)
            .getResultList();
    }
}
