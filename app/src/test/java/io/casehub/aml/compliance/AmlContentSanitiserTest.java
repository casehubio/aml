package io.casehub.aml.compliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmlContentSanitiserTest {

    private final AmlContentSanitiser sanitiser = new AmlContentSanitiser();

    @Test
    void sanitise_redactsIban() {
        String input = "account: GB29NWBK60161331926819";
        assertEquals("account: [REDACTED:account]", sanitiser.sanitise(input));
    }

    @Test
    void sanitise_preservesNumericStrings() {
        String input = "{\"priorIncidentCount\":12345678901234}";
        assertEquals(input, sanitiser.sanitise(input));
    }

    @Test
    void sanitise_preservesShortNumbers() {
        assertEquals("{\"count\": 3}", sanitiser.sanitise("{\"count\": 3}"));
    }

    @Test
    void sanitise_nullReturnsNull() {
        assertNull(sanitiser.sanitise(null));
    }

    @Test
    void sanitise_noPiiUnchanged() {
        String input = "{\"flagReason\":\"HIGH_RISK_JURISDICTION\"}";
        assertEquals(input, sanitiser.sanitise(input));
    }

    @Test
    void sanitise_multipleIbans() {
        String input = "from GB29NWBK60161331926819 to DE89370400440532013000";
        assertEquals("from [REDACTED:account] to [REDACTED:account]", sanitiser.sanitise(input));
    }
}
