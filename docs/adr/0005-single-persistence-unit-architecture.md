# ADR-0005: Single Persistence Unit Architecture

**Status:** Accepted
**Date:** 2026-09-20
**Context:** aml#10, qhorus Panache→JPA migration, Quarkus 3.39 upgrade

## Decision

AML uses a single default Hibernate persistence unit for ALL entities (work, qhorus, ledger, AML). Named persistence units (`qhorus`) are removed.

Named datasources (`qhorus`, `memory`) remain as aliases to the same H2 database — required by named Flyway instances for V-number isolation. Each Flyway instance has its own `flyway_schema_history_*` table.

## Context

After the qhorus Panache→JPA migration, qhorus stores inject `@Default EntityManager` (default PU). The qhorus deployment processor registers entities via `AdditionalJpaModelBuildItem`, which only targets the default PU. Consumer apps with a named `qhorus` PU don't discover the entities.

Previously AML used two PUs:
- Default: work + AML entities
- Named `qhorus`: qhorus + ledger entities

This broke when `AdditionalJpaModelBuildItem` registered qhorus entities on the default PU but AML's named PU expected them on `qhorus`.

## Consequences

- All entities on one PU — entity name collisions possible. AML's `TrustScoreSnapshot` renamed to `AmlTrustScoreSnapshot` to avoid collision with `io.casehub.ledger.runtime.model.TrustScoreSnapshot`.
- Flyway V-number isolation preserved via separate named Flyway instances with separate history tables.
- `baseline-on-migrate=true` + `baseline-version=0` required on named Flyway instances to handle non-empty schema from other Flyway's migrations.
- Entity package list in `hibernate-orm.packages` is long — includes all qhorus subpackages explicitly (Quarkus does exact package matching, not prefix).

## Alternatives Considered

- **Keep named PU + fix entity discovery** — would require Quarkus extension changes to scope `AdditionalJpaModelBuildItem` to named PUs. Not supported by Quarkus API.
- **Single datasource + single Flyway** — V-number collisions (work V1, qhorus V1, memory V1). Would require renumbering all foundation migrations.
