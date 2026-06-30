package io.casehub.aml.api.model;

/**
 * Aggregates all specialist findings for an AML investigation.
 *
 * <p>Each field wraps a specialist's output with execution status. A {@code PENDING}
 * status means the specialist hasn't executed yet. A {@code COMPLETED} status means
 * the specialist returned a result — which may still contain {@code declined=true}
 * if the agent declined due to insufficient clearance.
 *
 * @param entityResolution   entity-resolution specialist finding
 * @param patternAnalysis    pattern-analysis specialist finding
 * @param osintScreening     osint-screening specialist finding
 * @param sarNarrative       sar-drafting specialist finding
 */
public record InvestigationFindingsResponse(
        SpecialistFindingResponse entityResolution,
        SpecialistFindingResponse patternAnalysis,
        SpecialistFindingResponse osintScreening,
        SpecialistFindingResponse sarNarrative
) {}
