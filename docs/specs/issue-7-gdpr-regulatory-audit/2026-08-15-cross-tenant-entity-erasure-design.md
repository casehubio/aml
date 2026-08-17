# Cross-Tenant Entity Memory Erasure (GDPR Art.17 Multi-Tenant)

**Issue:** #84 — feat: cross-tenant entity memory erasure (GDPR Art.17 multi-tenant)
**Branch:** `issue-7-gdpr-regulatory-audit`
**Date:** 2026-08-15
**Decisions:** D9–D10 in `decisions.md`

---

## Problem Statement

`AmlErasureService.eraseEntity()` hardcodes `TenancyConstants.DEFAULT_TENANT_ID`. The platform provides `CaseMemoryStore.eraseEntityAcrossTenants(entityId, tenantIds)` for multi-tenant GDPR Art.17 requests, but AML has no path to it. With 36+ `DEFAULT_TENANT_ID` call sites already in the codebase, every new caller hardens the single-tenant assumption and makes future multi-tenancy migration harder.

## Scope

**In scope:**
- Parameterise `AmlErasureService.eraseEntity()` with `tenantId` (default overload preserves backwards compatibility)
- Add `AmlErasureService.eraseEntityAcrossTenants()` delegating to platform API
- New `CrossTenantErasureResult` record
- REST endpoint: optional `tenantId` on existing entity erasure endpoint
- New `POST /api/entities/{entityId}/erasure/cross-tenant` endpoint
- Tests for all new paths

**Not in scope:**
- Multi-tenancy infrastructure (tenant discovery, tenant registry)
- Changing the 36 existing `DEFAULT_TENANT_ID` call sites outside the erasure path
- Actor-level erasure (`LedgerErasureService.erase()`) — already tenant-unaware by design (operates on actorId across all tenants)

---

## Design

### Part 1: `AmlErasureService` — Parameterise and Extend

**Existing method — add tenantId overload:**

```java
public EntityErasureResult eraseEntity(String entityId, ErasureReason reason) {
    return eraseEntity(entityId, principal.tenancyId(), reason);
}

public EntityErasureResult eraseEntity(String entityId, String tenantId, ErasureReason reason) {
    int memoriesErased = memoryStore.eraseEntity(entityId, tenantId);
    UUID receiptEntryId = ledgerService.writeEntityErasure(
            entityId, reason, memoriesErased, principal.actorId(), principal.actorType());
    return new EntityErasureResult(entityId, memoriesErased, receiptEntryId);
}
```

The no-arg default uses `principal.tenancyId()` instead of `DEFAULT_TENANT_ID`. In single-tenant mode, `principal.tenancyId()` returns `DEFAULT_TENANT_ID` — behaviour is identical. When multi-tenancy activates, the correct tenant flows through automatically.

**New method — cross-tenant erasure:**

```java
public CrossTenantErasureResult eraseEntityAcrossTenants(
        String entityId, Set<String> tenantIds, ErasureReason reason) {
    int memoriesErased = memoryStore.eraseEntityAcrossTenants(entityId, tenantIds);
    UUID receiptEntryId = ledgerService.writeEntityErasure(
            entityId, reason, memoriesErased, principal.actorId(), principal.actorType());
    return new CrossTenantErasureResult(entityId, tenantIds.size(), memoriesErased, receiptEntryId);
}
```

Cross-tenant authorisation is handled by `CaseMemoryStore.eraseEntityAcrossTenants()` itself — it calls `MemoryPermissions.assertCrossTenantAdmin(principal)`. `AmlErasureService` does not add its own authorisation layer.

### Part 2: `CrossTenantErasureResult` Record (api/ module)

```java
package io.casehub.aml.compliance;

import java.util.UUID;

public record CrossTenantErasureResult(
        String entityId,
        int tenantsRequested,
        int memoriesErased,
        UUID receiptEntryId) {}
```

### Part 3: REST Endpoints

**Existing endpoint update** — add optional `tenantId` query param:

```java
@ApplicationScoped
@Path("/api/entities/{entityId}/erasure")
@Produces(MediaType.APPLICATION_JSON)
class AmlEntityErasureResource {

    @Inject AmlErasureService erasureService;

    @POST
    public EntityErasureResult eraseEntity(
            @PathParam("entityId") String entityId,
            @QueryParam("tenantId") String tenantId) {
        if (tenantId != null) {
            return erasureService.eraseEntity(entityId, tenantId, ErasureReason.GDPR_ART_17_REQUEST);
        }
        return erasureService.eraseEntity(entityId, ErasureReason.GDPR_ART_17_REQUEST);
    }
}
```

**New cross-tenant endpoint:**

```java
@ApplicationScoped
@Path("/api/entities/{entityId}/erasure/cross-tenant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AmlCrossTenantErasureResource {

    @Inject AmlErasureService erasureService;

    @POST
    public CrossTenantErasureResult eraseEntityAcrossTenants(
            @PathParam("entityId") String entityId,
            CrossTenantErasureRequest request) {
        return erasureService.eraseEntityAcrossTenants(
                entityId, request.tenantIds(), ErasureReason.GDPR_ART_17_REQUEST);
    }
}
```

**Request body:**

```java
package io.casehub.aml.compliance;

import java.util.Set;

public record CrossTenantErasureRequest(Set<String> tenantIds) {}
```

---

## Test Specification

### Unit tests (app/ module, plain Mockito)

- `AmlErasureServiceTest` additions:
  - `eraseEntity_defaultOverload_usesPrincipalTenancyId` — verify `memoryStore.eraseEntity()` called with `principal.tenancyId()`, not hardcoded `DEFAULT_TENANT_ID`
  - `eraseEntity_explicitTenantId_passedThrough` — verify explicit tenantId reaches `memoryStore.eraseEntity()`
  - `eraseEntityAcrossTenants_delegatesToMemoryStore` — verify `memoryStore.eraseEntityAcrossTenants()` called with provided tenant set
  - `eraseEntityAcrossTenants_writesReceiptWithTotalCount` — verify receipt includes total memories erased across all tenants

### Unit tests (api/ module)

- `CrossTenantErasureResultTest` — construction and accessor verification

### @QuarkusTest (app/ module)

- `AmlLayer7ErasureTest` additions:
  - `eraseEntity_withTenantIdParam_returns200` — `POST /api/entities/ACCT-1/erasure?tenantId=default`
  - `eraseEntityAcrossTenants_returns200` — `POST /api/entities/ACCT-1/erasure/cross-tenant` with `{"tenantIds": ["tenant-a", "tenant-b"]}`
  - `eraseEntityAcrossTenants_emptyTenantSet_returns400or200` — verify edge case handling

---

## Protocol Compliance

| Protocol | Status |
|----------|--------|
| api/ module purity | Compliant — `CrossTenantErasureResult`, `CrossTenantErasureRequest` are pure Java records |
| Platform coherence | Delegates to existing `CaseMemoryStore.eraseEntityAcrossTenants()` — no duplication |
| Authorisation model | Inherited from platform — `MemoryPermissions.assertCrossTenantAdmin(principal)` |

## Platform Coherence

- No new foundation types — uses existing `CaseMemoryStore.eraseEntityAcrossTenants()`
- Authorisation model delegated to platform — no AML-specific admin checks
- REST endpoint pattern follows existing `AmlEntityErasureResource`
