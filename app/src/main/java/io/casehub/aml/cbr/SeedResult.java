package io.casehub.aml.cbr;

import java.util.Map;

public record SeedResult(int seeded, Map<String, Integer> flagReasonCoverage,
                          Map<String, Integer> entityTypeCoverage,
                          Map<String, Integer> outcomeCoverage) {}
