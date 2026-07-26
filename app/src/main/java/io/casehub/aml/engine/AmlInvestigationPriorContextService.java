package io.casehub.aml.engine;

import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.UUID;

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

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPriorContext(UUID caseId) {
        try {
            Object result = runtime.query(caseId, "priorEntityContext");
            if (result == null) {
                throw new NotFoundException("Investigation not found or has no prior context: " + caseId);
            }
            return (Map<String, Object>) result;
        } catch (NotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Case instance not found")) {
                throw new NotFoundException("Investigation not found: " + caseId);
            }
            throw e;
        }
    }
}
