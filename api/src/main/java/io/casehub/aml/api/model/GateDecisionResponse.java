package io.casehub.aml.api.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Details of a single oversight gate decision.
 * Each gate represents a {@code PlannedAction} that requires approval before
 * the engine can execute it.
 *
 * @param workItemId WorkItem ID for this gate (used for approval/rejection)
 * @param actionType Action type string from {@code PlannedAction.actionType()}
 *                   (e.g., "sar.filing", "account.restriction")
 * @param gatePolicy Gate policy name (ALWAYS, RISK_SCORE_THRESHOLD, CONFIDENCE_THRESHOLD)
 * @param reversible Whether the action can be reversed after execution
 * @param description Human-readable description of the action
 * @param candidateGroups Approver groups (e.g., ["aml-mlro"], ["aml-compliance"])
 * @param status WorkItem status (PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, REJECTED, etc.)
 * @param approvedBy Actor who approved/rejected the gate (null if still pending)
 * @param approvedAt Timestamp of approval/rejection (null if still pending)
 * @param expiresAt Gate expiry deadline (null if no expiry)
 */
public record GateDecisionResponse(
    UUID workItemId,
    String actionType,
    String gatePolicy,
    boolean reversible,
    String description,
    List<String> candidateGroups,
    String status,
    String approvedBy,
    Instant approvedAt,
    Instant expiresAt
) {
}
