package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.SpecialistFindingResponse;
import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Assembles specialist findings from the CaseHub context for API exposure.
 *
 * <p>Each specialist worker writes its output to the case context under a specific key:
 * <ul>
 *   <li>{@code entityResolution} — entity-resolution worker</li>
 *   <li>{@code patternAnalysis} — pattern-analysis worker (inferred from code pattern)</li>
 *   <li>{@code osintScreening} — osint-screening worker (inferred from code pattern)</li>
 *   <li>{@code sarNarrative} — sar-drafting worker</li>
 * </ul>
 *
 * <p>A null query result means the specialist hasn't executed yet — returned as
 * {@code { status: "PENDING" }}.
 */
@ApplicationScoped
public class AmlInvestigationFindingsService {

    @Inject
    CaseHubRuntime caseHubRuntime;

    /**
     * Fetches all specialist findings for an investigation.
     *
     * @param caseId the investigation case ID
     * @return findings response with all specialist outcomes
     */
    public CompletionStage<InvestigationFindingsResponse> getFindings(UUID caseId) {
        // Query all context keys in parallel
        // If the case doesn't exist, query() throws RuntimeException — catch and return all PENDING
        CompletionStage<Object> entityResolutionCS = caseHubRuntime.query(caseId, "entityResolution")
                .exceptionally(t -> null);
        CompletionStage<Object> patternAnalysisCS = caseHubRuntime.query(caseId, "patternAnalysis")
                .exceptionally(t -> null);
        CompletionStage<Object> osintScreeningCS = caseHubRuntime.query(caseId, "osintScreening")
                .exceptionally(t -> null);
        CompletionStage<Object> sarNarrativeCS = caseHubRuntime.query(caseId, "sarNarrative")
                .exceptionally(t -> null);

        // Combine all results
        return entityResolutionCS.thenCombine(patternAnalysisCS, (entityRes, patternRes) ->
                new Object[]{entityRes, patternRes})
                .thenCombine(osintScreeningCS, (partial, osintRes) ->
                        new Object[]{partial[0], partial[1], osintRes})
                .thenCombine(sarNarrativeCS, (partial, sarRes) ->
                        new InvestigationFindingsResponse(
                                toFindingResponse(partial[0]),
                                toFindingResponse(partial[1]),
                                toFindingResponse(partial[2]),
                                toFindingResponse(sarRes)
                        ));
    }

    /**
     * Converts a context query result to a specialist finding response.
     *
     * @param contextResult the result from {@code CaseHubRuntime.query()} (null if not executed)
     * @return finding response with status and result
     */
    private SpecialistFindingResponse toFindingResponse(Object contextResult) {
        if (contextResult == null) {
            return new SpecialistFindingResponse("PENDING", null);
        }
        return new SpecialistFindingResponse("COMPLETED", contextResult);
    }
}
