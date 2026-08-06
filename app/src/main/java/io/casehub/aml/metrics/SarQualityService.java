package io.casehub.aml.metrics;

import io.casehub.aml.quality.*;
import io.casehub.ledger.api.model.AttestationVerdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;

@ApplicationScoped
public class SarQualityService {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    public SarQualityReport generateReport() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
                "SELECT p.narrativeSeeded, a.verdict, p.seedCount, COUNT(p) " +
                "FROM AmlCaseProfileLedgerEntry p, LedgerAttestation a " +
                "WHERE p.subjectId = a.subjectId " +
                "AND p.outcome = 'SAR_WARRANTED' " +
                "AND a.capabilityTag = 'sar-drafting' " +
                "AND a.trustDimension = 'investigation-accuracy' " +
                "AND a.occurredAt = (" +
                "  SELECT MAX(a2.occurredAt) FROM LedgerAttestation a2 " +
                "  WHERE a2.subjectId = a.subjectId " +
                "  AND a2.capabilityTag = a.capabilityTag " +
                "  AND a2.trustDimension = a.trustDimension" +
                ") " +
                "GROUP BY p.narrativeSeeded, a.verdict, p.seedCount"
        ).getResultList();

        int seededUpheld = 0, seededNotUpheld = 0;
        int unseededUpheld = 0, unseededNotUpheld = 0;
        Map<String, int[]> seedCountBuckets = new LinkedHashMap<>();

        for (Object[] row : rows) {
            boolean seeded = Boolean.TRUE.equals(row[0]);
            boolean upheld = row[1] == AttestationVerdict.SOUND;
            Integer seedCount = row[2] instanceof Number n ? n.intValue() : 0;
            long count = (Long) row[3];

            if (seeded) {
                if (upheld) seededUpheld += (int) count;
                else seededNotUpheld += (int) count;

                String bucket = seedCount >= 3 ? "3+" : String.valueOf(seedCount);
                seedCountBuckets.computeIfAbsent(bucket, k -> new int[2]);
                if (upheld) seedCountBuckets.get(bucket)[0] += (int) count;
                else seedCountBuckets.get(bucket)[1] += (int) count;
            } else {
                if (upheld) unseededUpheld += (int) count;
                else unseededNotUpheld += (int) count;
            }
        }

        var seededSegment = OutcomeSegment.of(seededUpheld, seededNotUpheld);
        var unseededSegment = OutcomeSegment.of(unseededUpheld, unseededNotUpheld);

        List<SeedCountBucket> buckets = new ArrayList<>();
        for (var entry : seedCountBuckets.entrySet()) {
            int total = entry.getValue()[0] + entry.getValue()[1];
            double rate = total > 0 ? (double) entry.getValue()[0] / total : 0.0;
            buckets.add(new SeedCountBucket(entry.getKey(), total, rate));
        }

        return new SarQualityReport(seededSegment, unseededSegment, buckets,
                seededSegment.total() + unseededSegment.total());
    }
}
