package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.ComplianceReviewLifecycle;
import io.casehub.api.engine.YamlCaseHub;
import io.casehub.aml.cbr.AmlCbrSchema;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.CbrConfig.CbrRetrievalTiming;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AmlInvestigationCaseHub extends YamlCaseHub {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationCaseHub.class);

    @Inject
    ComplianceReviewLifecycle complianceReviewLifecycle;
    @Inject
    ObjectMapper              objectMapper;
    @Inject
    io.casehub.ledger.api.spi.LedgerEntryRepository ledgerRepository;
    @Inject
    io.casehub.platform.api.identity.CurrentPrincipal principal;
    @Inject
    io.casehub.platform.api.preferences.PreferenceProvider preferenceProvider;

    public AmlInvestigationCaseHub() {
        super("aml/aml-investigation.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        final var seeder = new io.casehub.aml.cbr.SarNarrativeSeeder(
                new io.casehub.ledger.runtime.privacy.PassThroughDecisionContextSanitiser());
        final var descriptor = new AmlInvestigationCaseDescriptor(complianceReviewLifecycle, objectMapper, ledgerRepository, principal, preferenceProvider, seeder);
        definition.getWorkers().addAll(descriptor.workers());

        definition.setCbrConfig(CbrConfig.builder()
                                         .feature("flag_reason", ".transaction.flagReason")
                                         .feature("transaction_amount", ".transaction.amount")
                                         .feature("prior_incident_count", ".priorEntityContext.entityRiskCount")
                                         .feature("entity_type", ".entityResolution.entityType")
                                         .domain("aml.cbr")
                                         .caseType(AmlCbrSchema.CASE_TYPE)
                                         .topK(10)
                                         .minSimilarity(0.5)
                                         .vectorWeight(0.0)
                                         .timing(CbrRetrievalTiming.CASE_LIFETIME)
                                         .cbrType("plan")
                                         .weight("flag_reason", 0.30)
                                         .weight("transaction_amount", 0.15)
                                         .weight("prior_incident_count", 0.10)
                                         .weight("entity_type", 0.20)
                                         .build());

        LOG.debugf("AML investigation definition augmented: %d workers, CbrConfig=%s",
                   definition.getWorkers().size(), definition.getCbrConfig().caseType());
    }
}
