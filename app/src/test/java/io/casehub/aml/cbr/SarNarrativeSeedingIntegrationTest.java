package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SarNarrativeSeedingIntegrationTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @Inject AmlEngineCoordinator  coordinator;
    @Inject CbrCaseMemoryStore    cbrStore;
    @Inject LedgerEntryRepository ledgerRepository;
    @Inject WorkItemService       workItemService;
    @PersistenceContext EntityManager defaultEm;

    @Test
    @Order(2)
    void seededInvestigation_narrativeSeededTrue() {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put("flag_reason", FeatureValue.string("HIGH_RISK_JURISDICTION"));
        features.put("transaction_amount", FeatureValue.number(60000.0));
        features.put("prior_incident_count", FeatureValue.number(1));
        features.put("entity_type", FeatureValue.string("SHELL_COMPANY"));
        features.put("sar_narrative", FeatureValue.string("Past SAR narrative for structuring via shell company"));

        var pastCase = new PlanCbrCase(
                "Past structuring case TX-SEED-PAST",
                "entity-resolution→er-agent(SUCCESS), sar-drafting→sar-agent(SUCCESS)",
                "SAR_WARRANTED", 0.9, features,
                List.of(new PlanTrace("entity-resolution", "entity-resolution",
                        "er-agent", "SUCCESS", 0, Map.of(), null)),
                null, null);

        String entityId = UUID.nameUUIDFromBytes("aml-cbr:seed-test-past".getBytes()).toString();
        cbrStore.store(pastCase, AmlCbrSchema.CASE_TYPE, entityId,
                       AmlMemoryDomains.CBR, TENANT, "seed-test-past", Path.root());

        var tx = new SuspiciousTransaction(
                "TXN-SEED-" + UUID.randomUUID(),
                "ACC-SEED-A-" + UUID.randomUUID(),
                "ACC-SEED-B-" + UUID.randomUUID(),
                new BigDecimal("62000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);

        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var ledgerEntries = ledgerRepository.findBySubjectId(caseId, TENANT);
        var profileEntry = ledgerEntries.stream()
                .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                .map(AmlCaseProfileLedgerEntry.class::cast)
                .findFirst().orElse(null);

        assertNotNull(profileEntry, "AmlCaseProfileLedgerEntry must be written");
        assertEquals(Boolean.TRUE, profileEntry.narrativeSeeded,
                "narrativeSeeded must be true when seed narratives were available");
        assertNotNull(profileEntry.seedCount);
        assertTrue(profileEntry.seedCount > 0, "seedCount must be > 0");
    }

    @Test
    @Order(1)
    void coldStart_narrativeSeededFalse() {
        cbrStore.eraseByScope(io.casehub.platform.api.path.Path.root(), TENANT);
        var tx = new SuspiciousTransaction(
                "TXN-COLD-" + UUID.randomUUID(),
                "ACC-COLD-A-" + UUID.randomUUID(),
                "ACC-COLD-B-" + UUID.randomUUID(),
                new BigDecimal("15000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);

        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var ledgerEntries = ledgerRepository.findBySubjectId(caseId, TENANT);
        var profileEntry = ledgerEntries.stream()
                .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                .map(AmlCaseProfileLedgerEntry.class::cast)
                .findFirst().orElse(null);

        assertNotNull(profileEntry, "AmlCaseProfileLedgerEntry must be written");
        assertEquals(Boolean.FALSE, profileEntry.narrativeSeeded,
                "narrativeSeeded must be false with empty case base");
        assertEquals(Integer.valueOf(0), profileEntry.seedCount,
                "seedCount must be 0 with empty case base");
    }

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                defaultEm.createQuery(
                                "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                                WorkItem.class)
                        .setParameter("pattern", "case:" + caseId + "/gate:%")
                        .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private void drain(final UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100))
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseId)
                                .then().extract().path("status")));
    }
}
