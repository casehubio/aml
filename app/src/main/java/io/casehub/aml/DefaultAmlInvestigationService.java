package io.casehub.aml;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class DefaultAmlInvestigationService implements AmlInvestigator {

    private final DefaultEntityResolutionService entityResolutionService = new DefaultEntityResolutionService();
    private final DefaultPatternAnalysisService  patternAnalysisService  = new DefaultPatternAnalysisService();
    private final DefaultOsintScreeningService   osintScreeningService   = new DefaultOsintScreeningService();

    private static String draftNarrative(SuspiciousTransaction tx) {
        return "SAR narrative for transaction " + tx.id() + ". Amount: " + tx.amount() + " " + tx.currency() + ". Flag reason: " + tx.flagReason() + ".";
    }

    @Override
    public InvestigationSummary investigate(SuspiciousTransaction transaction, java.util.UUID caseId) {
        EntityResolutionResult         entityResult  = entityResolutionService.resolve(transaction);
        PatternAnalysisResult          patternResult = patternAnalysisService.analyze(transaction);
        OsintResult                    osintResult   = osintScreeningService.screen(transaction);
        SpecialistOutcome<OsintResult> osintOutcome  = new SpecialistOutcome.Completed<>(osintResult);
        String                         sarNarrative  = draftNarrative(transaction);

        return new InvestigationSummary(
                transaction,
                new SpecialistOutcome.Completed<>(entityResult),
                new SpecialistOutcome.Completed<>(patternResult),
                osintOutcome,
                sarNarrative);
    }
}
