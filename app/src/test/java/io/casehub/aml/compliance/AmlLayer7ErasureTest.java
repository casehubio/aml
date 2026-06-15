package io.casehub.aml.compliance;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Layer 7: verifies GDPR erasure endpoint wiring.
 *
 * <p>Ledger SNAPSHOT feat(#130): SYSTEM and AGENT actors are exempt from tokenisation
 * (not natural persons, no GDPR obligation). Only HUMAN actors are pseudonymised.
 * Human-actor erasure (officer ID) requires a full compliance-officer-review flow
 * and is covered by AmlLayer7ResourceTest.gdprDemoFlow_officerReview_erasure.
 */
@QuarkusTest
class AmlLayer7ErasureTest {

    @Test
    void eraseActor_systemActor_notTokenised_returnsMappingFalse() {
        // aml-orchestrator is ActorType.SYSTEM — exempt from tokenisation since ledger#130.
        // Erasure returns mappingFound=false: no token mapping exists for SYSTEM actors.
        given().contentType(ContentType.JSON).when()
            .post("/api/actors/{actorId}/erasure", "aml-orchestrator")
            .then().statusCode(200)
            .body("rawActorId", equalTo("aml-orchestrator"))
            .body("mappingFound", is(false))
            .body("affectedEntryCount", equalTo(0));
    }

    @Test
    void eraseActor_unknownActor_returnsMappingFalse() {
        given().contentType(ContentType.JSON).when()
            .post("/api/actors/{actorId}/erasure", "actor-that-does-not-exist")
            .then().statusCode(200)
            .body("mappingFound", is(false))
            .body("affectedEntryCount", equalTo(0));
    }
}
