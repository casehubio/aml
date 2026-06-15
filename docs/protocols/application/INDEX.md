# Application-Tier Protocols

Rules for casehub-aml as an application built on the CaseHub platform foundation.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [action-risk-classifier-fail-closed-metadata.md](action-risk-classifier-fail-closed-metadata.md) | Fail-closed classifier paths must derive all gate metadata from the domain action type | Any `ActionRiskClassifier` implementation — fail-closed / missingContext path |
| [tenant-principal-exclusion.md](tenant-principal-exclusion.md) | Exclude `TenantScopedPrincipal` from AML CDI in both main and test properties | `app/src/main/resources/application.properties`, `app/src/test/resources/application.properties` |
