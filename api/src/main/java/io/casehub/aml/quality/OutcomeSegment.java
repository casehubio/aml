package io.casehub.aml.quality;

public record OutcomeSegment(
        int total,
        int upheld,
        int notUpheld,
        double upheldRate
) {
    public static OutcomeSegment of(int upheld, int notUpheld) {
        int total = upheld + notUpheld;
        double rate = total > 0 ? (double) upheld / total : 0.0;
        return new OutcomeSegment(total, upheld, notUpheld, rate);
    }
}
