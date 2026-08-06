package io.casehub.aml.quality;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutcomeSegmentTest {

    @Test
    void zeroTotalYieldsZeroRate() {
        var segment = OutcomeSegment.of(0, 0);
        assertEquals(0, segment.total());
        assertEquals(0.0, segment.upheldRate());
    }

    @Test
    void normalComputation() {
        var segment = OutcomeSegment.of(8, 2);
        assertEquals(10, segment.total());
        assertEquals(8, segment.upheld());
        assertEquals(2, segment.notUpheld());
        assertEquals(0.8, segment.upheldRate(), 0.001);
    }

    @Test
    void allUpheld() {
        var segment = OutcomeSegment.of(5, 0);
        assertEquals(1.0, segment.upheldRate());
    }
}
