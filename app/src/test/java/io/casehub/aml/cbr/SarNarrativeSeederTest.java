package io.casehub.aml.cbr;

import io.casehub.aml.domain.SeedNarrative;
import io.casehub.ledger.runtime.privacy.DecisionContextSanitiser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SarNarrativeSeederTest {

    private SarNarrativeSeeder seeder;
    private final StringBuilder sanitisedLog = new StringBuilder();

    @BeforeEach
    void setUp() {
        DecisionContextSanitiser sanitiser = input -> {
            sanitisedLog.append(input).append(";");
            return "[SANITISED] " + input;
        };
        seeder = new SarNarrativeSeeder(sanitiser);
    }

    @Test
    void extract_filtersSarWarrantedOnly() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.9, "narrative-1", "STRUCTURING", "PEP"),
                experience("FALSE_POSITIVE", 0.8, "narrative-2", "LAYERING", "INDIVIDUAL"),
                experience("INCONCLUSIVE", 0.7, "narrative-3", "SMURFING", "CORPORATE"));

        var result = seeder.extract(experiences);

        assertEquals(1, result.size());
        assertTrue(result.get(0).narrative().contains("narrative-1"));
    }

    @Test
    void extract_skipsExperiencesWithoutNarrative() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.9, null, "STRUCTURING", "PEP"),
                experience("SAR_WARRANTED", 0.8, "has-narrative", "LAYERING", "INDIVIDUAL"));

        var result = seeder.extract(experiences);

        assertEquals(1, result.size());
        assertTrue(result.get(0).narrative().contains("has-narrative"));
    }

    @Test
    void extract_excludesNonPositiveSimilarity() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.0, "zero-sim", "STRUCTURING", "PEP"),
                experience("SAR_WARRANTED", -0.2, "negative-sim", "LAYERING", "INDIVIDUAL"),
                experience("SAR_WARRANTED", 0.5, "positive-sim", "SMURFING", "CORPORATE"));

        var result = seeder.extract(experiences);

        assertEquals(1, result.size());
        assertTrue(result.get(0).narrative().contains("positive-sim"));
    }

    @Test
    void extract_sortsBySimilarityDescending() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.6, "low", "STRUCTURING", "PEP"),
                experience("SAR_WARRANTED", 0.9, "high", "LAYERING", "INDIVIDUAL"),
                experience("SAR_WARRANTED", 0.75, "mid", "SMURFING", "CORPORATE"));

        var result = seeder.extract(experiences);

        assertEquals(3, result.size());
        assertEquals(0.9, result.get(0).similarityScore(), 0.001);
        assertEquals(0.75, result.get(1).similarityScore(), 0.001);
        assertEquals(0.6, result.get(2).similarityScore(), 0.001);
    }

    @Test
    void extract_callsSanitiserOnEachNarrative() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.9, "raw-text-1", "STRUCTURING", "PEP"),
                experience("SAR_WARRANTED", 0.8, "raw-text-2", "LAYERING", "INDIVIDUAL"));

        var result = seeder.extract(experiences);

        assertEquals(2, result.size());
        assertEquals("[SANITISED] raw-text-1", result.get(0).narrative());
        assertEquals("[SANITISED] raw-text-2", result.get(1).narrative());
        assertTrue(sanitisedLog.toString().contains("raw-text-1"));
        assertTrue(sanitisedLog.toString().contains("raw-text-2"));
    }

    @Test
    void extract_handlesWrappedFeatureValueShape() {
        var features = new LinkedHashMap<String, Object>();
        features.put("sar_narrative", Map.of("type", "STRING", "value", "wrapped-narrative"));
        features.put("flag_reason", Map.of("type", "STRING", "value", "STRUCTURING"));
        features.put("entity_type", Map.of("type", "STRING", "value", "PEP"));
        var exp = new LinkedHashMap<String, Object>();
        exp.put("outcome", "SAR_WARRANTED");
        exp.put("similarityScore", 0.85);
        exp.put("features", features);

        var result = seeder.extract(List.of(exp));

        assertEquals(1, result.size());
        assertTrue(result.get(0).narrative().contains("wrapped-narrative"));
        assertEquals("STRUCTURING", result.get(0).flagReason());
        assertEquals("PEP", result.get(0).entityType());
    }

    @Test
    void extract_handlesPlainStringFeatureShape() {
        var features = new LinkedHashMap<String, Object>();
        features.put("sar_narrative", "plain-narrative");
        features.put("flag_reason", "LAYERING");
        features.put("entity_type", "INDIVIDUAL");
        var exp = new LinkedHashMap<String, Object>();
        exp.put("outcome", "SAR_WARRANTED");
        exp.put("similarityScore", 0.85);
        exp.put("features", features);

        var result = seeder.extract(List.of(exp));

        assertEquals(1, result.size());
        assertTrue(result.get(0).narrative().contains("plain-narrative"));
        assertEquals("LAYERING", result.get(0).flagReason());
        assertEquals("INDIVIDUAL", result.get(0).entityType());
    }

    @Test
    void extract_nullInput_returnsEmpty() {
        assertEquals(List.of(), seeder.extract(null));
    }

    @Test
    void extract_emptyInput_returnsEmpty() {
        assertEquals(List.of(), seeder.extract(List.of()));
    }

    @Test
    void extract_allExperiencesLackNarratives_returnsEmpty() {
        var experiences = List.of(
                experience("SAR_WARRANTED", 0.9, null, "STRUCTURING", "PEP"),
                experience("SAR_WARRANTED", 0.8, null, "LAYERING", "INDIVIDUAL"));

        assertEquals(List.of(), seeder.extract(experiences));
    }

    private static Map<String, Object> experience(String outcome, double similarity,
                                                    String narrative, String flagReason,
                                                    String entityType) {
        var features = new LinkedHashMap<String, Object>();
        if (narrative != null) features.put("sar_narrative", narrative);
        if (flagReason != null) features.put("flag_reason", flagReason);
        if (entityType != null) features.put("entity_type", entityType);

        var exp = new LinkedHashMap<String, Object>();
        exp.put("outcome", outcome);
        exp.put("similarityScore", similarity);
        exp.put("features", features);
        return exp;
    }
}
