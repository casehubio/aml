---
id: PP-20260615-d274cc
title: "Exclude TenantScopedPrincipal from AML CDI — Qhorus-based architecture"
type: rule
scope: application
applies_to: "app/src/main/resources/application.properties and app/src/test/resources/application.properties"
severity: important
refs:
  - CLAUDE.md
violation_hint: "mvn verify fails with 'Ambiguous dependencies for type CurrentPrincipal' listing TenantScopedPrincipal as an available bean; or @QuarkusTest passes but mvn verify CDI validation fails"
created: 2026-06-15
---

`TenantScopedPrincipal` from casehub-work must be excluded from AML's CDI graph in **both** `src/main/resources/application.properties` and `src/test/resources/application.properties`. AML uses `QhorusInboundCurrentPrincipal` for tenant context (Qhorus-based architecture). `TenantScopedPrincipal` conflicts as a second non-`@DefaultBean` `CurrentPrincipal` implementation alongside `QhorusInboundCurrentPrincipal`, creating an ambiguity that blocks CDI deployment. The main-properties exclusion is required because `quarkus:build` (invoked by `mvn verify`) validates CDI against the production classpath only — test `application.properties` is not loaded at that phase.
