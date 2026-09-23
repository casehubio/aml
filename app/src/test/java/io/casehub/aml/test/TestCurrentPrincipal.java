package io.casehub.aml.test;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Set;

@Alternative
@Priority(200)
@ApplicationScoped
public class TestCurrentPrincipal implements CurrentPrincipal {

    @Inject Instance<SecurityIdentity> identity;

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }

    @Override
    public String actorId() {
        try {
            SecurityIdentity si = identity.get();
            if (si != null && si.getPrincipal() != null) {
                return si.getPrincipal().getName();
            }
        } catch (Exception ignored) {
        }
        return "test-system";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }
}
