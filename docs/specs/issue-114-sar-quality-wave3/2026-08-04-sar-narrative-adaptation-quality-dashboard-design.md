# SAR Narrative Adaptation & Quality Dashboard — Design Spec

**Issues:** #114 (LLM-powered sar-drafting worker — seed narrative adaptation), #116 (quality dashboard — UPHELD-rate segmentation by narrative seeding)
**Epic:** #92 (Case-Based Reasoning)
**Date:** 2026-08-04
**Builds on:** #98 (SAR narrative seeding — plumbing)

## Context

Issue #98 delivered the plumbing: `SeedNarrative` records flow from CBR retrieval
through `CbrPathAdvisorWorker` into the sar-drafting worker's input projection.
Worker stubs acknowledge seeds (set `narrativeSeeded`/`seedCount` flags) but never
use them for actual narrative generation. `AmlCaseProfileLedgerEntry` has
`narrative_seeded` and `seed_count` columns (V3008) for quality segmentation.

Two gaps remain:
1. The sar-drafting worker produces hard-coded narratives — seeds are plumbed but ignored
2. No analytics surface exists to measure whether seeding improves SAR quality

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| SPI architecture | Replace `SarDraftingService` with `SarNarrativeService` | Old interface is dead code (workers never call it), lacks seed parameter and metadata return. Pre-release — breaking changes cost nothing. |
| Implementation strategy | `@ApplicationScoped` deterministic + eidos `@Alternative` with composition fallback | Deterministic service is `@ApplicationScoped` (not `@DefaultBean`) so it remains in CDI when the eidos `@Alternative` is active — eidos injects it by concrete type for fallback on LLM failure. `@DefaultBean` would remove the template service entirely when the alternative is active, breaking composition. `@Alternative` (not `@DefaultBean` displacement) because eidos requires external infrastructure (API keys, model endpoint) and must not auto-activate on classpath presence — displacement is for progressive layer evolution (L1 → L3 → L5), not optional runtime variants. |
| Adaptation level | Style + structure | LLM uses seeds for document structure and regulatory prose style, reasons independently about current case specifics. Deterministic adapter mirrors best seed's structure with fact substitution. |
| Context budget | Both layers | AML preferences for maxSeeds (domain: how many exemplars are useful). Eidos handles token-level truncation (model: does this fit the context window). |
| LLM backend | casehub-eidos | Eidos builds on the platform agent provider, which wraps Claude SDK / LangChain4j. Going directly to a provider would bypass the platform abstraction. |
| Dashboard placement | New tab in Operations view | Operations is the analytics hub. SAR quality is a cross-case aggregate metric alongside Throughput, Trust Scores, Gates, Intervention. |
| Dashboard form | KPI cards + tables | Matches existing Operations aesthetic. No charts/D3 — tables and KPI cards are sufficient for the data shape. |

## §1 — Domain Types

### Removals

- `api/.../investigation/SarDraftingService.java` — interface, dead code
- `app/.../DefaultSarDraftingService.java` — implementation, dead code
- `app/.../DefaultSarDraftingServiceTest.java` — tests for dead code

### New records in `api/src/main/java/io/casehub/aml/investigation/`

```java
public record NarrativeContext(
    SuspiciousTransaction transaction,
    EntityResolutionResult entity,
    PatternAnalysisResult pattern,
    OsintResult osint,
    List<SeedNarrative> seeds
) {
    public NarrativeContext {
        Objects.requireNonNull(transaction, "transaction is required for SAR narrative drafting");
        if (seeds == null) seeds = List.of();
    }
}

public enum AdaptationMethod {
    DETERMINISTIC,
    LLM,
    LLM_FALLBACK_DETERMINISTIC
}

public record NarrativeResult(
    String narrative,
    boolean seeded,
    int seedCount,
    AdaptationMethod adaptationMethod
) {}
```

### New SPI in `api/src/main/java/io/casehub/aml/investigation/`

```java
public interface SarNarrativeService {
    NarrativeResult draft(NarrativeContext context);
}
```

The contract: given investigation findings and available seeds, produce the best
narrative possible. When `seeds` is empty, generate from scratch. The
implementation decides how to use seeds — the caller does not know or care
whether it is template substitution or LLM adaptation.

`NarrativeContext` bundles typed domain objects. ObjectMapper conversion from
`Map<String, Object>` stays in the worker (framework concern). The SPI gets
clean typed input.

### Quality report records in `api/src/main/java/io/casehub/aml/api/model/`

```java
public record SarQualityReport(
    OutcomeSegment seeded,
    OutcomeSegment unseeded,
    List<SeedCountBucket> bySeedCount,
    int totalCases    // = seeded.total + unseeded.total (attested SAR_WARRANTED cases only)
) {}

public record OutcomeSegment(
    int total,
    int upheld,
    int notUpheld,      // WITHDRAWN + FLAGGED (indistinguishable in attestation — see §1 limitation note)
    double upheldRate
) {
    public static OutcomeSegment of(int upheld, int notUpheld) {
        int total = upheld + notUpheld;
        double rate = total > 0 ? (double) upheld / total : 0.0;
        return new OutcomeSegment(total, upheld, notUpheld, rate);
    }
}

public record SeedCountBucket(
    String range,    // "1", "2", "3+" — aligned with default maxSeeds=3
    int total,
    double upheldRate
) {}
```

`totalCases` is always `seeded.total + unseeded.total` — a convenience field
for the frontend KPI row. Cases without attestations are excluded from the
query and do not appear in any count.

`SeedCountBucket` ranges are aligned with the default `maxSeeds=3` preference.
With `maxSeeds=3`, the "3+" bucket is effectively "exactly 3". If `maxSeeds` is
changed, the bucket definitions should be updated to match.

### Data correlation

`AmlCaseProfileLedgerEntry.outcome` stores the **triage decision**
(`SAR_WARRANTED`, `FALSE_POSITIVE`, `INCONCLUSIVE`), not the post-submission
verdict. SAR verdicts (UPHELD/WITHDRAWN/FLAGGED) are stored as
`LedgerAttestation` records via `SarOutcomeFeedbackService`, where both
WITHDRAWN and FLAGGED map to `AttestationVerdict.FLAGGED`.

The quality query **joins two entities**:
- `AmlCaseProfileLedgerEntry` — has `narrativeSeeded`, `seedCount`, `adaptationMethod`
- `LedgerAttestation` — has `verdict` (SOUND = UPHELD, FLAGGED = not upheld)

### New ledger column

`adaptation_method VARCHAR(50)` on `AmlCaseProfileLedgerEntry` — stores the
`AdaptationMethod` enum name (`DETERMINISTIC`, `LLM`, `LLM_FALLBACK_DETERMINISTIC`).
Nullable for backward compatibility with pre-existing entries. Populated from the
case file snapshot at retain time (same pattern as `narrativeSeeded`/`seedCount`):

```java
if (snapshot.get("adaptationMethod") instanceof String s) {
    entry.adaptationMethod = s;
}
```

**Flyway migration:**
```sql
-- V3009__adaptation_method_column.sql
ALTER TABLE aml_case_profile_ledger_entry ADD COLUMN adaptation_method VARCHAR(50);
```

The quality dashboard's primary segmentation remains seeded/unseeded (the stated
goal of #116). The `adaptationMethod` column enables future drill-down to measure
the incremental value of LLM adaptation over deterministic adaptation — not
exposed in the dashboard UI in this iteration

Join on `subjectId` (= caseId), filtered to:
- `AmlCaseProfileLedgerEntry.outcome = 'SAR_WARRANTED'` (only SARs)
- `LedgerAttestation.capabilityTag = 'sar-drafting'`
- `LedgerAttestation.trustDimension = 'investigation-accuracy'`

Cases without an attestation haven't had their SAR reviewed yet — excluded from
quality metrics (too early to measure).

### WITHDRAWN / FLAGGED conflation — known limitation

`SarOutcomeFeedbackService.toVerdict()` maps both `SarVerdict.WITHDRAWN` and
`SarVerdict.FLAGGED` to `AttestationVerdict.FLAGGED`. The raw `SarVerdict` is
not preserved on `LedgerAttestation` — only the mapped `AttestationVerdict` is
stored. This means the quality dashboard cannot distinguish between:
- **WITHDRAWN** — SAR pulled back (wrong investigation path)
- **FLAGGED** — SAR flagged as deficient (poor narrative quality)

This is acceptable for the UPHELD-rate metric: both WITHDRAWN and FLAGGED
represent non-UPHELD outcomes, and the primary question is "does seeding
improve the proportion of SARs that survive regulatory review?" A seeded
narrative that led to a WITHDRAWN SAR was still part of a failed investigation
outcome regardless of narrative quality.

If future analysis requires distinguishing narrative-quality deficiency from
investigation-path error, the `SarVerdict` must be preserved as additional
metadata on `LedgerAttestation` — this is a cross-cutting ledger model change
tracked in a follow-up issue.

## §2 — `TemplateSarNarrativeService` (deterministic default)

`app/src/main/java/io/casehub/aml/investigation/TemplateSarNarrativeService.java`
— `@ApplicationScoped`.

### Unseeded path

Empty seeds list: generates a structured narrative from investigation findings
directly. Same logic currently inlined in `buildNarrative()` in the descriptor,
but producing a richer multi-section narrative rather than a single sentence.

### Seeded path

1. Apply `maxSeeds` limit from platform preferences (default 3) — truncate list
2. Apply `maxSeedLength` limit (default 2000 chars) — truncate individual seeds
3. Select top seed by `similarityScore` (already sorted by `SarNarrativeSeeder`)
4. Generate narrative that structurally follows the seed's pattern with current
   case facts (transaction ID, amount, entity type, flag reason, OSINT findings)
5. Append provenance note: "Adapted from N similar case(s), highest similarity: X.XX"

### Context budget preferences

New preference keys in `AmlNarrativePolicyKeys`:
- `cbr.narrative.maxSeeds` — max seed narratives to pass through (default: 3)
- `cbr.narrative.maxSeedLength` — max chars per seed narrative (default: 2000)

Applied inside the service implementation before any adaptation logic. Both
implementations (deterministic and eidos) honour the same preferences — the AML
domain decides "how many exemplars are useful", not the LLM.

### Why `@ApplicationScoped` (not `@DefaultBean`)

Works everywhere — tests, CI, no API keys. When the eidos `@Alternative` is not
selected, this is the sole `SarNarrativeService` bean. When the alternative IS
selected, CDI resolves `SarNarrativeService` to the eidos implementation, but
`TemplateSarNarrativeService` remains available by concrete type — the eidos
service injects it for fallback delegation (see §3). `@DefaultBean` would remove
this bean entirely when the alternative is active, breaking the composition chain.

## §3 — `EidosSarNarrativeService` (eidos-powered `@Alternative`)

`app/src/main/java/io/casehub/aml/investigation/EidosSarNarrativeService.java`
— `@Alternative @Priority(1) @ApplicationScoped`.

### Dependencies

- `casehub-eidos-api` added to `app/pom.xml` (interface types for prompt construction)
- `casehub-eidos` runtime added to `app/pom.xml`

The eidos-api dependency belongs in `app/` not `api/` — the SPI in `api/`
(`SarNarrativeService`, `NarrativeContext`, `NarrativeResult`) is deliberately
eidos-agnostic. Only `EidosSarNarrativeService` in `app/` references eidos types.
`api/` remains zero-framework, zero-eidos per ARC42STORIES §5 module purity.

### How it works

1. Applies the same `maxSeeds` / `maxSeedLength` preference limits
2. Builds an `AgentPromptContext` using eidos prompt infrastructure:
   - **System prompt:** regulatory context — what a SAR is, FinCEN requirements,
     what "style + structure adaptation" means
   - **Seed narratives:** injected as exemplar documents with metadata
     (similarity score, flag reason, entity type) — the LLM uses these for
     structure and regulatory prose style
   - **Current case facts:** investigation findings (entity resolution, pattern
     analysis, OSINT) as structured data
   - **Instruction:** "Produce a SAR narrative for the current case. Use the
     exemplar narratives for document structure and regulatory language. Reason
     independently about this case's specifics."
3. Calls eidos agent execution — eidos handles token-level context window
   management (truncation of seeds if they exceed the model's capacity)
4. Parses the LLM response into `NarrativeResult`

### Activation

Configured via `application.properties`:
```properties
quarkus.arc.selected-alternatives=io.casehub.aml.investigation.EidosSarNarrativeService
```

Not active by default — the deterministic service runs unless explicitly overridden.

### Failure handling and fault tolerance

`EidosSarNarrativeService` takes `TemplateSarNarrativeService` as a constructor
parameter (injected by concrete type). If the eidos call fails (timeout, model
error, rate limit), delegates to the injected template service and logs a warning.
The fallback sets `adaptationMethod = LLM_FALLBACK_DETERMINISTIC` to distinguish
from a pure deterministic draft — the quality dashboard can segment by adaptation
method if future analysis requires it.

This is explicit composition, not interface-level displacement: the eidos service
wraps the deterministic service and delegates to it on failure. The concrete-type
coupling is intentional — the `@Alternative` is architecturally above the default
implementation and needs it as a fallback target. The investigation never blocks
on an LLM failure.

**Timeout and circuit breaker:** The eidos call method carries MicroProfile Fault
Tolerance annotations (requires `quarkus-smallrye-fault-tolerance` dependency in
`app/pom.xml`):

- `@Timeout(value = 10, unit = ChronoUnit.SECONDS)` — bounds the eidos call to
  10 seconds. Configurable via `aml.eidos.timeout-seconds` preference
- `@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5, delay = 30,
  delayUnit = ChronoUnit.SECONDS, successThreshold = 3)` — after 3 failures in
  5 calls, the circuit opens for 30 seconds. During this window, calls go directly
  to the deterministic fallback without attempting the eidos call — no thread
  blocking, no timeout wait
- `@Fallback(fallbackMethod = "draftDeterministic")` — routes all failure modes
  (timeout, circuit open, exception) to the deterministic path

These annotations are on the internal `callEidos()` method, not on `draft()`
itself. `draft()` calls `callEidos()` and lets the fault tolerance interceptor
handle failures via the fallback method.

**PII exposure at startup:** `@PostConstruct` checks whether the injected
`ContentSanitiser` is `PassThroughContentSanitiser`. If so, logs a WARNING:
"EidosSarNarrativeService is active with pass-through ContentSanitiser — seed
narratives sent to eidos may contain PII from past cases. Gate production
activation on #115." See §9 for full PII risk documentation.

### Testing

Tested with a mock eidos agent (no real LLM calls in CI). The mock verifies
prompt structure — that seeds are present, that current case facts are injected,
that the system prompt includes regulatory context. Integration tests with a
real LLM are manual/optional.

## §4 — Worker Refactoring

### Current state

The sar-drafting workers in `AmlInvestigationCaseDescriptor` are inline lambdas
with 30+ lines of ObjectMapper conversion and narrative string building. Neither
calls `SarDraftingService`. Two worker methods exist (`sarDraftingWorkerJunior`,
`sarDraftingWorkerSenior`) but only the senior is in the `workers()` list.

### Changes

**Remove:** `sarDraftingWorkerJunior()` — dead code, already excluded from
`workers()` per engine#82 multi-worker PlanItem fix.

**Cleanup:** Remove `sar-drafting-agent-junior` seed from `AmlTrustScoreSeeder` —
dead configuration matching the removed junior worker. Currently seeds Beta(2,8)
for a worker that no longer exists.

**Refactor `sarDraftingWorkerSenior()`:** delegates to `SarNarrativeService`:

```java
private Worker sarDraftingWorkerSenior() {
    return Worker.builder()
                 .name("sar-drafting-agent-senior")
                 .capabilityName("sar-drafting")
                 .function((final Map<String, Object> input) -> {
                     var tx = objectMapper.convertValue(input.get("transaction"), SuspiciousTransaction.class);
                     var entity = objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
                     var pattern = objectMapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
                     var osint = objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
                     var seeds = deserializeSeeds(input.get("similarSarNarratives"));

                     var context = new NarrativeContext(tx, entity, pattern, osint, seeds);
                     var result = sarNarrativeService.draft(context);

                     var output = new LinkedHashMap<String, Object>();
                     output.put("sarNarrative", result.narrative());
                     output.put("narrativeSeeded", result.seeded());
                     output.put("seedCount", result.seedCount());
                     output.put("adaptationMethod", result.adaptationMethod().name());

                     String entityType = entity != null ? entity.entityType() : "UNKNOWN";
                     return WorkerResult.of(output,
                             PlannedAction.of(
                                     "SAR filing for transaction " + tx.id(),
                                     AmlActionType.SAR_FILING.actionType(),
                                     Map.of("transactionId", tx.id(),
                                            "amount", String.valueOf(tx.amount()),
                                            "currency", tx.currency(),
                                            "entityType", entityType)));
                 })
                 .build();
}
```

### `deserializeSeeds()` helper

Seeds arrive from the engine as `List<Map<String, Object>>` (JSON round-tripped
through `CaseContext`). The helper uses `ObjectMapper.convertValue()` — the same
pattern as all other field deserialisations in the worker:

```java
@SuppressWarnings("unchecked")
private List<SeedNarrative> deserializeSeeds(Object raw) {
    if (raw == null) return List.of();
    var list = (List<Map<String, Object>>) raw;
    return list.stream()
               .map(m -> {
                   try {
                       return objectMapper.convertValue(m, SeedNarrative.class);
                   } catch (IllegalArgumentException e) {
                       LOG.warnf("Skipping malformed seed narrative: %s", e.getMessage());
                       return null;
                   }
               })
               .filter(Objects::nonNull)
               .toList();
}
```

Defensive: individual malformed seeds (missing fields, wrong types after JSON
round-trip) are skipped with a warning — the remaining valid seeds are still
used. A single bad seed does not fail the entire SAR drafting worker.

Seeds are already sanitised and filtered by `SarNarrativeSeeder.extract()` in
the advisor worker — no re-processing needed.

### `seedCount` semantics

`NarrativeResult.seedCount()` reflects **seeds used** — after `maxSeeds`
truncation. This is intentional: the quality dashboard correlates outcome quality
with the number of seeds that actually influenced narrative generation, not the
number available before truncation. A case with 5 available seeds truncated to 3
by `maxSeeds` shows up in the "3+" bucket because 3 seeds influenced the output.

### Service injection

`SarNarrativeService` added as a constructor parameter to
`AmlInvestigationCaseDescriptor`, injected via `AmlInvestigationCaseHub.augment()`.

### PlannedAction unchanged

The `SAR_FILING` PlannedAction is constructed from the worker result after the
service call — the service does not know about PlannedAction (engine concern,
not domain logic).

## §5 — Quality Dashboard REST API

### Query service

`app/src/main/java/io/casehub/aml/metrics/SarQualityService.java` —
`@ApplicationScoped`. Lives in the `metrics/` package alongside
`AmlMetricsService`. Injects `EntityManager` (qhorus PU, where ledger entities
live).

Joins `AmlCaseProfileLedgerEntry` with `LedgerAttestation` via JPQL:
- `AmlCaseProfileLedgerEntry.subjectId = LedgerAttestation.subjectId`
- Filtered to `outcome = 'SAR_WARRANTED'`, `capabilityTag = 'sar-drafting'`,
  `trustDimension = 'investigation-accuracy'`
- **Latest attestation only:** a correlated subquery restricts to the attestation
  with `MAX(occurredAt)` per `subjectId` + `capabilityTag` + `trustDimension`.
  This handles SAR outcome revisions (e.g., UPHELD → FLAGGED on re-review) —
  only the final verdict counts. Without this, a revised case appears in both
  the upheld and not-upheld segments
- Groups by `narrativeSeeded` and attestation `verdict`
  (`SOUND` = upheld, `FLAGGED` = not upheld)
- Buckets `seedCount` into 1, 2, 3+ for correlation analysis
- Cases without an attestation are excluded (SAR not yet reviewed)

**Indexing:** The join uses `LedgerAttestation.subjectId`, which is a platform
table. The platform's `LedgerAttestation` entity already has a named query
`findBySubjectId` suggesting this is a common access pattern. If the platform
does not yet have an index on `ledger_attestation(subject_id)`, file a
platform issue — this query (and the existing named query) benefit from it.
AML does not own the `ledger_attestation` table schema and should not add
platform-level indexes in its own migrations

The JPQL query returns raw counts (upheld, not-upheld per segment). The service
builds `OutcomeSegment` records via the `OutcomeSegment.of(upheld, notUpheld)`
factory method, which computes `upheldRate` with division-by-zero protection.
`totalCases` is computed as the sum of both segments' totals.

Returns `SarQualityReport`.

### REST endpoint

New `@GET @Path("/sar-quality")` method on the existing `AmlMetricsResource`,
returning `SarQualityReport` as JSON. `SarQualityService` is injected alongside
`AmlMetricsService` and `TrustScoreSnapshotService` — same multi-injection
pattern as trust-score history.

No separate resource class. The `/api/metrics` namespace stays under one JAX-RS
resource, consistent with `/throughput`, `/trust-scores`, `/gates`.

## §6 — Quality Dashboard Frontend

### New component

`app/src/main/webui/src/views/aml-sar-quality-tab.ts` — Lit web component.

### Integration into Operations

Add `'sar-quality'` to the `TabId` union type in `operations.ts`. New tab button:
"SAR Quality". Fetches from `/api/metrics/sar-quality` on tab activation — same
pattern as the other four tabs.

### Layout

1. **KPI summary row** (existing `kpi-summary` grid):
   - Total cases (with terminal outcomes)
   - Seeded UPHELD rate (percentage)
   - Unseeded UPHELD rate (percentage)
   - Lift (seeded − unseeded, displayed as +/- percentage points, colour-coded)

2. **Segmentation table** (`pages-table`):

   | Segment | Total | Upheld | Not Upheld | UPHELD Rate |
   |---------|-------|--------|------------|-------------|
   | Seeded  | 42    | 38     | 4          | 90.5%       |
   | Unseeded| 15    | 10     | 5          | 66.7%       |

3. **Seed count correlation table** (`pages-table`):

   | Seeds | Total | UPHELD Rate |
   |-------|-------|-------------|
   | 1     | 12    | 83.3%       |
   | 2     | 18    | 88.9%       |
   | 3+    | 12    | 100%        |

### Minimum sample size

When any segment has `total < 5`, the UPHELD rate percentage is replaced with
"Insufficient data" in both the KPI row and tables. The raw counts (total,
upheld, not upheld) remain visible. The Lift KPI shows "—" when either
segment is below threshold. No statistical confidence intervals — this is an
operational monitoring dashboard, not a research tool. The `total` count
displayed alongside each rate lets the reader judge significance.

### TypeScript types

Added to `types.ts`:

```typescript
export interface SarQualityReport {
  seeded: OutcomeSegment;
  unseeded: OutcomeSegment;
  bySeedCount: SeedCountBucket[];
  totalCases: number;
}

export interface OutcomeSegment {
  total: number;
  upheld: number;
  notUpheld: number;
  upheldRate: number;
}

export interface SeedCountBucket {
  range: string;
  total: number;
  upheldRate: number;
}
```

## §7 — Testing

### Unit tests (api module)

- `NarrativeContextTest` — record construction, empty seeds list, null seeds normalised to empty list, null transaction throws `IllegalArgumentException`
- `NarrativeResultTest` — seeded/unseeded states, adaptation method carried through
- `AdaptationMethodTest` — enum values match expected set
- `OutcomeSegmentTest` — `OutcomeSegment.of()` factory: zero total → rate 0.0, normal computation, all upheld → rate 1.0
- `SarQualityReportTest` — totalCases equals seeded.total + unseeded.total

### Unit tests (app module)

**`TemplateSarNarrativeServiceTest`:**
- Unseeded: empty seeds → narrative from findings, `seeded=false`, `seedCount=0`
- Seeded: 1 seed → mirrors structure, `seeded=true`, `seedCount=1`
- maxSeeds: 5 available, maxSeeds=3 → top 3 used, `seedCount=3`
- maxSeedLength: long seed truncated to configured limit
- Null-safe: null entity/pattern/osint handled gracefully
- Provenance note present in seeded output

**`EidosSarNarrativeServiceTest` (Mockito):**
- Prompt structure: seeds present in agent context
- Prompt structure: current case facts injected
- Prompt structure: system prompt contains regulatory context
- maxSeeds/maxSeedLength applied before prompt construction
- Eidos failure → deterministic fallback, warning logged, `adaptationMethod = LLM_FALLBACK_DETERMINISTIC`
- Successful eidos call → `adaptationMethod = LLM`
- Circuit breaker: after 3 consecutive failures, subsequent calls skip eidos and go directly to fallback
- Timeout: eidos call exceeding 10s triggers fallback
- Empty seeds → generates without exemplars
- Startup with pass-through ContentSanitiser → WARNING logged

**`SarQualityServiceTest` (Mockito):**
- Mixed outcomes: correct segmentation by narrative_seeded, attestation verdict mapped to upheld/notUpheld
- All seeded: unseeded segment has zero total
- No attestations: empty report (no reviewed SARs yet)
- Cases without SAR_WARRANTED outcome excluded
- Seed count bucketing: 1, 2, 3+ correctly grouped
- Duplicate attestations: case with two attestations (UPHELD then FLAGGED) → only the latest (FLAGGED) counted
- Revised outcome: UPHELD → FLAGGED revision does not inflate totalCases

### @QuarkusTest integration (app module)

- **Seeded narrative adaptation:** Pre-populate CBR case base → start
  investigation → drain → assert `narrativeSeeded=true`, `seedCount>0`,
  narrative content differs from unseeded baseline

- **Unseeded baseline:** Empty case base → drain → assert `narrativeSeeded=false`

- **Service displacement:** `TemplateSarNarrativeService` active by default.
  When `EidosSarNarrativeService` in selected-alternatives, verify displacement
  (test with mock eidos agent)

- **Quality endpoint:** Pre-populate `AmlCaseProfileLedgerEntry` rows →
  `GET /api/metrics/sar-quality` → assert segmentation matches

- **Old interface removed:** CDI boot succeeds without `SarDraftingService`

### Test conventions

- Drain to `status=completed` before assertions
- `casehub.ledger.hash-chain.enabled=false`
- Ledger subject isolation: `UUID.nameUUIDFromBytes("aml-<concern>:" + caseId)`
- Gate approval ordering for PlannedAction workers
- CBR store isolation: `cbrStore.eraseByScope()` at test start
- `quarkus.quartz.thread-count=25`

## §8 — Files Touched

| File | Change |
|------|--------|
| `api/.../investigation/SarDraftingService.java` | **Remove** |
| `api/.../investigation/NarrativeContext.java` | **New** — record |
| `api/.../investigation/NarrativeResult.java` | **New** — record |
| `api/.../investigation/AdaptationMethod.java` | **New** — enum |
| `api/.../investigation/SarNarrativeService.java` | **New** — SPI interface |
| `api/.../api/model/SarQualityReport.java` | **New** — record |
| `api/.../api/model/OutcomeSegment.java` | **New** — record |
| `api/.../api/model/SeedCountBucket.java` | **New** — record |
| `app/.../DefaultSarDraftingService.java` | **Remove** |
| `app/.../DefaultSarDraftingServiceTest.java` | **Remove** |
| `app/.../investigation/TemplateSarNarrativeService.java` | **New** — `@ApplicationScoped` deterministic adapter |
| `app/.../investigation/EidosSarNarrativeService.java` | **New** — eidos `@Alternative` |
| `app/.../investigation/AmlNarrativePolicyKeys.java` | **New** — preference keys |
| `app/.../engine/AmlInvestigationCaseDescriptor.java` | **Modify** — new constructor param, refactor sar-drafting worker, remove junior |
| `app/.../engine/AmlInvestigationCaseHub.java` | **Modify** — inject and pass SarNarrativeService |
| `app/.../metrics/SarQualityService.java` | **New** — query service |
| `app/.../metrics/AmlMetricsResource.java` | **Modify** — add SAR quality endpoint, inject SarQualityService |
| `app/.../trust/AmlTrustScoreSeeder.java` | **Modify** — remove junior worker seed |
| `app/.../ledger/AmlCaseProfileLedgerEntry.java` | **Modify** — add `adaptationMethod` column + `domainContentBytes()` |
| `app/.../cbr/AmlCaseProfileStoreObserver.java` | **Modify** — read `adaptationMethod` from snapshot |
| `V3009__adaptation_method_column.sql` | **New** — migration |
| `app/pom.xml` | **Modify** — add casehub-eidos-api + casehub-eidos + quarkus-smallrye-fault-tolerance dependencies |
| `app/.../webui/src/views/aml-sar-quality-tab.ts` | **New** — Lit component |
| `app/.../webui/src/views/operations.ts` | **Modify** — add SAR Quality tab |
| `app/.../webui/src/types.ts` | **Modify** — add quality report types |
| `ARC42STORIES.MD` | **Modify** — update §5 interfaces list, §9.4 key files, §13 glossary: replace `SarDraftingService` references with `SarNarrativeService` |
| `docs/guides/consumer-guide.md` | **Modify** — update `SarDraftingService` reference |
| `docs/guides/contributor-guide.md` | **Modify** — update `SarDraftingService` reference in specialist service list |
| `LAYER-LOG.md` | **Modify** — update `SarDraftingService` references |
| Unit + integration tests | **New/Modify** — per §7 |

## §9 — Scope Boundaries

### Not in scope

- **Real PII sanitisation** — `ContentSanitiser` remains pass-through. Real
  NER-based redaction is a separate concern (#115).
  **PII risk with eidos path:** Until #115 ships real redaction, the eidos
  adapter sends unsanitised seed narratives (from past cases) to the LLM.
  Current-case data going to the LLM is inherent to LLM-powered drafting, but
  past-case PII leaking via seeds is an unintended cross-case exposure.
  Mitigations: (1) eidos activation requires explicit `selected-alternatives`
  configuration — it does not activate by default; (2) `EidosSarNarrativeService`
  logs a startup WARNING when `ContentSanitiser` is pass-through, alerting
  operators to the risk; (3) test and staging environments use synthetic data
  where this risk is moot. Production activation of the eidos path should be
  gated on #115 completion
- **A/B testing** — no experimental flag. Unseeded cases occur naturally
- **Charts / D3 visualisation** — tables and KPI cards match existing Operations
  aesthetic. Charts are a follow-up if needed
- **Prompt tuning** — initial prompt template ships; iteration is operational
- **Changes to CBR retrieval or storage** — narratives already stored and
  retrieved by #98

### Cross-repo impact

- **casehub-eidos** — consumed as dependency (api + runtime). No changes to eidos
- **casehub-parent** — update `docs/repos/casehub-aml.md` if capability list changes
