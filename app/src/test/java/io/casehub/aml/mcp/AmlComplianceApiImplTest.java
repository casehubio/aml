package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.SarPipelineStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmlComplianceApiImplTest {

    @Test
    void sarPipelineStatus_isRecord() {
        SarPipelineStatus status = new SarPipelineStatus(3, 1, 86400000L, 0);
        assertEquals(3, status.pendingReviews());
        assertEquals(1, status.pendingGates());
        assertEquals(86400000L, status.averageReviewAgeMs());
        assertEquals(0, status.slaBreaches());
    }
}
