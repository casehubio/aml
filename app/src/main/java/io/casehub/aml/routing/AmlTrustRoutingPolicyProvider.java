package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyKeys;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.api.spi.routing.TrustRoutingPolicyResolver;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class AmlTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    static final TrustRoutingPolicyKeys KEYS =
            TrustRoutingPolicyKeys.create("casehubio.aml.trust-routing")
                    .withFloor("investigation-accuracy", "investigation-accuracy");

    private final PreferenceProvider preferenceProvider;

    @Override
    public String id() {
        return "aml";
    }

    @Inject
    public AmlTrustRoutingPolicyProvider(final PreferenceProvider preferenceProvider) {
        this.preferenceProvider = preferenceProvider;
    }

    @Override
    public TrustRoutingPolicy forCapability(final String capabilityName) {
        return TrustRoutingPolicyResolver.resolve(
                preferenceProvider.resolve(
                        SettingsScope.of(io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID,
                                         io.casehub.platform.api.path.Path.of("casehubio", "aml", "trust-routing", capabilityName))),
                KEYS);
    }

    public Set<String> capabilities() {
        return Set.of("entity-resolution", "pattern-analysis", "osint-screening",
                      "sar-drafting", "senior-analyst-review");
    }
}
