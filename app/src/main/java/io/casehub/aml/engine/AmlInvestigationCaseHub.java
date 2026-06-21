package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.aml.ComplianceReviewLifecycle;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Layer 5 case hub — loads the AML investigation YAML definition and augments it
 * with in-process worker functions from {@link AmlInvestigationCaseDescriptor}.
 *
 * <p>Thin CDI wrapper. All worker lambdas and business logic live in the descriptor,
 * which is testable without Quarkus. This hub's only responsibility is wiring CDI
 * dependencies to the descriptor and producing the augmented {@link CaseDefinition}.
 *
 * <p>The returned {@link CaseDefinition} is mutable; the engine may write to its
 * workers list during execution. Callers must not add workers after {@code @PostConstruct}
 * — augmentation happens exactly once and is not thread-safe after that point.
 */
@ApplicationScoped
public class AmlInvestigationCaseHub extends YamlCaseHub {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationCaseHub.class);

    @Inject ComplianceReviewLifecycle complianceReviewLifecycle;
    @Inject ObjectMapper objectMapper;

    private CaseDefinition augmentedDefinition;

    public AmlInvestigationCaseHub() {
        super("aml/aml-investigation.yaml");
    }

    @PostConstruct
    void init() {
        final var descriptor = new AmlInvestigationCaseDescriptor(complianceReviewLifecycle, objectMapper);
        augmentedDefinition = super.getDefinition();
        augmentedDefinition.getWorkers().addAll(descriptor.workers());
        LOG.debugf("AML investigation definition augmented: %d workers registered",
                augmentedDefinition.getWorkers().size());
    }

    @Override
    public CaseDefinition getDefinition() {
        return augmentedDefinition;
    }
}
