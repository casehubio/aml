package io.casehub.aml.investigation;

import io.casehub.aml.domain.*;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.ledger.runtime.privacy.PassThroughContentSanitiser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EidosSarNarrativeServiceTest {

    @Mock PreferenceProvider preferenceProvider;
    @Mock Preferences preferences;

    private TemplateSarNarrativeService templateService;
    private EidosSarNarrativeService eidosService;

    private static final SuspiciousTransaction TX = new SuspiciousTransaction(
            "TX-001", "ACC-001", "ACC-002", BigDecimal.valueOf(50000), "USD",
            Instant.parse("2026-01-15T10:00:00Z"), FlagReason.HIGH_RISK_JURISDICTION);

    @BeforeEach
    void setUp() {
        when(preferenceProvider.resolve(any())).thenReturn(preferences);
        templateService = new TemplateSarNarrativeService(preferenceProvider);
        eidosService = new EidosSarNarrativeService(templateService, new PassThroughContentSanitiser());
    }

    @Test
    void callEidos_stub_returnsLlmAdaptationMethod() {
        var ctx = new NarrativeContext(TX, null, null, null, List.of());
        var result = eidosService.callEidos(ctx);
        assertNotNull(result.narrative());
        assertEquals(AdaptationMethod.LLM, result.adaptationMethod());
    }

    @Test
    void draftDeterministic_returnsFallbackMethod() {
        var ctx = new NarrativeContext(TX, null, null, null, List.of());
        var result = eidosService.draftDeterministic(ctx);
        assertNotNull(result.narrative());
        assertEquals(AdaptationMethod.LLM_FALLBACK_DETERMINISTIC, result.adaptationMethod());
    }

    @Test
    void fallbackOnEidosFailure_returnsDeterministic() {
        var ctx = new NarrativeContext(TX, null, null, null, List.of());
        var result = templateService.draft(ctx);
        assertNotNull(result.narrative());
        assertEquals(AdaptationMethod.DETERMINISTIC, result.adaptationMethod());
    }
}
