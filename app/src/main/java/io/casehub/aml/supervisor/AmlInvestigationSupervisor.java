package io.casehub.aml.supervisor;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.engine.planning.control.PlanningStrategy;
import io.casehub.engine.planning.plan.CasePlanModel;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Unremovable
public class AmlInvestigationSupervisor implements PlanningStrategy {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationSupervisor.class);

    private final AmlSupervisorLlmAdapter llmAdapter;
    private final AmlSupervisorPendingStore pendingStore;

    @Inject
    public AmlInvestigationSupervisor(
            AmlSupervisorLlmAdapter llmAdapter,
            AmlSupervisorPendingStore pendingStore) {
        this.llmAdapter = llmAdapter;
        this.pendingStore = pendingStore;
    }

    @Override
    public String id() {
        return "aml-supervisor";
    }

    @Override
    public String getName() {
        return "AML Investigation LLM Supervisor";
    }

    @Override
    public List<Binding> select(
            CasePlanModel plan, PlanExecutionContext context, List<Binding> eligible) {
        if (!llmAdapter.isAvailable() || eligible.isEmpty()) {
            return eligible;
        }
        try {
            SupervisorDecision decision = llmAdapter.consult(plan, context, eligible);
            List<Binding> selected = validateAndResolve(decision, eligible);
            pendingStore.put(new PendingSupervisorDecision(
                    decision, eligible.size(), false,
                    context.caseId(), context.tenancyId()));
            return selected;
        } catch (Exception e) {
            LOG.warnf(e, "Supervisor degraded for case %s: %s",
                    context.caseId(), e.getMessage());
            return degradedFallback(eligible, context);
        }
    }

    private List<Binding> validateAndResolve(
            SupervisorDecision decision, List<Binding> eligible) {
        Set<String> eligibleNames = eligible.stream()
                .map(Binding::getName)
                .collect(Collectors.toSet());
        for (String name : decision.selectedBindings()) {
            if (!eligibleNames.contains(name)) {
                throw new InvalidSupervisorResponseException(
                        "LLM selected non-eligible binding: " + name);
            }
        }
        return eligible.stream()
                .filter(b -> decision.selectedBindings().contains(b.getName()))
                .toList();
    }

    private List<Binding> degradedFallback(
            List<Binding> eligible, PlanExecutionContext context) {
        List<String> allNames = eligible.stream()
                .map(Binding::getName).toList();
        var degradedDecision = new SupervisorDecision(
                allNames, List.of(),
                "LLM unavailable — degraded to choreography", false);
        pendingStore.put(new PendingSupervisorDecision(
                degradedDecision, eligible.size(), true,
                context.caseId(), context.tenancyId()));
        return eligible;
    }
}
