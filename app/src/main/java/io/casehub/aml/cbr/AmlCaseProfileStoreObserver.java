package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.CaseProfile;
import io.casehub.aml.domain.EntityType;
import io.casehub.aml.domain.JurisdictionRisk;
import io.casehub.aml.domain.NetworkComplexity;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.compliance.AmlComplianceSupplement;
import io.casehub.aml.compliance.AmlContentSanitiser;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CapabilityTarget;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.TaskStatus;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AmlCaseProfileStoreObserver implements CaseOutcomeObserver {

    private static final Logger LOG = Logger.getLogger(AmlCaseProfileStoreObserver.class);
    private static final AmlContentSanitiser ART22_SANITISER = new AmlContentSanitiser();


    private static final Map<TaskStatus, String> OUTCOME_MAP = Map.of(
            TaskStatus.COMPLETED, "SUCCESS",
            TaskStatus.FAULTED, "FAILURE",
            TaskStatus.REJECTED, "DECLINED",
            TaskStatus.CANCELLED, "CANCELLED",
            TaskStatus.OBSOLETE, "OBSOLETE");

    @Inject
    CbrCaseMemoryStore     cbrStore;
    @Inject
    LedgerEntryRepository  ledgerRepository;
    @Inject
    PlanItemStore          planItemStore;
    @Inject
    CaseDefinitionRegistry registry;
    @Inject
    ObjectMapper           objectMapper;
    @Inject
    CurrentPrincipal       principal;

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        try {
            doRetain(event);
        } catch (Exception e) {
            LOG.warnf(e, "CBR retain failed for caseId=%s — skipping", event.caseId());
        }
    }

    @SuppressWarnings("unchecked")
    private void doRetain(CaseOutcomeEvent event) {
        var caseId   = event.caseId();
        var snapshot = event.caseFileSnapshot();
        var tenantId = event.tenancyId() != null
                       ? event.tenancyId()
                       : TenancyConstants.DEFAULT_TENANT_ID;

        var triageMap = (Map<String, Object>) snapshot.get("investigationTriage");
        if (triageMap == null) {
            LOG.warnf("No investigationTriage in snapshot for caseId=%s — skipping CBR retain", caseId);
            return;
        }
        TriageDecision triageDecision;
        try {
            triageDecision = TriageDecision.valueOf((String) triageMap.get("decision"));
        } catch (Exception e) {
            LOG.warnf("Invalid triage decision in snapshot for caseId=%s — skipping", caseId);
            return;
        }

        SuspiciousTransaction tx;
        try {
            tx = objectMapper.convertValue(snapshot.get("transaction"), SuspiciousTransaction.class);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize transaction for caseId=%s — skipping", caseId);
            return;
        }

        @SuppressWarnings("unchecked")
        var entityResolution   = (Map<String, Object>) snapshot.get("entityResolution");
        var entityTypeStr      = entityResolution != null && entityResolution.get("entityType") instanceof String s ? s : null;
        var jurisdictionStr    = entityResolution != null && entityResolution.get("jurisdictionRisk") instanceof String s ? s : null;
        var networkStr         = entityResolution != null && entityResolution.get("networkComplexity") instanceof String s ? s : null;
        final int priorIncidentCount;
        if (snapshot.get("priorEntityContext") instanceof Map<?, ?> m
            && m.get("entityRiskCount") instanceof Number n) {
            priorIncidentCount = n.intValue();
        } else {
            priorIncidentCount = 0;
        }

        EntityType        entityType   = safeValueOf(EntityType.class, entityTypeStr);
        JurisdictionRisk  jurisdiction = safeValueOf(JurisdictionRisk.class, jurisdictionStr);
        NetworkComplexity network      = safeValueOf(NetworkComplexity.class, networkStr);

        CaseProfile profile;
        if (entityType != null && jurisdiction != null && network != null) {
            profile = CaseProfile.complete(
                    tx.flagReason(), tx.amount(), priorIncidentCount,
                    entityType, jurisdiction, network);
        } else {
            profile = CaseProfile.initial(tx.flagReason(), tx.amount(), priorIncidentCount);
        }

        var definition        = registry.findByName(event.caseType()).orElse(null);
        var capabilityNameMap = definition != null ? buildRoutingKeyMap(definition) : Map.<String, String>of();

        var   records = planItemStore.findByCaseId(caseId, tenantId);
        int[] index   = {0};
        var traces = records.stream()
                            .filter(r -> r.status().isTerminal())
                            .filter(r -> capabilityNameMap.isEmpty() || capabilityNameMap.containsKey(r.bindingName()))
                            .filter(r -> r.executorName() != null)
                            .sorted(Comparator.comparing(PlanItemRecord::createdAt))
                            .map(r -> new PlanTrace(r.bindingName(),
                                                    capabilityNameMap.getOrDefault(r.bindingName(), r.bindingName()),
                                                    r.executorName(),
                                                    OUTCOME_MAP.getOrDefault(r.status(), r.status().name()),
                                                    index[0]++, Map.of(), null))
                            .toList();

        String solution = traces.stream()
                                .map(t -> t.bindingName() + "→" + t.workerName() + "(" + t.stepOutcome() + ")")
                                .collect(Collectors.joining(", "));
        if (solution.isBlank()) {
            solution = "(direct-verdict)";
        }

        String problem = String.format("Flagged transaction %s: %s from %s to %s, amount %s %s",
                                       tx.id(), tx.flagReason().name(),
                                       tx.originAccountId(), tx.destinationAccountId(),
                                       tx.amount().toPlainString(), tx.currency());

        var features = new LinkedHashMap<>(profile.toFeatures());
        if (snapshot.get("sarNarrative") instanceof String s) {
            features.put("sar_narrative", FeatureValue.string(s));
        }

        var cbrCase = new PlanCbrCase(problem, solution,
                                      triageDecision.name(), null, features, traces, null, null);

        String entityId = UUID.nameUUIDFromBytes(
                ("aml-cbr:" + caseId).getBytes(StandardCharsets.UTF_8)).toString();

        try {
            cbrStore.store(cbrCase, AmlCbrSchema.CASE_TYPE, entityId,
                           AmlMemoryDomains.CBR, tenantId, caseId.toString(), Path.root());
            LOG.infof("CBR case profile stored: caseId=%s outcome=%s traces=%d",
                      caseId, triageDecision, traces.size());
        } catch (Exception e) {
            LOG.warnf(e, "CBR store failed for caseId=%s — skipping", caseId);
        }

        final String fEntityType    = entityTypeStr;
        final String fJurisdiction  = jurisdictionStr;
        final String fNetwork       = networkStr;
        final String fSolution      = solution;
        final String fOutcome       = triageDecision.name();
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                var entry = new AmlCaseProfileLedgerEntry();
                entry.flagReason         = tx.flagReason().name();
                entry.transactionAmount  = tx.amount();
                entry.priorIncidentCount = priorIncidentCount;
                entry.entityType         = fEntityType;
                entry.jurisdictionRisk   = fJurisdiction;
                entry.networkComplexity  = fNetwork;
                entry.outcome            = fOutcome;
                entry.confidence         = null;
                entry.investigationPath  = fSolution;
                if (snapshot.get("narrativeSeeded") instanceof Boolean b) {
                    entry.narrativeSeeded = b;
                }
                if (snapshot.get("seedCount") instanceof Number n) {
                    entry.seedCount = n.intValue();
                }
                if (snapshot.get("adaptationMethod") instanceof String s) {
                    entry.adaptationMethod = s;
                }
                entry.subjectId          = caseId;
                entry.actorId            = "aml-system";
                entry.tenancyId          = tenantId;
                entry.entryType          = LedgerEntryType.ATTESTATION;
                try {
                    var decisionContextMap = new LinkedHashMap<String, Object>();
                    decisionContextMap.put("decision", fOutcome);
                    decisionContextMap.put("flagReason", tx.flagReason().name());
                    decisionContextMap.put("amount", tx.amount().toPlainString());
                    decisionContextMap.put("currency", tx.currency());
                    if (fEntityType != null) decisionContextMap.put("entityType", fEntityType);
                    if (fJurisdiction != null) decisionContextMap.put("jurisdictionRisk", fJurisdiction);
                    if (fNetwork != null) decisionContextMap.put("networkComplexity", fNetwork);
                    String contextJson = objectMapper.writeValueAsString(decisionContextMap);
                    String sanitised = ART22_SANITISER.sanitise(contextJson);
                    entry.attach(AmlComplianceSupplement.triageDecision(
                            fOutcome, entry.confidence, fSolution, sanitised));
                } catch (Exception e) {
                    LOG.warnf(e, "Art.22 supplement creation failed for caseId=%s — entry saved without supplement", caseId);
                }
                ledgerRepository.save(entry, tenantId);
            });
            LOG.infof("CBR profile ledger entry written: caseId=%s", caseId);
        } catch (Exception e) {
            LOG.warnf(e, "Ledger write failed for CBR profile caseId=%s — skipping", caseId);
        }
    }

    private Map<String, String> buildRoutingKeyMap(CaseDefinition definition) {
        var map = new LinkedHashMap<String, String>();
        for (Binding binding : definition.getBindings()) {
            if (binding.target() instanceof CapabilityTarget ct) {
                map.put(binding.getName(), ct.capability().name());
            }
        }
        return map;
    }

    private static <E extends Enum<E>> E safeValueOf(Class<E> enumType, String value) {
        if (value == null) {return null;}
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
