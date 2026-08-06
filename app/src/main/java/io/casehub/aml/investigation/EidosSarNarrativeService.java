package io.casehub.aml.investigation;

import io.casehub.ledger.runtime.privacy.ContentSanitiser;
import io.casehub.ledger.runtime.privacy.PassThroughContentSanitiser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

@Alternative
@Priority(1)
@ApplicationScoped
public class EidosSarNarrativeService implements SarNarrativeService {

    private static final Logger LOG = Logger.getLogger(EidosSarNarrativeService.class);

    private final TemplateSarNarrativeService templateService;
    private final ContentSanitiser sanitiser;

    @Inject
    public EidosSarNarrativeService(TemplateSarNarrativeService templateService,
                                     ContentSanitiser sanitiser) {
        this.templateService = templateService;
        this.sanitiser = sanitiser;
    }

    @PostConstruct
    void warnIfPassThrough() {
        if (sanitiser instanceof PassThroughContentSanitiser) {
            LOG.warn("EidosSarNarrativeService is active with pass-through ContentSanitiser — " +
                     "seed narratives sent to eidos may contain PII from past cases. " +
                     "Gate production activation on #115.");
        }
    }

    @Override
    public NarrativeResult draft(NarrativeContext context) {
        return callEidos(context);
    }

    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS, successThreshold = 3)
    @Fallback(fallbackMethod = "draftDeterministic")
    NarrativeResult callEidos(NarrativeContext context) {
        LOG.info("Eidos SAR narrative service called — stub delegates to template");
        var result = templateService.draft(context);
        return new NarrativeResult(result.narrative(), result.seeded(), result.seedCount(), AdaptationMethod.LLM);
    }

    NarrativeResult draftDeterministic(NarrativeContext context) {
        LOG.warn("Eidos call failed — falling back to deterministic narrative");
        var result = templateService.draft(context);
        return new NarrativeResult(result.narrative(), result.seeded(), result.seedCount(),
                AdaptationMethod.LLM_FALLBACK_DETERMINISTIC);
    }
}
