package io.casehub.aml.engine;

import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Service for retrieving prior entity context used when an AML investigation started.
 *
 * <p>Queries the case's {@code priorEntityContext} from the engine runtime, which was
 * populated by {@link io.casehub.aml.memory.AmlMemoryService} before the investigation
 * started. The returned Map contains historical facts about entities, networks, and
 * patterns observed in prior investigations.
 */
@ApplicationScoped
public class AmlInvestigationPriorContextService {

    @Inject
    CaseHubRuntime runtime;

    /**
     * Retrieves the prior entity context for a case.
     *
     * @param caseId The investigation case ID
     * @return The prior context map from {@link io.casehub.aml.memory.AmlPriorContext#toContextMap()}
     * @throws NotFoundException if the case doesn't exist or has no prior context
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPriorContext(UUID caseId) {
        try {
            Object result = runtime.query(caseId, "priorEntityContext")
                .toCompletableFuture()
                .join();

            if (result == null) {
                throw new NotFoundException("Investigation not found or has no prior context: " + caseId);
            }

            return (Map<String, Object>) result;
        } catch (CompletionException e) {
            // Unwrap the cause
            Throwable cause = e.getCause();

            // If it's a "Case instance not found" exception, convert to 404
            if (cause instanceof RuntimeException &&
                cause.getMessage() != null &&
                cause.getMessage().contains("Case instance not found")) {
                throw new NotFoundException("Investigation not found: " + caseId);
            }

            // Otherwise rethrow as-is
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
