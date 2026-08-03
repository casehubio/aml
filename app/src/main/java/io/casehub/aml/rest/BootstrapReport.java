package io.casehub.aml.rest;

import java.util.Map;

public record BootstrapReport(
        CaseBaseSummary caseBase,
        AdvisoryMetrics advisoryMetrics) {

    public record CaseBaseSummary(
            long totalCases,
            int activationThreshold,
            Map<String, Long> byFlagReason,
            Map<String, Long> byEntityType,
            Map<String, Long> byJurisdictionRisk,
            Map<String, Long> byOutcome) {}

    public record AdvisoryMetrics(
            long totalAdvisories,
            long activeAdvisories,
            long learningAdvisories,
            double avgConfidence,
            double avgCaseCount) {}
}
