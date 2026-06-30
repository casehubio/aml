package io.casehub.aml.api.model;

import java.util.List;

/**
 * Response containing all gate decisions for an AML investigation.
 * Gates are oversight checkpoints where consequential actions (SAR filing,
 * account restriction, etc.) require human approval before the engine proceeds.
 *
 * @param gates List of gate decisions, ordered by creation time (oldest first)
 */
public record InvestigationGatesResponse(
    List<GateDecisionResponse> gates
) {
}
