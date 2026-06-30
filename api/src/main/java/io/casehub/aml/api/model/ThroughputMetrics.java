package io.casehub.aml.api.model;

import java.util.Map;

/**
 * Throughput metrics for AML investigations.
 * Aggregated from {@code InvestigationSummaryView} by status, flag reason, and outcome.
 *
 * @param totalInvestigations Total count of investigations in the query window
 * @param byStatus Count by investigation status (IN_PROGRESS, COMPLETED, CANCELLED)
 * @param byFlagReason Count by flag reason (e.g., "high-risk-jurisdiction", "velocity-anomaly")
 * @param byOutcomeType Count by outcome type (SAR_FILED, SAR_DECLINED, ESCALATED)
 */
public record ThroughputMetrics(
    long totalInvestigations,
    Map<String, Long> byStatus,
    Map<String, Long> byFlagReason,
    Map<String, Long> byOutcomeType
) {
}
