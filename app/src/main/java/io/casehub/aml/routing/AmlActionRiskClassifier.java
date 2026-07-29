package io.casehub.aml.routing;

import io.casehub.aml.domain.AmlActionType;
import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.routing.StaticSetStrategy;
import io.casehub.worker.api.PlannedAction;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AML-specific {@link ActionRiskClassifier} — discovered by casehub-engine via
 * {@link RiskClassifier} CDI qualifier. Encodes FinCEN/FCA regulatory gate requirements
 * for consequential AML actions.
 *
 * <p>Fail-closed for known types with missing context: if a recognised consequential action type
 * arrives without the context needed to assess risk, the action is gated for human review rather
 * than allowed to proceed unreviewed.
 *
 * <p>Unknown {@code actionType} → {@link RiskDecision.Autonomous} — this classifier does not
 * gate actions it does not own.
 */
@ApplicationScoped
@RiskClassifier
public class AmlActionRiskClassifier implements ActionRiskClassifier {

    static final double RISK_SCORE_GATE_THRESHOLD = 0.8;
    static final double CONFIDENCE_GATE_THRESHOLD = 0.9;

    @Override
    public RiskDecision classify(final PlannedAction action, final ClassificationContext context) {
        final Optional<AmlActionType> typeOpt = AmlActionType.fromActionType(action.actionType());
        if (typeOpt.isEmpty()) {
            return new RiskDecision.Autonomous();
        }
        final AmlActionType type = typeOpt.get();
        return switch (type.gatePolicy()) {
            case ALWAYS -> gate(type);
            case RISK_SCORE_THRESHOLD -> classifyByRiskScore(type, action.parameters());
            case CONFIDENCE_THRESHOLD -> classifyByConfidence(type, action.parameters());
        };
    }

    private RiskDecision classifyByRiskScore(final AmlActionType type, final Map<String, Object> context) {
        if (context != null && "PEP".equals(context.get("entityType"))) {
            return gate(type);
        }
        final Object raw = context != null ? context.get("riskScore") : null;
        if (raw == null) return missingContext(type);
        try {
            final double score = Double.parseDouble(raw.toString());
            return score >= RISK_SCORE_GATE_THRESHOLD ? gate(type) : new RiskDecision.Autonomous();
        } catch (final NumberFormatException e) {
            return missingContext(type);
        }
    }

    private RiskDecision classifyByConfidence(final AmlActionType type, final Map<String, Object> context) {
        final Object raw = context != null ? context.get("confidenceScore") : null;
        if (raw == null) return missingContext(type);
        try {
            final double score = Double.parseDouble(raw.toString());
            return score < CONFIDENCE_GATE_THRESHOLD ? gate(type) : new RiskDecision.Autonomous();
        } catch (final NumberFormatException e) {
            return missingContext(type);
        }
    }

    private RiskDecision.GateRequired gate(final AmlActionType type) {
        return new RiskDecision.GateRequired(
                type.reason(), type.reversible(), StaticSetStrategy.of(Set.copyOf(type.candidateGroups())),
                type.expiresIn(), type.scope(), null, null);}

    private RiskDecision.GateRequired missingContext(final AmlActionType type) {
        return new RiskDecision.GateRequired(
                "Risk assessment unavailable — human review required",
                type.reversible(), StaticSetStrategy.of(Set.copyOf(type.candidateGroups())), null, type.scope(), null, null);}
}
