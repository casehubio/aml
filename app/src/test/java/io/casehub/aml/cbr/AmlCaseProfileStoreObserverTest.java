package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class AmlCaseProfileStoreObserverTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;
    @Inject
    AmlEngineCoordinator  coordinator;
    @Inject
    CbrCaseMemoryStore    cbrStore;
    @Inject
    LedgerEntryRepository ledgerRepository;

    @org.junit.jupiter.api.BeforeEach
    void clearCbrStore() {
        cbrStore.eraseByScope(io.casehub.platform.api.path.Path.root(), TENANT);
    }
    @Inject
    WorkItemService       workItemService;
    @PersistenceContext
    EntityManager         defaultEm;

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
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                  .until(() -> "completed".equals(
                          given().when().get("/api/layer6/investigations/" + caseId)
                                 .then().extract().path("status")));
    }

    @Test
    void onCaseOutcome_storesProfileAndLedgerEntry() {
        Instant before = Instant.now();
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-CBR-001-" + UUID.randomUUID(),
                "ACC-CBR-ORIGIN-" + UUID.randomUUID(),
                "ACC-CBR-DEST-" + UUID.randomUUID(),
                new BigDecimal("75000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);
        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var ledgerEntries = ledgerRepository.findBySubjectId(caseId, TENANT);
        var profileEntry = ledgerEntries.stream()
                                        .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                                        .map(AmlCaseProfileLedgerEntry.class::cast)
                                        .findFirst()
                                        .orElse(null);

        assertNotNull(profileEntry, "AmlCaseProfileLedgerEntry must be written");
        assertEquals("HIGH_RISK_JURISDICTION", profileEntry.flagReason);
        assertEquals(0, new BigDecimal("75000").compareTo(profileEntry.transactionAmount));
        assertEquals("SAR_WARRANTED", profileEntry.outcome);
        assertNull(profileEntry.confidence);
        assertNotNull(profileEntry.investigationPath);
        assertFalse(profileEntry.investigationPath.isBlank());
    }

    @Test
    void onCaseOutcome_cbrStoreContainsPlanCbrCase() {
        Instant before = Instant.now();
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-CBR-002-" + UUID.randomUUID(),
                "ACC-CBR-ORIGIN2-" + UUID.randomUUID(),
                "ACC-CBR-DEST2-" + UUID.randomUUID(),
                new BigDecimal("50000"), "USD", Instant.now(), FlagReason.HIGH_RISK_JURISDICTION);
        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        var query = CbrQuery.of(TENANT, io.casehub.aml.memory.AmlMemoryDomains.CBR,
                                io.casehub.platform.api.path.Path.root(),
                                AmlCbrSchema.CASE_TYPE,
                                Map.of("flag_reason", FeatureValue.string("HIGH_RISK_JURISDICTION")), 10)
                            .withWeights(AmlCbrSchema.WEIGHTS)
                            .withNotBefore(before);

        var results = cbrStore.retrieveSimilar(query, PlanCbrCase.class);
        assertFalse(results.isEmpty(), "Should find at least one CBR case");
        var match = results.stream()
                           .filter(r -> "SAR_WARRANTED".equals(r.cbrCase().outcome()))
                           .findFirst().orElse(null);
        assertNotNull(match, "CBR store must contain a PlanCbrCase with SAR_WARRANTED outcome");
        assertNotNull(match.cbrCase().planTrace(), "PlanCbrCase must have planTrace");
    }
}
