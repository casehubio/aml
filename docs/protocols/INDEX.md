# casehub-aml Protocol Index

Standing architectural rules for casehub-aml. Read before implementing new features, classifiers, or ledger entries.

Platform-level protocols (applicable across all casehub repos) live in `casehub/garden/docs/protocols/`.

---

## Application Tier

Rules for casehub-aml as an app built on the CaseHub platform.

| Protocol | Rule Summary | Applies To |
|----------|-------------|------------|
| [action-risk-classifier-fail-closed-metadata](application/action-risk-classifier-fail-closed-metadata.md) | Fail-closed paths derive all gate metadata from the domain type | `ActionRiskClassifier` — missingContext path |
| [tenant-principal-exclusion](application/tenant-principal-exclusion.md) | Exclude `TenantScopedPrincipal` from AML CDI — both main and test properties | `application.properties` (both scopes) |

→ Full list: [application/INDEX.md](application/INDEX.md)

---

## AML Domain

Rules specific to casehub-aml's domain implementation.

| Protocol | Rule Summary | Applies To |
|----------|-------------|------------|
| [aml-ledger-entry-tenancy-id-non-null](aml/aml-ledger-entry-tenancy-id-non-null.md) | AML ledger entry writes must guarantee non-null tenancyId | Any class writing an AML `LedgerEntry` subclass |

→ Full list: [aml/INDEX.md](aml/INDEX.md)
