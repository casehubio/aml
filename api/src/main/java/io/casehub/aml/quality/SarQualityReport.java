package io.casehub.aml.quality;

import java.util.List;

public record SarQualityReport(
        OutcomeSegment seeded,
        OutcomeSegment unseeded,
        List<SeedCountBucket> bySeedCount,
        int totalCases
) {}
