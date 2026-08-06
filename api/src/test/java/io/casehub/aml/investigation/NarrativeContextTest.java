package io.casehub.aml.investigation;

import io.casehub.aml.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NarrativeContextTest {

    private static final SuspiciousTransaction TX = new SuspiciousTransaction(
            "TX-001", "ACC-001", "ACC-002", BigDecimal.valueOf(50000), "USD", Instant.now(), FlagReason.STRUCTURING);

    @Test
    void nullTransactionThrows() {
        assertThrows(NullPointerException.class,
                () -> new NarrativeContext(null, null, null, null, List.of()));
    }

    @Test
    void nullSeedsNormalisedToEmptyList() {
        var ctx = new NarrativeContext(TX, null, null, null, null);
        assertNotNull(ctx.seeds());
        assertTrue(ctx.seeds().isEmpty());
    }

    @Test
    void validConstruction() {
        var seed = new SeedNarrative("narrative text", 0.85, "STRUCTURING", "CORPORATE");
        var ctx = new NarrativeContext(TX, null, null, null, List.of(seed));
        assertEquals(TX, ctx.transaction());
        assertEquals(1, ctx.seeds().size());
    }
}
