package io.casehub.aml.api.model;

import io.casehub.aml.ledger.*;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.model.CaseLedgerEntry;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import jakarta.persistence.DiscriminatorValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record AuditTrailEntryResponse(
        UUID entryId,
        String entryType,
        String discriminator,
        String actorId,
        String actorRole,
        Instant occurredAt,
        UUID causedByEntryId,
        String digest,
        int sequenceNumber,
        Map<String, Object> domainFields
) {
    public static AuditTrailEntryResponse from(LedgerEntry e) {
        return new AuditTrailEntryResponse(
                e.id,
                e.entryType.name(),
                resolveDiscriminator(e),
                e.actorId,
                e.actorRole,
                e.occurredAt,
                e.causedByEntryId,
                e.digest,
                e.sequenceNumber,
                extractDomainFields(e)
        );
    }

    private static String resolveDiscriminator(LedgerEntry e) {
        var ann = e.getClass().getAnnotation(DiscriminatorValue.class);
        return ann != null ? ann.value() : e.getClass().getSimpleName();
    }

    private static Map<String, Object> extractDomainFields(LedgerEntry e) {
        if (e instanceof AmlCaseOpenedLedgerEntry co) {
            return Map.of(
                    "transactionId", co.transactionId,
                    "originAccountId", co.originAccountId,
                    "destinationAccountId", co.destinationAccountId);
        }
        if (e instanceof AmlComplianceReviewLedgerEntry cr) {
            return Map.of("taskId", cr.taskId);
        }
        if (e instanceof AmlSarOfficerReviewedLedgerEntry sr) {
            var fields = new HashMap<String, Object>();
            fields.put("reviewDecision", sr.reviewDecision);
            fields.put("rejectionReason", sr.rejectionReason);
            fields.put("actorRole", sr.actorRole);
            return fields;
        }
        if (e instanceof AmlCaseProfileLedgerEntry cp) {
            var fields = new HashMap<String, Object>();
            fields.put("flagReason", cp.flagReason);
            fields.put("transactionAmount", cp.transactionAmount != null ? cp.transactionAmount.toPlainString() : null);
            fields.put("outcome", cp.outcome);
            fields.put("entityType", cp.entityType);
            fields.put("investigationPath", cp.investigationPath);
            return fields;
        }
        if (e instanceof AmlCbrAdvisoryLedgerEntry cbr) {
            return Map.of(
                    "caseCount", cbr.caseCount,
                    "confidence", cbr.confidence,
                    "predominantOutcome", cbr.predominantOutcome != null ? cbr.predominantOutcome : "",
                    "recommendedCapabilities", cbr.recommendedCapabilities != null ? cbr.recommendedCapabilities : "");
        }
        if (e instanceof MessageLedgerEntry msg) {
            var fields = new HashMap<String, Object>();
            fields.put("messageType", msg.messageType);
            fields.put("target", msg.target);
            fields.put("correlationId", msg.correlationId);
            fields.put("topic", msg.topic);
            if (msg.durationMs != null) {fields.put("durationMs", msg.durationMs);}
            return fields;
        }
        if (e instanceof CaseLedgerEntry ce) {
            var fields = new HashMap<String, Object>();
            fields.put("caseStatus", ce.caseStatus);
            fields.put("eventType", ce.eventType);
            fields.put("commandType", ce.commandType);
            return fields;
        }
        if (e instanceof WorkerDecisionEntry wd) {
            var fields = new HashMap<String, Object>();
            fields.put("workerId", wd.workerId);
            fields.put("capabilityTag", wd.capabilityTag);
            if (wd.trustScoreAtRouting != null) {fields.put("trustScoreAtRouting", wd.trustScoreAtRouting);}
            return fields;
        }
        return Map.of();
    }
}
