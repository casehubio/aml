package io.casehub.aml.api.model;

import io.casehub.aml.ledger.*;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditTrailEntryResponseMappingTest {

    @Test
    void caseOpened_extractsTransactionFields() {
        var entry = new AmlCaseOpenedLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AmlInvestigationOrchestrator";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 1;
        entry.transactionId = "TXN-001";
        entry.originAccountId = "ACC-001";
        entry.destinationAccountId = "ACC-002";

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("AML_CASE_OPENED", response.discriminator());
        assertEquals("TXN-001", response.domainFields().get("transactionId"));
        assertEquals("ACC-001", response.domainFields().get("originAccountId"));
        assertEquals("ACC-002", response.domainFields().get("destinationAccountId"));
    }

    @Test
    void complianceReview_extractsTaskId() {
        var entry = new AmlComplianceReviewLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AmlInvestigationOrchestrator";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 3;
        entry.taskId = "task-abc-123";

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("AML_COMPLIANCE_REVIEW", response.discriminator());
        assertEquals("task-abc-123", response.domainFields().get("taskId"));
    }

    @Test
    void sarOfficerReviewed_extractsDecisionAndActorRole() {
        var entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "officer-001";
        entry.actorType = ActorType.HUMAN;
        entry.actorRole = "ComplianceOfficer";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 5;
        entry.reviewDecision = "approved";
        entry.rejectionReason = null;

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("AML_SAR_OFFICER_REVIEWED", response.discriminator());
        assertEquals("approved", response.domainFields().get("reviewDecision"));
        assertNull(response.domainFields().get("rejectionReason"));
        assertEquals("ComplianceOfficer", response.domainFields().get("actorRole"));
    }

    @Test
    void sarOfficerReviewed_observerFailure_exposesActorRole() {
        var entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ComplianceOfficer-observer-failed";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 5;
        entry.reviewDecision = "approved";

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("ComplianceOfficer-observer-failed", response.domainFields().get("actorRole"));
    }

    @Test
    void caseProfile_serializesBigDecimalAsString() {
        var entry = new AmlCaseProfileLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "CaseProfileStore";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 10;
        entry.flagReason = "HIGH_RISK_JURISDICTION";
        entry.transactionAmount = new BigDecimal("123456.7890");
        entry.outcome = "sar-filed";
        entry.investigationPath = "entity-resolution,pattern-analysis";
        entry.priorIncidentCount = 2;

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("AML_CASE_PROFILE", response.discriminator());
        assertEquals("123456.7890", response.domainFields().get("transactionAmount"));
        assertEquals("HIGH_RISK_JURISDICTION", response.domainFields().get("flagReason"));
        assertEquals("sar-filed", response.domainFields().get("outcome"));
    }

    @Test
    void cbrAdvisory_extractsAdvisoryFields() {
        var entry = new AmlCbrAdvisoryLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = LedgerEntryType.ATTESTATION;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "CbrPathAdvisor";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = 8;
        entry.caseCount = 5;
        entry.confidence = 0.85;
        entry.predominantOutcome = "sar-filed";
        entry.recommendedCapabilities = "entity-resolution,pattern-analysis";

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals("AML_CBR_ADVISORY", response.discriminator());
        assertEquals(5, response.domainFields().get("caseCount"));
        assertEquals(0.85, response.domainFields().get("confidence"));
        assertEquals("sar-filed", response.domainFields().get("predominantOutcome"));
    }

    @Test
    void baseFieldsPreserved() {
        var entry = new AmlCaseOpenedLedgerEntry();
        var id = UUID.randomUUID();
        var causedBy = UUID.randomUUID();
        var now = Instant.now();
        entry.id = id;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "role-1";
        entry.occurredAt = now;
        entry.causedByEntryId = causedBy;
        entry.digest = "sha256:abc";
        entry.sequenceNumber = 42;
        entry.transactionId = "TXN-X";
        entry.originAccountId = "A";
        entry.destinationAccountId = "B";

        var response = AuditTrailEntryResponse.from(entry);

        assertEquals(id, response.entryId());
        assertEquals("EVENT", response.entryType());
        assertEquals("actor-1", response.actorId());
        assertEquals("role-1", response.actorRole());
        assertEquals(now, response.occurredAt());
        assertEquals(causedBy, response.causedByEntryId());
        assertEquals("sha256:abc", response.digest());
        assertEquals(42, response.sequenceNumber());
    }
}
