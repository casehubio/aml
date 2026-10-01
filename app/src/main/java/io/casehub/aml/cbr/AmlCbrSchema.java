package io.casehub.aml.cbr;

import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;

import java.util.Map;

public final class AmlCbrSchema {

    public static final String CASE_TYPE = "aml-investigation";

    public static final MemoryDomain DOMAIN = AmlMemoryDomains.CBR;

    public static final CbrRecordSchema SCHEMA = CbrRecordSchema.of(CASE_TYPE,
                                                                      new FeatureField.Categorical("flag_reason",
                                                                                                   new SimilaritySpec.CategoricalTable(Map.of(
                                                                                                           "STRUCTURING", Map.of("SMURFING", 0.7, "LAYERING", 0.4, "ROUND_TRIP", 0.5),
                                                                                                           "SMURFING", Map.of("LAYERING", 0.3, "ROUND_TRIP", 0.3),
                                                                                                           "LAYERING", Map.of("ROUND_TRIP", 0.5),
                                                                                                           "VELOCITY_ANOMALY", Map.of("LARGE_VOLUME", 0.6),
                                                                                                           "PEP_MATCH", Map.of("HIGH_RISK_JURISDICTION", 0.3)))),
                                                                      new FeatureField.Categorical("entity_type",
                                                                                                   new SimilaritySpec.CategoricalTable(Map.of(
                                                                                                           "SHELL_COMPANY", Map.of("CORPORATE", 0.4, "PEP", 0.2),
                                                                                                           "PEP", Map.of("INDIVIDUAL", 0.3, "CORPORATE", 0.1)))),
                                                                      new FeatureField.Numeric("transaction_amount", 0, 10_000_000,
                                                                                               new SimilaritySpec.GaussianDecay(0.15)),
                                                                      new FeatureField.Categorical("jurisdiction_risk",
                                                                                                   new SimilaritySpec.CategoricalTable(Map.of(
                                                                                                           "HIGH", Map.of("MEDIUM", 0.5, "LOW", 0.2),
                                                                                                           "MEDIUM", Map.of("LOW", 0.5)))),
                                                                      new FeatureField.Numeric("prior_incident_count", 0, 20,
                                                                                               new SimilaritySpec.GaussianDecay(0.3)),
                                                                      new FeatureField.Categorical("network_complexity",
                                                                                                   new SimilaritySpec.CategoricalTable(Map.of(
                                                                                                           "SINGLE_ENTITY", Map.of("SMALL_NETWORK", 0.3, "LARGE_NETWORK", 0.1),
                                                                                                           "SMALL_NETWORK", Map.of("LARGE_NETWORK", 0.5)))),
                                                                      FeatureField.semanticText("sar_narrative"));

    public static final Map<String, Double> WEIGHTS = Map.of(
            "flag_reason", 0.30,
            "entity_type", 0.20,
            "transaction_amount", 0.15,
            "jurisdiction_risk", 0.15,
            "prior_incident_count", 0.10,
            "network_complexity", 0.10);

    private AmlCbrSchema() {}
}
