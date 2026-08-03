# Cold-Start CBR Seeding — Design Spec

**Issue:** #99 (cold-start case base seeding for CBR bootstrap)
**Epic:** #92 (Case-Based Reasoning)
**Date:** 2026-08-03

## Context

CBR Retrieve (#95), Retain (#97), Reuse (#96), and SAR narrative seeding (#98)
are complete. The CBR pipeline works end-to-end: cases are retained on completion,
retrieved at startup, analysed by the path advisor, and fed into triage and routing.

The gap: with an empty case base, CBR does nothing. The advisor never fires
(no `cbrExperiences`), and the activation pipeline sits idle until enough real
investigations complete and retain cases organically. For development and demos,
this bootstrapping period is unacceptable. For production, CBR should observe
without influencing routing until it has enough data to be trustworthy.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Seeding mechanism | Direct `CbrCaseMemoryStore.store()` | Fast (milliseconds), controllable coverage, no engine pipeline overhead. Plan traces are structurally deterministic from the YAML bindings — identical shape to real cases |
| Learning mode control | Advisor-driven activation (Approach A) | Self-activating, per-cluster by construction (retrieval IS the cluster), no engine changes, clean data contract |
| Activation threshold scope | Per-retrieval, not global | CBR retrieval returns only cases similar to the current investigation. `caseCount` from retrieval inherently reflects cluster density — no separate clustering mechanism needed |
| Threshold configuration | `PreferenceProvider` | Same pattern as triage thresholds (`AmlTriagePolicyKeys`). Externally configurable, tenant-scoped |
| Bootstrap report data source | Ledger entries, not CBR store | `CbrCaseMemoryStore` has no `count()` or `listAll()` API. Profile ledger entries are a 1:1 proxy (one per retained case), queryable via JPA |
| Report endpoint gating | Not simulation-gated | Operational diagnostic — needed in any environment, not just dev/demo |
| Synthetic case shape | `PlanCbrCase` via `CaseProfile.toFeatures()` | Same domain types and feature paths as `AmlCaseProfileStoreObserver`. No drift between synthetic and real case shapes |

## §1 — Activation Threshold & Learning Mode

### `CbrPathAdvice` gains `active` field

```java
public record CbrPathAdvice(
        int caseCount,
        double avgSimilarity,
        double confidence,
        String predominantOutcome,
        Double predominantOutcomeFrequency,
        boolean error,
        boolean active) {}
```

The advisor computes `active = caseCount >= activationThreshold`. When
`active` is false, CBR is in learning mode — it logged what it would have
recommended, but downstream consumers ignore it.

### Preference key

New `AmlCbrPolicyKeys` class in `app/.../cbr/`:

```java
public final class AmlCbrPolicyKeys {
    public static final PreferenceKey<DoublePreference> ACTIVATION_THRESHOLD =
        PreferenceKey.of("casehub.aml.cbr.activation-threshold",
                         DoublePreference.class);
    private AmlCbrPolicyKeys() {}
}
```

Default value: 30 (resolved in the advisor worker when preference is absent).
Stored as `DoublePreference` for consistency with `AmlTriagePolicyKeys` — cast
to `int` for comparison with `caseCount`.

### Advisor changes (`CbrPathAdvisorWorker`)

`CbrPathAdvisorWorker.create()` gains a `PreferenceProvider` parameter.
The threshold is resolved inside `doAdvise()`:

```java
int activationThreshold = resolveActivationThreshold(preferenceProvider);
boolean active = count >= activationThreshold;
result.put("active", active);
```

The `resolveActivationThreshold()` method follows the same
`PreferenceProvider.resolve(SettingsScope)` pattern as
`InvestigationTriageWorker.buildEvaluator()`.

### Wiring

`AmlInvestigationCaseHub` already injects `PreferenceProvider` (for triage).
Thread it to `CbrPathAdvisorWorker.create()` via `AmlInvestigationCaseDescriptor`:

1. `AmlInvestigationCaseHub.augment()` — passes `preferenceProvider` to descriptor (already available)
2. `AmlInvestigationCaseDescriptor` — passes to `CbrPathAdvisorWorker.create()`
3. `CbrPathAdvisorWorker.create()` — new parameter, captured in lambda closure

### `CbrAdjuster` integration

`CbrAdjuster.adjust()` gains the `active` check:

```java
if (cbr == null || cbr.caseCount() == 0 || cbr.confidence() < minConfidence
        || cbr.error() || !cbr.active() || cbr.predominantOutcome() == null) {
    return new AdjustedThresholds(sarThreshold, fpThreshold, null);
}
```

When `active=false`, the adjuster returns unadjusted thresholds — CBR has
no effect on triage.

### Binding change

`senior-analyst-required-resolution` gains `.cbrPathAdvice.active == true`:

```yaml
- name: senior-analyst-required-resolution
  on: { contextChange: {} }
  when: >-
    .entityResolution != null and
    .priorEntityContext.knownHighRisk != true and
    (.entityResolution.entityType == "PEP" or
     .entityResolution.riskScore > 0.8 or
     (.cbrPathAdvice != null and
      .cbrPathAdvice.active == true and
      .cbrPathAdvice.capabilities["senior-analyst-review"].frequency > 0.6)) and
    .seniorAnalystReview == null
  capability: senior-analyst-review
```

This prevents CBR from triggering senior analyst routing when below the
activation threshold.

### Advisory ledger entry

`AmlCbrAdvisoryLedgerEntry` gains an `active` column:

```java
@Column(name = "active", nullable = false)
public boolean active;
```

Flyway migration adds the column. `domainContentBytes()` updated to include
the field. Creates a queryable audit trail of learning-mode vs active-mode
advisories.

## §2 — Synthetic Seeder

### Design principle

The seeder constructs `PlanCbrCase` entries using the same domain types
(`CaseProfile.toFeatures()`, `PlanTrace`, `TriageDecision`) that
`AmlCaseProfileStoreObserver` uses. No new serialization format — identical
case shape to what the engine produces organically.

### `CbrSyntheticSeeder` — plain class in `app/.../cbr/`

Not a CDI bean — constructed directly in the simulation service (same
pattern as `SarNarrativeSeeder`). Takes `CbrCaseMemoryStore` as a
constructor parameter.

Core method:

```java
public SeedResult seed(int targetCount, String tenantId)
```

### Coverage strategy

The similarity matrix has `FlagReason`(8) x `EntityType`(4) x
`JurisdictionRisk`(3) = 96 cells. For `targetCount < 96`, sample weighted
by realistic frequency (STRUCTURING and LAYERING more common than
ROUND_TRIP). For `targetCount >= 96`, fill every cell then add duplicates
with varied amounts and outcomes for density.

Per-case generation:

- `CaseProfile.complete(flagReason, amount, priorIncidentCount, entityType,
  jurisdiction, network)` -> `toFeatures()`
- `transaction_amount`: flag-reason-appropriate ranges (STRUCTURING: $5k-15k,
  LARGE_VOLUME: $100k-1M, etc.)
- `prior_incident_count`: 0-5
- `network_complexity`: weighted by entity type (SHELL_COMPANY ->
  LARGE_NETWORK more often)
- Outcome: weighted distribution — ~55% SAR_WARRANTED, ~30% FALSE_POSITIVE,
  ~15% INCONCLUSIVE

Amount ranges per flag reason:

| Flag reason | Min | Max |
|-------------|-----|-----|
| STRUCTURING | 5,000 | 15,000 |
| LAYERING | 20,000 | 200,000 |
| SMURFING | 3,000 | 12,000 |
| ROUND_TRIP | 50,000 | 500,000 |
| PEP_MATCH | 10,000 | 500,000 |
| HIGH_RISK_JURISDICTION | 25,000 | 1,000,000 |
| VELOCITY_ANOMALY | 10,000 | 100,000 |
| LARGE_VOLUME | 100,000 | 5,000,000 |

### Plan trace patterns

Three patterns from the YAML bindings:

| Outcome | Trace sequence |
|---------|---------------|
| SAR_WARRANTED | entity-resolution -> pattern-analysis -> osint-screening -> investigation-triage -> sar-drafting -> compliance-review-opening |
| SAR_WARRANTED (PEP or high-risk) | entity-resolution -> senior-analyst-review -> pattern-analysis -> osint-screening -> investigation-triage -> sar-drafting -> compliance-review-opening |
| FALSE_POSITIVE / INCONCLUSIVE | entity-resolution -> pattern-analysis -> osint-screening -> investigation-triage |

Each `PlanTrace` entry uses realistic worker names from
`AmlInvestigationCaseDescriptor` (e.g., `"entity-resolution-agent"`,
`"pattern-analysis-agent"`), outcome `"SUCCESS"`, and sequential index.

PEP/high-risk trace is used when `entityType == PEP` or
`flagReason == HIGH_RISK_JURISDICTION`.

### Deterministic randomness

The seeder uses a `Random` instance seeded with a fixed seed (e.g.,
`new Random(99)`) for reproducible output. Same `targetCount` produces
the same cases every time. This makes tests deterministic and makes
repeated `DELETE` + `POST` cycles produce identical case bases.

### Store call

Uses `CbrCaseMemoryStore.store()` with correct parameter ordering
(GE-20260718-95e11e):

```java
cbrStore.store(cbrCase, AmlCbrSchema.CASE_TYPE, entityId,
               AmlMemoryDomains.CBR, tenantId, caseId, Path.root());
```

Where `entityId = UUID.nameUUIDFromBytes(("synthetic-cbr:" + i).getBytes()).toString()`
and `caseId = new UUID(random.nextLong(), random.nextLong()).toString()`.

### `SeedResult` record

```java
public record SeedResult(int seeded, Map<String, Integer> flagReasonCoverage,
                          Map<String, Integer> entityTypeCoverage,
                          Map<String, Integer> outcomeCoverage) {}
```

### API integration

Extends `AmlSimulationResource` (same `@IfBuildProperty` gate):

- `POST /api/simulation/seed/cbr` — seeds the case base. Optional request
  body `{ "count": 50 }`. Returns `SeedResult` as 202 Accepted.
- `DELETE /api/simulation/seed/cbr` — clears the CBR store via
  `cbrStore.eraseByScope(Path.root(), tenantId)`. Returns 204 No Content.

`AmlSimulationService` gains the `CbrCaseMemoryStore` injection and
delegates to `CbrSyntheticSeeder`.

### Idempotency

No built-in idempotency — the seeder always writes new cases.
`DELETE` then `POST` for a clean reset. Acceptable for dev/demo — this
API doesn't exist in production builds.

## §3 — Bootstrap Report

### `AmlCbrResource` — new REST resource

Not simulation-gated — this is an operational diagnostic endpoint.

```java
@Path("/api/cbr")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AmlCbrResource {

    @GET
    @Path("/bootstrap-report")
    public BootstrapReport getBootstrapReport() { ... }
}
```

### Data sources

The report queries two ledger entry tables via `EntityManager`:

1. **Case base coverage** — `AmlCaseProfileLedgerEntry` (one per retained
   case). Aggregates: total count, count by `flag_reason`, `entity_type`,
   `jurisdiction_risk`, `outcome`.

2. **Advisory metrics** — `AmlCbrAdvisoryLedgerEntry` (one per advisor
   execution). Aggregates: total advisories, count where `active=true` vs
   `active=false`, average confidence, average case count.

### Report shape

```java
public record BootstrapReport(
    CaseBaseSummary caseBase,
    AdvisoryMetrics advisoryMetrics) {}

public record CaseBaseSummary(
    long totalCases,
    int activationThreshold,
    Map<String, Long> byFlagReason,
    Map<String, Long> byEntityType,
    Map<String, Long> byJurisdictionRisk,
    Map<String, Long> byOutcome) {}

public record AdvisoryMetrics(
    long totalAdvisories,
    long activeAdvisories,
    long learningAdvisories,
    double avgConfidence,
    double avgCaseCount) {}
```

`activationThreshold` is included in the report (resolved from
`PreferenceProvider`) so the consumer can see the relationship between
case count and threshold.

### Why ledger entries, not the CBR store

`CbrCaseMemoryStore` has no `count()` or `listAll()` API — only
`retrieveSimilar()`. Profile ledger entries are a 1:1 proxy for retained
cases (one written per case by `AmlCaseProfileStoreObserver`) and are
queryable via standard JPA. Advisory entries similarly track every advisor
execution.

### Note on synthetic cases

Synthetic cases written by the seeder go into `CbrCaseMemoryStore` only —
they do NOT produce ledger entries. The bootstrap report's case base
section reflects retained (real) cases only. This is correct: the report
answers "how much real experience does CBR have?" not "how many entries
are in the store?"

To see synthetic case count, use the CBR store directly or the seed
endpoint response.

## §4 — Testing

### Unit tests (api module)

**`CbrPathAdviceTest`** — `active` field serialization/deserialization.

**`CbrAdjusterTest`** — existing tests plus:
- `active=false` -> no adjustment regardless of confidence/outcome
- `active=true` with sufficient confidence -> adjustment applied

### Unit tests (app module)

**`CbrSyntheticSeederTest`**:
- Default count (50) produces 50 cases
- All 8 `FlagReason` values represented
- All 4 `EntityType` values represented
- All 3 `JurisdictionRisk` values represented
- All 3 outcomes present (SAR_WARRANTED, FALSE_POSITIVE, INCONCLUSIVE)
- Plan traces match known binding patterns (SAR path: 6 steps, cleared: 4)
- Features built via `CaseProfile.toFeatures()` — all keys present
- Deterministic: same seed -> same output
- Store called with correct parameter ordering

### @QuarkusTest integration (app module)

**Learning mode — below threshold:** Seed 5 CBR cases -> start
investigation with `HIGH_RISK_JURISDICTION` -> drain to completion ->
assert `cbrPathAdvice.active == false` -> assert
`AmlCbrAdvisoryLedgerEntry.active == false` -> assert triage decision
based on risk score alone (no CBR adjustment).

**Active mode — above threshold:** Seed 35 CBR cases (all FALSE_POSITIVE
for similar flag reasons) -> start investigation -> drain -> assert
`cbrPathAdvice.active == true` -> assert triage thresholds were
CBR-adjusted.

**CBR binding gated by active:** Seed 35 cases where 80% used
senior-analyst-review -> start investigation that wouldn't trigger
senior-analyst by risk score -> assert senior-analyst IS dispatched
(active=true, frequency > 0.6). Repeat with only 3 cases -> assert
senior-analyst NOT dispatched (active=false).

**Seed endpoint:** `POST /api/simulation/seed/cbr` with
`{"count": 20}` -> assert 202 with coverage map. Then
`DELETE /api/simulation/seed/cbr` -> 204.

**Bootstrap report — empty state:** No cases, no advisories -> report
returns zeros across all fields.

**Bootstrap report — after investigation:** Seed cases + run
investigation -> report reflects case base coverage and advisory metrics.

### Test conventions

- `cbrStore.eraseByScope(Path.root(), TENANT)` at test start
  (GE-20260716-986cd1)
- Drain to `status=completed` before assertions
- `casehub.ledger.hash-chain.enabled=false`
- Ledger subject isolation:
  `UUID.nameUUIDFromBytes("aml-<concern>:" + caseId)`
- Gate approval ordering for PlannedAction workers
  (GE-20260628-dbc656)
- `HIGH_RISK_JURISDICTION` flag reason for tests requiring the SAR path
  (GE-20260726-00e4df)

## §5 — Scope Boundaries

### Not in scope

| Item | Why |
|------|-----|
| Operator override (manual ACTIVE/LEARNING toggle) | Per-retrieval activation is sufficient for pre-release |
| Workbench UI for bootstrap report | Deferred to #110. JSON endpoint is the data contract |
| Per-cluster activation reporting | Inherent in the advisor — no separate mechanism needed |
| SAR narrative content in synthetic cases | Synthetic cases don't carry `sar_narrative`. Narrative seeding (#98) works from real retained cases |
| Cross-encoder reranking | In-memory store uses feature-only scoring |

### Follow-up issues

1. **Workbench CBR panel** (#110) — bootstrap report visualization, CBR
   override toggle
2. **Production CBR store adapter** — synthetic seeding works unchanged
   via `CbrCaseMemoryStore.store()` API

### Cross-repo impact

None. All types used (`CbrCaseMemoryStore`, `PlanCbrCase`, `PlanTrace`,
`PreferenceProvider`, `SettingsScope`) are existing API surface. No engine
changes.

### Files touched

| File | Change |
|------|--------|
| `api/.../domain/CbrPathAdvice.java` | Add `active` field |
| `api/.../triage/CbrAdjuster.java` | Add `!cbr.active()` guard |
| `app/.../cbr/CbrPathAdvisorWorker.java` | Compute `active`, resolve threshold |
| `app/.../cbr/CbrSyntheticSeeder.java` | **New** — synthetic case generation |
| `app/.../cbr/AmlCbrPolicyKeys.java` | **New** — preference key |
| `app/.../engine/AmlInvestigationCaseHub.java` | Pass preferenceProvider to advisor |
| `app/.../engine/AmlInvestigationCaseDescriptor.java` | Thread preferenceProvider |
| `app/.../simulation/AmlSimulationResource.java` | CBR seed/clear endpoints |
| `app/.../simulation/AmlSimulationService.java` | CBR seed/clear methods |
| `app/.../rest/AmlCbrResource.java` | **New** — bootstrap report |
| `app/.../rest/BootstrapReport.java` | **New** — report records |
| `app/.../ledger/AmlCbrAdvisoryLedgerEntry.java` | Add `active` column |
| `aml-investigation.yaml` | `.cbrPathAdvice.active == true` condition |
| Flyway migration | `active` column on advisory ledger entry |
| Tests (new + modified) | Per §4 |

## Garden Entries Referenced

- GE-20260716-986cd1 — InMemoryCbrCaseMemoryStore test isolation
- GE-20260718-95e11e — CbrCaseMemoryStore.store() parameter ordering
- GE-20260720-6ea915 — CbrCaseRetainObserver CDI exclusion
- GE-20260628-dbc656 — PlannedAction gate approval ordering in tests
- GE-20260726-00e4df — Investigation test flag reasons after triage logic
