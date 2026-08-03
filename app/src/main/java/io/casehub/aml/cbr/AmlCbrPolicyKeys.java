package io.casehub.aml.cbr;

import io.casehub.api.spi.routing.DoublePreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class AmlCbrPolicyKeys {
    private static final String NS = "casehubio.aml.cbr";

    public static final PreferenceKey<DoublePreference> ACTIVATION_THRESHOLD =
            new PreferenceKey<>(NS, "activation-threshold",
                    DoublePreference.of(30.0), DoublePreference::parse);

    private AmlCbrPolicyKeys() {}
}
