package io.casehub.aml.compliance;

import java.util.Set;

public record CrossTenantErasureRequest(Set<String> tenantIds) {}
