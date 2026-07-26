package io.casehub.aml.memory;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.api.spi.routing.IntPreference;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central service for all CaseMemoryStore interactions in the AML domain.
 * No caller touches CaseMemoryStore directly — all domain semantics live here.
 * Memory failures MUST NOT propagate to callers.
 */
@ApplicationScoped
public class AmlMemoryService {

    private static final Logger LOG = Logger.getLogger(AmlMemoryService.class);
    private static final String AML_SYSTEM_ACTOR = "aml-system";

    private final CaseMemoryStore memoryStore;
    private final CurrentPrincipal principal;
    private final PreferenceProvider preferenceProvider;

    @Inject
    public AmlMemoryService(
            final CaseMemoryStore memoryStore,
            final CurrentPrincipal principal,
            final PreferenceProvider preferenceProvider) {
        this.memoryStore = memoryStore;
        this.principal = principal;
        this.preferenceProvider = preferenceProvider;
    }

    // ── Read path ────────────────────────────────────────────────────────────

    public AmlPriorContext queryPriorContext(final SuspiciousTransaction transaction) {
        final String tenantId = principal.tenancyId();
        final List<String> entityIds = List.of(
            transaction.originAccountId(), transaction.destinationAccountId());
        final Instant since = lookbackCutoff();

        // Entity-risk lookback is time-bounded (stale risk classifications must not drive routing).
        // Network and pattern facts are unbounded — historical relationships remain relevant.
        List<Memory> entityRisk = queryDomain(entityIds, AmlMemoryDomains.ENTITY_RISK, tenantId, since);
        List<Memory> network    = queryDomain(entityIds, AmlMemoryDomains.NETWORK, tenantId, null);
        List<Memory> pattern    = queryDomain(entityIds, AmlMemoryDomains.PATTERN, tenantId, null);

        return new AmlPriorContext(entityRisk, network, pattern);
    }

    private List<Memory> queryDomain(
            final List<String> entityIds, final MemoryDomain domain,
            final String tenantId, final Instant since) {
        try {
            // Limit 20 provides headroom: adapters return newest-first (DESC), so selectFacts() sees
            // the most relevant records even when the caller passes a wide entity list.
            MemoryQuery query = MemoryQuery.forEntities(entityIds, domain, tenantId).withLimit(20);
            if (since != null) query = query.withSince(since);
            return memoryStore.query(query);
        } catch (Exception e) {
            LOG.warnf(e, "Memory query failed for domain %s — returning empty list", domain.name());
            return List.of();
        }
    }

    private Instant lookbackCutoff() {
        try {
            final Preferences prefs = preferenceProvider.resolve(
                SettingsScope.of(io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID, io.casehub.platform.api.path.Path.of("casehubio", "aml", "memory")));
            final IntPreference lookback = prefs.get(AmlMemoryPolicyKeys.ENTITY_RISK_LOOKBACK_DAYS);
            final int days = lookback != null ? lookback.value() : 365;
            return Instant.now().minus(Duration.ofDays(days));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to resolve memory lookback — defaulting to 365 days");
            return Instant.now().minus(Duration.ofDays(365));
        }
    }

    // ── Write path ────────────────────────────────────────────────────────────

    public void storeEntityRisk(
            final UUID caseId, final String entityId, final EntityResolutionResult result) {
        try {
            final String text = String.format(
                "Account %s resolved as entity type %s (risk score: %.4f). Ownership chain: %s.",
                entityId, result.entityType(), result.riskScore(), result.ownershipChain());
            memoryStore.store(new MemoryInput(
                entityId, AmlMemoryDomains.ENTITY_RISK, principal.tenancyId(),
                caseId != null ? caseId.toString() : null, text,
                Map.of(
                    MemoryAttributeKeys.ACTOR_ID,   AML_SYSTEM_ACTOR,
                    MemoryAttributeKeys.OUTCOME,    result.entityType(),
                    MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(result.riskScore()))));
        } catch (Exception e) {
            LOG.warnf(e, "storeEntityRisk failed for entity %s — skipping", entityId);
        }
    }

    public void storeNetworkRelationship(
            final UUID caseId, final SuspiciousTransaction transaction, final EntityResolutionResult result) {
        try {
            final String text = String.format(
                "Transaction %s between accounts %s and %s. Beneficial ownership: %s.",
                transaction.id(), transaction.originAccountId(), transaction.destinationAccountId(),
                result.ownershipChain());
            final String caseIdStr = caseId != null ? caseId.toString() : null;
            final String tenantId = principal.tenancyId();
            final Map<String, String> attrs = Map.of(MemoryAttributeKeys.ACTOR_ID, AML_SYSTEM_ACTOR);
            memoryStore.storeAll(List.of(
                new MemoryInput(transaction.originAccountId(),      AmlMemoryDomains.NETWORK, tenantId, caseIdStr, text, attrs),
                new MemoryInput(transaction.destinationAccountId(), AmlMemoryDomains.NETWORK, tenantId, caseIdStr, text, attrs)));
        } catch (Exception e) {
            LOG.warnf(e, "storeNetworkRelationship failed for tx %s — skipping", transaction.id());
        }
    }

    public void storePatternFindings(
            final UUID caseId, final SuspiciousTransaction transaction, final PatternAnalysisResult result) {
        try {
            final String text = String.format(
                "Pattern analysis for transaction %s (accounts %s → %s): %s. Structuring detected: %b.",
                transaction.id(), transaction.originAccountId(), transaction.destinationAccountId(),
                result.description(), result.structuringDetected());
            final String outcome = result.structuringDetected() ? "STRUCTURING_DETECTED" : "NO_STRUCTURING";
            final String caseIdStr = caseId != null ? caseId.toString() : null;
            final String tenantId = principal.tenancyId();
            final Map<String, String> attrs = Map.of(
                MemoryAttributeKeys.ACTOR_ID, AML_SYSTEM_ACTOR,
                MemoryAttributeKeys.OUTCOME,  outcome);
            memoryStore.storeAll(List.of(
                new MemoryInput(transaction.originAccountId(),      AmlMemoryDomains.PATTERN, tenantId, caseIdStr, text, attrs),
                new MemoryInput(transaction.destinationAccountId(), AmlMemoryDomains.PATTERN, tenantId, caseIdStr, text, attrs)));
        } catch (Exception e) {
            LOG.warnf(e, "storePatternFindings failed for tx %s — skipping", transaction.id());
        }
    }

    public void storeSarOutcome(
            final UUID caseId, final SuspiciousTransaction transaction, final SarOutcome outcome) {
        try {
            final boolean isReversal =
                outcome.verdict() == SarVerdict.WITHDRAWN || outcome.verdict() == SarVerdict.FLAGGED;
            final double confidence = isReversal ? 0.0 : outcome.investigationAccuracyScore();
            final String text = String.format(
                "Transaction from %s to %s resulted in SAR %s (%s). Investigation accuracy: %.4f.",
                transaction.originAccountId(), transaction.destinationAccountId(),
                isReversal ? "reversal" : "filing",
                outcome.verdict().name(), outcome.investigationAccuracyScore());
            final String caseIdStr = caseId != null ? caseId.toString() : null;
            final String tenantId = principal.tenancyId();
            final Map<String, String> attrs = Map.of(
                MemoryAttributeKeys.ACTOR_ID,   AML_SYSTEM_ACTOR,
                MemoryAttributeKeys.OUTCOME,    outcome.verdict().name(),
                MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(confidence));
            memoryStore.storeAll(List.of(
                new MemoryInput(transaction.originAccountId(),      AmlMemoryDomains.ENTITY_RISK, tenantId, caseIdStr, text, attrs),
                new MemoryInput(transaction.destinationAccountId(), AmlMemoryDomains.ENTITY_RISK, tenantId, caseIdStr, text, attrs)));
        } catch (Exception e) {
            LOG.warnf(e, "storeSarOutcome failed for caseId %s — skipping", caseId);
        }
    }
}
