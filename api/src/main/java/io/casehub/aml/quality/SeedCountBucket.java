package io.casehub.aml.quality;

public record SeedCountBucket(
        String range,
        int total,
        double upheldRate
) {}
