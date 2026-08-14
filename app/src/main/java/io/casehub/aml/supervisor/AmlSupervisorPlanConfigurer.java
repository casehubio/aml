package io.casehub.aml.supervisor;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.planning.control.BlackboardPlanConfigurer;
import io.casehub.engine.planning.plan.CasePlanModel;
import io.casehub.engine.planning.plan.PlanItemDefinition;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AmlSupervisorPlanConfigurer implements BlackboardPlanConfigurer {

    private static final String AML_INVESTIGATION = "aml-investigation";

    @Override
    public boolean supports(CaseDefinition definition) {
        return definition != null
                && AML_INVESTIGATION.equals(definition.getName());
    }

    @Override
    public void configure(CasePlanModel plan, PlanExecutionContext context) {
        var compound = PlanItemDefinition.Compound.builder("supervised-investigation")
                .planningStrategy("aml-supervisor")
                .binding("pattern-analysis")
                .binding("osint-screening")
                .binding("investigation-triage")
                .binding("sar-drafting")
                .binding("senior-analyst-required-resolution")
                .build();
        plan.registerDefinition(compound);
    }
}
