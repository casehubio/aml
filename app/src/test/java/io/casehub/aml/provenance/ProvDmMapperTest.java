package io.casehub.aml.provenance;

import io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry;
import io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProvDmMapperTest {

    private final ProvDmMapper mapper = new ProvDmMapper();

    @Test
    void emptyEntryList_producesDocumentWithPrefixesOnly() {
        ProvDocument doc = mapper.map(List.of(), Map.of());
        assertNotNull(doc);
        assertEquals(3, doc.prefix().size());
        assertEquals("http://www.w3.org/ns/prov#", doc.prefix().get("prov"));
        assertEquals("urn:casehub:ledger:", doc.prefix().get("casehub"));
        assertEquals("urn:casehub:aml:", doc.prefix().get("aml"));
        assertTrue(doc.entity().isEmpty());
        assertTrue(doc.activity().isEmpty());
        assertTrue(doc.agent().isEmpty());
    }

    @Test
    void singleCaseOpenedEntry_mapsEntityActivityAgent() {
        AmlCaseOpenedLedgerEntry entry = new AmlCaseOpenedLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AmlInvestigationOrchestrator";
        entry.occurredAt = Instant.parse("2026-08-15T10:00:00Z");
        entry.transactionId = "TXN-2024-001";
        entry.originAccountId = "ACC-A";
        entry.destinationAccountId = "ACC-B";
        entry.digest = "sha256:abc123";

        ProvDocument doc = mapper.map(List.of(entry), Map.of());

        String entryKey = "casehub:entry-" + entry.id;
        assertTrue(doc.entity().containsKey(entryKey));
        assertEquals("aml:CaseOpenedRecord", doc.entity().get(entryKey).get("prov:type"));
        assertEquals("2026-08-15T10:00:00Z", doc.entity().get(entryKey).get("prov:generatedAtTime"));
        assertEquals("sha256:abc123", doc.entity().get(entryKey).get("casehub:digest"));

        String activityKey = "aml:activity-" + entry.id;
        assertTrue(doc.activity().containsKey(activityKey));
        assertEquals("aml:CaseOpening", doc.activity().get(activityKey).get("prov:type"));
        assertEquals("TXN-2024-001", doc.activity().get(activityKey).get("aml:transactionId"));
        assertEquals("ACC-A", doc.activity().get(activityKey).get("aml:originAccountId"));

        String agentKey = "aml:agent-aml-orchestrator";
        assertTrue(doc.agent().containsKey(agentKey));
        assertEquals("prov:SoftwareAgent", doc.agent().get(agentKey).get("prov:type"));
        assertEquals("SYSTEM", doc.agent().get(agentKey).get("casehub:actorType"));

        assertEquals(1, doc.wasGeneratedBy().size());
        assertEquals(1, doc.wasAssociatedWith().size());
        assertEquals(1, doc.wasAttributedTo().size());
        assertTrue(doc.wasDerivedFrom().isEmpty());
    }

    @Test
    void causalChain_producesWasDerivedFromEdges() {
        AmlCaseOpenedLedgerEntry opened = new AmlCaseOpenedLedgerEntry();
        opened.id = UUID.randomUUID();
        opened.subjectId = UUID.randomUUID();
        opened.sequenceNumber = 1;
        opened.actorId = "aml-orchestrator";
        opened.actorType = ActorType.SYSTEM;
        opened.actorRole = "AmlInvestigationOrchestrator";
        opened.occurredAt = Instant.now();
        opened.transactionId = "TXN-001";
        opened.originAccountId = "A";
        opened.destinationAccountId = "B";

        AmlComplianceReviewLedgerEntry review = new AmlComplianceReviewLedgerEntry();
        review.id = UUID.randomUUID();
        review.subjectId = opened.subjectId;
        review.sequenceNumber = 2;
        review.actorId = "aml-orchestrator";
        review.actorType = ActorType.SYSTEM;
        review.actorRole = "AmlInvestigationOrchestrator";
        review.occurredAt = Instant.now();
        review.taskId = UUID.randomUUID().toString();
        review.causedByEntryId = opened.id;

        ProvDocument doc = mapper.map(List.of(opened, review), Map.of());

        assertEquals(1, doc.wasDerivedFrom().size());
        Map<String, Object> edge = doc.wasDerivedFrom().values().iterator().next();
        assertEquals("casehub:entry-" + review.id, edge.get("prov:generatedEntity"));
        assertEquals("casehub:entry-" + opened.id, edge.get("prov:usedEntity"));
    }

    @Test
    void sameActorId_producesOneAgentNode() {
        AmlCaseOpenedLedgerEntry e1 = new AmlCaseOpenedLedgerEntry();
        e1.id = UUID.randomUUID();
        e1.subjectId = UUID.randomUUID();
        e1.sequenceNumber = 1;
        e1.actorId = "aml-orchestrator";
        e1.actorType = ActorType.SYSTEM;
        e1.actorRole = "AmlInvestigationOrchestrator";
        e1.occurredAt = Instant.now();
        e1.transactionId = "T1";
        e1.originAccountId = "A";
        e1.destinationAccountId = "B";

        AmlComplianceReviewLedgerEntry e2 = new AmlComplianceReviewLedgerEntry();
        e2.id = UUID.randomUUID();
        e2.subjectId = e1.subjectId;
        e2.sequenceNumber = 2;
        e2.actorId = "aml-orchestrator";
        e2.actorType = ActorType.SYSTEM;
        e2.actorRole = "AmlInvestigationOrchestrator";
        e2.occurredAt = Instant.now();
        e2.taskId = "task-1";

        ProvDocument doc = mapper.map(List.of(e1, e2), Map.of());

        assertEquals(1, doc.agent().size());
        assertEquals(2, doc.entity().size());
        assertEquals(2, doc.activity().size());
    }

    @Test
    void humanActor_mapsToPerson() {
        AmlSarOfficerReviewedLedgerEntry entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.actorId = "officer-jane";
        entry.actorType = ActorType.HUMAN;
        entry.actorRole = "ComplianceOfficer";
        entry.occurredAt = Instant.now();
        entry.reviewDecision = "APPROVED";

        ProvDocument doc = mapper.map(List.of(entry), Map.of());

        Map<String, Object> agent = doc.agent().get("aml:agent-officer-jane");
        assertEquals("prov:Person", agent.get("prov:type"));
        assertEquals("HUMAN", agent.get("casehub:actorType"));
        assertEquals("ComplianceOfficer", agent.get("casehub:actorRole"));

        Map<String, Object> activity = doc.activity().values().iterator().next();
        assertEquals("APPROVED", activity.get("aml:reviewDecision"));
        assertNull(activity.get("aml:rejectionReason"));
    }

    @Test
    void unknownEntryType_fallsBackToGeneric() {
        io.casehub.ledger.runtime.model.PlainLedgerEntry entry =
            new io.casehub.ledger.runtime.model.PlainLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.actorId = "unknown-system";
        entry.actorType = ActorType.SYSTEM;
        entry.occurredAt = Instant.now();

        ProvDocument doc = mapper.map(List.of(entry), Map.of());

        Map<String, Object> entity = doc.entity().values().iterator().next();
        assertEquals("aml:LedgerRecord", entity.get("prov:type"));
        Map<String, Object> activity = doc.activity().values().iterator().next();
        assertEquals("aml:LedgerEvent", activity.get("prov:type"));
    }
}
