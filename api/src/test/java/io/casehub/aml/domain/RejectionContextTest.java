package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RejectionContextTest {

    @Test
    void valid_construction() {
        var ctx = new RejectionContext("sar.filing", "sar-drafting-agent-senior", "test-mlro", "Insufficient evidence");
        assertEquals("sar.filing", ctx.actionType());
        assertEquals("sar-drafting-agent-senior", ctx.workerId());
        assertEquals("test-mlro", ctx.rejectedBy());
        assertEquals("Insufficient evidence", ctx.resolution());
    }

    @Test
    void null_resolution_allowed() {
        var ctx = new RejectionContext("sar.filing", "worker-1", "mlro", null);
        assertNull(ctx.resolution());
    }

    @Test
    void null_workerId_allowed() {
        var ctx = new RejectionContext("investigation.clearance", null, "officer", "reason");
        assertNull(ctx.workerId());
    }
}
