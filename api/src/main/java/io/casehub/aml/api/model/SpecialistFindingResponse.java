package io.casehub.aml.api.model;

/**
 * Wraps a specialist finding with execution status.
 *
 * <p><strong>Status values:</strong>
 * <ul>
 *   <li>{@code COMPLETED} — specialist executed and returned a result (may still contain
 *       {@code declined=true} in the result Map if the agent declined due to clearance)</li>
 *   <li>{@code PENDING} — specialist has not executed yet (result is null)</li>
 * </ul>
 *
 * <p><strong>Result structure:</strong> The {@code result} field contains the raw Map written
 * by the worker to the CaseHub context. Each specialist writes different keys:
 * <ul>
 *   <li>entity-resolution: {@code entityId, ownershipChain, entityType, riskScore}</li>
 *   <li>pattern-analysis: {@code structuringDetected, description}</li>
 *   <li>osint-screening: {@code declined, reason, pepHit, sanctionsHit, screeningLevel}</li>
 *   <li>sar-drafting: {@code sarNarrative}</li>
 * </ul>
 *
 * @param status    execution status — "COMPLETED" | "PENDING"
 * @param result    the data Map written by the worker (null when status is PENDING)
 */
public record SpecialistFindingResponse(
        String status,
        Object result
) {}
