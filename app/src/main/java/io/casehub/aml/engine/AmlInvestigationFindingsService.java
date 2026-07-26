package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.SpecialistFindingResponse;
import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

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

    public InvestigationFindingsResponse getFindings(UUID caseId) {
        Object entityRes  = safeQuery(caseId, "entityResolution");
        Object patternRes = safeQuery(caseId, "patternAnalysis");
        Object osintRes   = safeQuery(caseId, "osintScreening");
        Object sarRes     = safeQuery(caseId, "sarNarrative");

        return new InvestigationFindingsResponse(
                toFindingResponse(entityRes),
                toFindingResponse(patternRes),
                toFindingResponse(osintRes),
                toFindingResponse(sarRes));
    }

    private Object safeQuery(UUID caseId, String key) {
        try {
            return caseHubRuntime.query(caseId, key);
        } catch (Exception e) {
            return null;
        }
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
