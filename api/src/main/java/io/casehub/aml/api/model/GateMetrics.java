package io.casehub.aml.api.model;

import java.util.Map;

/**
 * Oversight gate metrics for AML investigations.
 * Aggregated from WorkItems with callerRef pattern matching gates.
 *
 * @param totalGates Total count of gates in the query window
 * @param byActionType Count by action type (e.g., "sar.filing", "account.restriction")
 * @param byStatus Count by WorkItem status (PENDING, COMPLETED, REJECTED, etc.)
 * @param averageApprovalTimeSeconds Average approval time in seconds (null if no completed gates)
 */
public record GateMetrics(
    long totalGates,
    Map<String, Long> byActionType,
    Map<String, Long> byStatus,
    Double averageApprovalTimeSeconds
) {
}
