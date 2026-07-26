package io.casehub.aml.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.SuspiciousTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Layer 9 coordinator — starts an oversight investigation case and returns its UUID.
 *
 * <p>Minimal by design: no memory query (oversight investigations have no prior context),
 * no ledger write (the gate WorkItem created by {@code ActionGateWorkItemHandler} is the
 * audit artefact for this layer). Case start is the only side effect.
 */
@ApplicationScoped
public class AmlOversightCoordinator {

    private static final Logger LOG = Logger.getLogger(AmlOversightCoordinator.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int CASE_START_TIMEOUT_SECONDS = 5;

    @Inject AmlOversightCaseHub caseHub;
    @Inject ObjectMapper objectMapper;

    public UUID startInvestigation(final SuspiciousTransaction transaction) {
        final Map<String, Object> initialContext = Map.of(
                "transaction", objectMapper.convertValue(transaction, MAP_TYPE));
        try {
            final UUID caseId = caseHub.startCase(initialContext);
            LOG.infof("Oversight investigation started: caseId=%s txId=%s", caseId, transaction.id());
            return caseId;
        } catch (final Exception e) {
            LOG.errorf(e, "Failed to start oversight investigation for transaction %s", transaction.id());
            throw new RuntimeException("Failed to start oversight investigation", e);
        }
    }
}
