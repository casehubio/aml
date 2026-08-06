package io.casehub.aml.investigation;

import io.casehub.api.spi.routing.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class AmlNarrativePolicyKeys {
    private static final String NS = "casehubio.aml.cbr.narrative";

    public static final PreferenceKey<IntPreference> MAX_SEEDS =
            new PreferenceKey<>(NS, "max-seeds", IntPreference.of(3), IntPreference::parse);

    public static final PreferenceKey<IntPreference> MAX_SEED_LENGTH =
            new PreferenceKey<>(NS, "max-seed-length", IntPreference.of(2000), IntPreference::parse);

    private AmlNarrativePolicyKeys() {}
}
