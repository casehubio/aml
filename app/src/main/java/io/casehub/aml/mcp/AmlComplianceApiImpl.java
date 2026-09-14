package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.AmlComplianceApi;
import io.casehub.aml.api.mcp.SarPipelineStatus;
import io.casehub.work.api.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AmlComplianceApiImpl implements AmlComplianceApi {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public SarPipelineStatus getSarPipeline() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
                "SELECT w.status, w.createdAt, w.expiresAt FROM WorkItemEntity w " +
                "WHERE w.scope = :scope AND w.status IN :statuses")
                .setParameter("scope", "casehubio/aml/oversight")
                .setParameter("statuses", List.of(WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED))
                .getResultList();

        int pendingReviews = 0;
        int pendingGates = 0;
        long totalAgeMs = 0;
        int slaBreaches = 0;
        Instant now = Instant.now();

        for (Object[] row : rows) {
            WorkItemStatus status = (WorkItemStatus) row[0];
            Instant createdAt = (Instant) row[1];
            Instant expiresAt = (Instant) row[2];

            if (status == WorkItemStatus.PENDING) {
                pendingReviews++;
            } else {
                pendingGates++;
            }
            totalAgeMs += Duration.between(createdAt, now).toMillis();
            if (expiresAt != null && now.isAfter(expiresAt)) {
                slaBreaches++;
            }
        }

        long avgAge = (pendingReviews + pendingGates) > 0
                ? totalAgeMs / (pendingReviews + pendingGates) : 0;
        return new SarPipelineStatus(pendingReviews, pendingGates, avgAge, slaBreaches);
    }
}
