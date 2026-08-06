package io.casehub.aml.quality;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SarQualityReportTest {

    @Test
    void totalCasesEqualsSumOfSegments() {
        var seeded = OutcomeSegment.of(8, 2);
        var unseeded = OutcomeSegment.of(3, 2);
        var report = new SarQualityReport(seeded, unseeded, List.of(), seeded.total() + unseeded.total());
        assertEquals(15, report.totalCases());
        assertEquals(seeded.total() + unseeded.total(), report.totalCases());
    }
}
