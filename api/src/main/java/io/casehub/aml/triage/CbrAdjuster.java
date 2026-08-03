package io.casehub.aml.triage;

import io.casehub.aml.domain.CbrPathAdvice;

import java.util.logging.Logger;

public final class CbrAdjuster {

    private static final Logger LOG = Logger.getLogger(CbrAdjuster.class.getName());

    private final double maxAdjustment;
    private final double minConfidence;

    public CbrAdjuster(double maxAdjustment, double minConfidence) {
        this.maxAdjustment = maxAdjustment;
        this.minConfidence = minConfidence;
    }

    public record AdjustedThresholds(double sarThreshold, double fpThreshold, Double adjustment) {}

    public AdjustedThresholds adjust(double sarThreshold, double fpThreshold, CbrPathAdvice cbr) {
        if (cbr == null || cbr.caseCount() == 0 || cbr.confidence() < minConfidence
                || cbr.error() || !cbr.active() || cbr.predominantOutcome() == null) {
            return new AdjustedThresholds(sarThreshold, fpThreshold, null);
        }

        String outcome = cbr.predominantOutcome();
        if ("INCONCLUSIVE".equals(outcome)) {
            return new AdjustedThresholds(sarThreshold, fpThreshold, null);
        }

        double freq = cbr.predominantOutcomeFrequency() != null ? cbr.predominantOutcomeFrequency() : 0.0;
        double magnitude = Math.min(cbr.confidence() * freq * maxAdjustment, maxAdjustment);

        double adjustedSar;
        double adjustedFp;
        if ("FALSE_POSITIVE".equals(outcome)) {
            adjustedSar = sarThreshold + magnitude;
            adjustedFp = fpThreshold + magnitude;
        } else if ("SAR_WARRANTED".equals(outcome)) {
            adjustedSar = sarThreshold - magnitude;
            adjustedFp = fpThreshold - magnitude;
        } else {
            return new AdjustedThresholds(sarThreshold, fpThreshold, null);
        }

        if (adjustedSar <= adjustedFp) {
            double mid = (adjustedSar + adjustedFp) / 2.0;
            adjustedSar = mid + 0.05;
            adjustedFp = mid - 0.05;
            LOG.warning("CBR threshold ordering violated — clamped to midpoint ± 0.05");
        }

        return new AdjustedThresholds(adjustedSar, adjustedFp, magnitude);
    }
}
