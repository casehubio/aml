package io.casehub.aml.provenance;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.service.model.InclusionProof;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProvDmMapper {

    private static final Map<String, String> PREFIXES = Map.of(
        "prov", "http://www.w3.org/ns/prov#",
        "casehub", "urn:casehub:ledger:",
        "aml", "urn:casehub:aml:"
    );

    public ProvDocument map(List<? extends LedgerEntry> entries, Map<UUID, InclusionProof> proofs) {
        Map<String, Map<String, Object>> entities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> activities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> agents = new LinkedHashMap<>();
        Map<String, Map<String, Object>> wasGeneratedBy = new LinkedHashMap<>();
        Map<String, Map<String, Object>> wasAssociatedWith = new LinkedHashMap<>();
        Map<String, Map<String, Object>> wasAttributedTo = new LinkedHashMap<>();
        Map<String, Map<String, Object>> wasDerivedFrom = new LinkedHashMap<>();

        for (LedgerEntry entry : entries) {
            String entryKey = "casehub:entry-" + entry.id;
            String activityKey = "aml:activity-" + entry.id;
            String agentKey = "aml:agent-" + entry.actorId;

            entities.put(entryKey, buildEntity(entry, proofs.get(entry.id)));
            activities.put(activityKey, buildActivity(entry));
            agents.putIfAbsent(agentKey, buildAgent(entry));

            wasGeneratedBy.put("_:wgb-" + entry.id, Map.of(
                "prov:entity", entryKey, "prov:activity", activityKey));
            wasAssociatedWith.put("_:waw-" + entry.id, Map.of(
                "prov:activity", activityKey, "prov:agent", agentKey));
            wasAttributedTo.put("_:wat-" + entry.id, Map.of(
                "prov:entity", entryKey, "prov:agent", agentKey));

            if (entry.causedByEntryId != null) {
                wasDerivedFrom.put("_:wdf-" + entry.id, Map.of(
                    "prov:generatedEntity", entryKey,
                    "prov:usedEntity", "casehub:entry-" + entry.causedByEntryId));
            }
        }

        return new ProvDocument(PREFIXES, entities, activities, agents,
            wasGeneratedBy, wasAssociatedWith, wasAttributedTo, wasDerivedFrom);
    }

    private Map<String, Object> buildEntity(LedgerEntry entry, InclusionProof proof) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("prov:type", entityType(entry));
        attrs.put("prov:generatedAtTime", entry.occurredAt.toString());
        attrs.put("casehub:sequenceNumber", entry.sequenceNumber);
        if (entry.digest != null) {
            attrs.put("casehub:digest", entry.digest);
        }
        if (proof != null) {
            attrs.put("casehub:merkleProofEntryIndex", proof.entryIndex());
            attrs.put("casehub:merkleProofTreeSize", proof.treeSize());
            attrs.put("casehub:merkleProofLeafHash", proof.leafHash());
            attrs.put("casehub:merkleProofTreeRoot", proof.treeRoot());
            attrs.put("casehub:merkleProofSiblings", proof.siblings().stream()
                .map(s -> Map.of("hash", s.hash(), "side", s.side().name()))
                .toList());
        }
        return attrs;
    }

    private Map<String, Object> buildActivity(LedgerEntry entry) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("prov:type", activityType(entry));
        attrs.put("prov:startTime", entry.occurredAt.toString());
        addDomainAttributes(entry, attrs);
        return attrs;
    }

    private Map<String, Object> buildAgent(LedgerEntry entry) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("prov:type", switch (entry.actorType) {
            case HUMAN -> "prov:Person";
            case SYSTEM, AGENT -> "prov:SoftwareAgent";
        });
        attrs.put("casehub:actorType", entry.actorType.name());
        if (entry.actorRole != null) {
            attrs.put("casehub:actorRole", entry.actorRole);
        }
        return attrs;
    }

    private String entityType(LedgerEntry entry) {
        return switch (entry) {
            case io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry e -> "aml:CaseOpenedRecord";
            case io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry e -> "aml:ComplianceReviewRecord";
            case io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry e -> "aml:SarOfficerReviewRecord";
            case io.casehub.aml.ledger.AmlSupervisorDecisionLedgerEntry e -> "aml:SupervisorDecisionRecord";
            case io.casehub.aml.ledger.AmlCaseProfileLedgerEntry e -> "aml:CaseProfileRecord";
            case io.casehub.aml.ledger.AmlEntityErasureLedgerEntry e -> "aml:EntityErasureRecord";
            case io.casehub.aml.ledger.AmlCbrAdvisoryLedgerEntry e -> "aml:CbrAdvisoryRecord";
            case io.casehub.aml.trust.AmlTrustRoutingAttestation e -> "aml:TrustAttestationRecord";
            case io.casehub.ledger.model.CaseLedgerEntry e -> "aml:CaseLedgerRecord";
            case io.casehub.ledger.model.WorkerDecisionEntry e -> "aml:WorkerDecisionRecord";
            case io.casehub.qhorus.runtime.ledger.MessageLedgerEntry e -> "aml:MessageRecord";
            default -> "aml:LedgerRecord";
        };
    }

    private String activityType(LedgerEntry entry) {
        return switch (entry) {
            case io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry e -> "aml:CaseOpening";
            case io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry e -> "aml:ComplianceReviewOpening";
            case io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry e -> "aml:SarOfficerReview";
            case io.casehub.aml.ledger.AmlSupervisorDecisionLedgerEntry e -> "aml:SupervisorDecision";
            case io.casehub.aml.ledger.AmlCaseProfileLedgerEntry e -> "aml:CaseProfileCapture";
            case io.casehub.aml.ledger.AmlEntityErasureLedgerEntry e -> "aml:EntityErasure";
            case io.casehub.aml.ledger.AmlCbrAdvisoryLedgerEntry e -> "aml:CbrAdvisoryGeneration";
            case io.casehub.aml.trust.AmlTrustRoutingAttestation e -> "aml:TrustAttestation";
            case io.casehub.ledger.model.CaseLedgerEntry e -> "aml:CaseLifecycleEvent";
            case io.casehub.ledger.model.WorkerDecisionEntry e -> "aml:AgentRoutingDecision";
            case io.casehub.qhorus.runtime.ledger.MessageLedgerEntry e -> "aml:SpecialistCommunication";
            default -> "aml:LedgerEvent";
        };
    }

    private void addDomainAttributes(LedgerEntry entry, Map<String, Object> attrs) {
        switch (entry) {
            case io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry e -> {
                attrs.put("aml:transactionId", e.transactionId);
                attrs.put("aml:originAccountId", e.originAccountId);
                attrs.put("aml:destinationAccountId", e.destinationAccountId);
            }
            case io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry e ->
                attrs.put("aml:taskId", e.taskId);
            case io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry e -> {
                attrs.put("aml:reviewDecision", e.reviewDecision);
                if (e.rejectionReason != null) attrs.put("aml:rejectionReason", e.rejectionReason);
            }
            case io.casehub.aml.ledger.AmlSupervisorDecisionLedgerEntry e -> {
                attrs.put("aml:selectedBindings", e.selectedBindings);
                if (e.suppressedBindings != null) attrs.put("aml:suppressedBindings", e.suppressedBindings);
                attrs.put("aml:rationale", e.rationale);
                attrs.put("aml:earlyTermination", e.earlyTermination);
                attrs.put("aml:eligibleCount", e.eligibleCount);
                attrs.put("aml:degraded", e.degraded);
            }
            case io.casehub.aml.ledger.AmlCaseProfileLedgerEntry e -> {
                attrs.put("aml:flagReason", e.flagReason);
                attrs.put("aml:transactionAmount", e.transactionAmount);
                attrs.put("aml:priorIncidentCount", e.priorIncidentCount);
                if (e.entityType != null) attrs.put("aml:entityType", e.entityType);
                if (e.jurisdictionRisk != null) attrs.put("aml:jurisdictionRisk", e.jurisdictionRisk);
                if (e.networkComplexity != null) attrs.put("aml:networkComplexity", e.networkComplexity);
                attrs.put("aml:outcome", e.outcome);
                if (e.confidence != null) attrs.put("aml:confidence", e.confidence);
                attrs.put("aml:investigationPath", e.investigationPath);
                if (e.narrativeSeeded != null) attrs.put("aml:narrativeSeeded", e.narrativeSeeded);
                if (e.seedCount != null) attrs.put("aml:seedCount", e.seedCount);
                if (e.adaptationMethod != null) attrs.put("aml:adaptationMethod", e.adaptationMethod);
            }
            case io.casehub.aml.ledger.AmlEntityErasureLedgerEntry e -> {
                attrs.put("aml:erasedEntityId", e.erasedEntityId);
                attrs.put("aml:erasureReason", e.erasureReason.name());
                attrs.put("aml:memoriesErased", e.memoriesErased);
            }
            case io.casehub.aml.ledger.AmlCbrAdvisoryLedgerEntry e -> {
                attrs.put("aml:caseCount", e.caseCount);
                attrs.put("aml:avgSimilarity", e.avgSimilarity);
                attrs.put("aml:confidence", e.confidence);
                if (e.predominantOutcome != null) attrs.put("aml:predominantOutcome", e.predominantOutcome);
                if (e.predominantOutcomeFrequency != null) attrs.put("aml:predominantOutcomeFrequency", e.predominantOutcomeFrequency);
                if (e.recommendedCapabilities != null) attrs.put("aml:recommendedCapabilities", e.recommendedCapabilities);
                attrs.put("aml:active", e.active);
            }
            case io.casehub.aml.trust.AmlTrustRoutingAttestation e -> {
                attrs.put("aml:capabilityTag", e.capabilityTag);
                attrs.put("aml:selectedWorkerId", e.selectedWorkerId);
                if (e.trustScoreAtRouting != null) attrs.put("aml:trustScoreAtRouting", e.trustScoreAtRouting);
                attrs.put("aml:thresholdApplied", e.thresholdApplied);
                attrs.put("aml:investigationCaseId", e.investigationCaseId.toString());
                attrs.put("aml:reconstructed", e.reconstructed);
                attrs.put("aml:observerFailed", e.observerFailed);
            }
            case io.casehub.ledger.model.WorkerDecisionEntry e -> {
                if (e.capabilityTag != null) attrs.put("aml:capabilityTag", e.capabilityTag);
                if (e.workerId != null) attrs.put("aml:workerId", e.workerId);
                if (e.trustScoreAtRouting != null) attrs.put("aml:trustScoreAtRouting", e.trustScoreAtRouting);
                if (e.routingRationale != null) attrs.put("aml:routingRationale", e.routingRationale);
            }
            case io.casehub.ledger.model.CaseLedgerEntry e -> {
                if (e.caseStatus != null) attrs.put("aml:caseStatus", e.caseStatus);
                if (e.eventType != null) attrs.put("aml:eventType", e.eventType);
                if (e.commandType != null) attrs.put("aml:commandType", e.commandType);
            }
            case io.casehub.qhorus.runtime.ledger.MessageLedgerEntry e -> {
                if (e.messageType != null) attrs.put("aml:messageType", e.messageType);
                if (e.target != null) attrs.put("aml:target", e.target);
                if (e.correlationId != null) attrs.put("aml:correlationId", e.correlationId);
                if (e.topic != null) attrs.put("aml:topic", e.topic);
                if (e.durationMs != null) attrs.put("aml:durationMs", e.durationMs);
            }
            default -> {}
        }
    }
}
