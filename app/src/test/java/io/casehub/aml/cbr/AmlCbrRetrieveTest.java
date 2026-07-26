package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.restassured.http.ContentType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AmlCbrRetrieveTest {

    @Inject
    CbrCaseMemoryStore cbrStore;
    @Inject
    CaseInstanceCache  caseInstanceCache;

    @Test
    void similar_case_appears_in_context_after_investigation_start() {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put("flag_reason", FeatureValue.string("STRUCTURING"));
        features.put("transaction_amount", FeatureValue.number(50000.0));
        features.put("prior_incident_count", FeatureValue.number(2));
        features.put("entity_type", FeatureValue.string("CORPORATE"));

        var pastCase = new PlanCbrCase(
                "Past structuring case TX-PAST-001",
                "entity-resolution→entity-resolution-agent(SUCCESS)",
                "SAR_WARRANTED", 0.87, features,
                List.of(new PlanTrace("entity-resolution", "entity-resolution",
                        "entity-resolution-agent", "SUCCESS", 0, Map.of())),
                null, null);

        String entityId = UUID.nameUUIDFromBytes(
                "aml-cbr:test-past-case".getBytes()).toString();
        cbrStore.store(pastCase, AmlCbrSchema.CASE_TYPE, entityId,
                       AmlMemoryDomains.CBR, TenancyConstants.DEFAULT_TENANT_ID,
                       "test-past-case", Path.root());

        var tx = new SuspiciousTransaction(
                "TXN-CBR-" + UUID.randomUUID(),
                "ACC-CBR-A", "ACC-CBR-B",
                new BigDecimal("55000"), "USD",
                Instant.now(), FlagReason.STRUCTURING);

        String caseIdStr = given().contentType(ContentType.JSON).body(tx)
                                  .when().post("/api/layer6/investigations")
                                  .then().statusCode(202)
                                  .extract().path("caseId");
        UUID caseId = UUID.fromString(caseIdStr);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .pollInterval(200, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> {
                      CaseInstance instance = caseInstanceCache.get(caseId);
                      assertThat(instance).isNotNull();
                      Object experiences = instance.getCaseContext().get("cbrExperiences");
                      assertThat(experiences).as("cbrExperiences should be present").isNotNull();

                      @SuppressWarnings("unchecked")
                      var list = (List<Map<String, Object>>) experiences;
                      assertThat(list).isNotEmpty();
                      assertThat(list.get(0)).containsEntry("outcome", "SAR_WARRANTED");
                      assertThat((double) list.get(0).get("similarityScore"))
                              .isGreaterThan(0.0);
                  });
    }

    @Test
    void empty_case_base_produces_no_experiences() {
        var tx = new SuspiciousTransaction(
                "TXN-EMPTY-" + UUID.randomUUID(),
                "ACC-EMPTY-A", "ACC-EMPTY-B",
                new BigDecimal("10000"), "USD",
                Instant.now(), FlagReason.VELOCITY_ANOMALY);

        String caseIdStr = given().contentType(ContentType.JSON).body(tx)
                                  .when().post("/api/layer6/investigations")
                                  .then().statusCode(202)
                                  .extract().path("caseId");
        UUID caseId = UUID.fromString(caseIdStr);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .pollInterval(200, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> {
                      CaseInstance instance = caseInstanceCache.get(caseId);
                      assertThat(instance).isNotNull();
                      Object experiences = instance.getCaseContext().get("cbrExperiences");
                      assertThat(experiences).as("cbrExperiences should be absent for empty case base").isNull();
                  });
    }
}
