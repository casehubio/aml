# casehub-aml -- Contributor Guide

> Internal architecture, module structure, and development context for contributors modifying casehub-aml's internals or extension points.

**GitHub:** [casehubio/aml](https://github.com/casehubio/aml)

---

## Internal Architecture

### Hexagonal Module Structure

Two-tier module structure per platform protocol:

- **`api/`** -- domain layer: pure Java records, service interfaces, triage logic, zero framework dependencies. No JPA, no Quarkus, no CDI annotations. Compiles with no `io.quarkus.*` or `jakarta.persistence.*` imports.
- **`app/`** -- application + infrastructure layer: CDI beans, REST resources, JPA entities, Flyway migrations, Quarkus integration, web UI.

### Package Layout

```
aml-api/
  domain/        SuspiciousTransaction, InvestigationSummary, AmlInvestigationResult,
                 EntityResolutionResult, PatternAnalysisResult, OsintResult,
                 SpecialistOutcome<T> sealed interface, SarOutcome, SarVerdict,
                 InvestigationStatus, InvestigationOutcome, InvestigationResolution,
                 FailureContext, FailureEvent, FlagReason, EntityType, HardGate,
                 AmlActionType, AmlGroups, CaseProfile, CbrPathAdvice, SeedNarrative,
                 TriageDecision, TriageInput, TriageResult, JurisdictionRisk,
                 NetworkComplexity, RiskFactor, PagedResponse, InvestigationSummaryResponse
  api/model/     AgentTrustScore, FlowEdge, FlowNode, GateDecisionResponse,
                 GateMetrics, InvestigationFindingsResponse, InvestigationFlowResponse,
                 InvestigationGatesResponse, SpecialistFindingResponse,
                 ThroughputMetrics, TrustScoreMetrics, TrustScoreSnapshotResponse
  investigation/ EntityResolutionService, PatternAnalysisService,
                 OsintScreeningService, SarNarrativeService
  compliance/    ComplianceEvidence, RequirementStatus, AuditChainRequirement,
                 SlaRequirement, TrustRoutingRequirement, GdprErasureRequirement,
                 ActorErasureResult, LedgerEventRecord, AmlInclusionProof, AmlProofStep
  triage/        InvestigationTriageEvaluator, RiskScorer, HardGateEvaluator, CbrAdjuster

aml-app/
  (root)         AmlInvestigationApplicationService, AmlInvestigator (inner interface),
                 AmlInvestigationCoordinator, AmlInvestigationResource,
                 ComplianceReviewLifecycle, AmlJacksonConfig,
                 Default*Service stubs (5), DefaultAmlInvestigationService @DefaultBean,
                 QhorusAmlInvestigator
  agents/        AgentBehaviour, AgentDispatchMechanism, AgentChannelRegistry,
                 PushAgentDispatch, NoOpAgentDispatch,
                 EntityResolutionBehaviour, PatternAnalysisBehaviour,
                 OsintScreeningBehaviour
  cbr/           AmlCaseProfileStoreObserver, CaseProfileExtractor, AmlCbrSchema,
                 AmlCbrSchemaRegistrar, AmlTriagePolicyKeys,
                 CbrPathAdvisorWorker, InvestigationTriageWorker, SarNarrativeSeeder
  compliance/    AmlComplianceEvidenceService, AmlErasureService,
                 AmlWorkItemLifecycleObserver, AmlLayer7Resource, RetentionExpiryJob,
                 EntityErasureResult
  engine/        AmlInvestigationCaseHub, AmlInvestigationCaseDescriptor,
                 AmlEngineCoordinator, AmlOversightCaseHub, AmlOversightCoordinator,
                 AmlInvestigationOutcomeService, AmlInvestigationFindingsService,
                 AmlInvestigationFlowService, AmlInvestigationGatesService,
                 AmlInvestigationPriorContextService, AmlInvestigationQueryResource,
                 AmlAuditTrailResource, AmlLayer5Resource, AmlLayer6Resource,
                 AmlLayer9Resource, SarOutcomeRecordedEvent, SarOutcomeRequest,
                 Layer5InvestigationResponse, Layer6InvestigationResponse,
                 Layer9InvestigationResponse, WorkerRoutingDecision
  ledger/        AmlCaseOpenedLedgerEntry, AmlComplianceReviewLedgerEntry,
                 AmlSarOfficerReviewedLedgerEntry, AmlCaseProfileLedgerEntry,
                 AmlCbrAdvisoryLedgerEntry, AmlEntityErasureLedgerEntry,
                 AmlLedgerService
  memory/        AmlMemoryDomains, AmlMemoryPolicyKeys, AmlMemoryService,
                 AmlPriorContext, AmlSarOutcomeMemoryObserver
  metrics/       AmlMetricsResource, AmlMetricsService
  query/         InvestigationSummaryService, InvestigationSummaryObserver,
                 InvestigationSummaryRepository, InvestigationSummaryView
  rest/          IllegalArgumentExceptionMapper, JsonMappingExceptionMapper, ErrorResponse
  routing/       AmlActionRiskClassifier, AmlTrustRoutingPolicyProvider
  simulation/    AmlSimulationResource, AmlSimulationService, AmlScenarioTemplate
  trust/         AmlTrustScoreSeeder, AmlTrustRoutingObserver,
                 AmlTrustRoutingAttestation, AmlTrustAttestationRepository,
                 AmlAttestationReconciler, AmlWorkerDecisionRepository,
                 SarOutcomeFeedbackService, TrustScoreSnapshot,
                 TrustScoreSnapshotRepository, TrustScoreSnapshotService
  api/model/     AuditTrailEntryResponse, InclusionProofResponse
  domain/        TrustScoreSnapshot (JPA entity)
  webui/         Lit-based frontend (TypeScript, esbuild via Quinoa)
```

### Layering Rule

This is an application, not a framework. If the capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

---

## Investigation CasePlanModel

### Main Investigation (`aml-investigation.yaml`)

8 capabilities, 10 bindings, 2 goals:

**Capabilities:**
- `entity-resolution` -- fires first on any new transaction (no prior analysis required)
- `pattern-analysis` -- fires when entity graph complete (parallel with OSINT)
- `osint-screening` -- fires when entity graph complete (parallel with pattern)
- `senior-analyst-review` -- fires via two complementary bindings (see below)
- `sar-drafting` -- fires when triage decides `SAR_WARRANTED`; declares `PlannedAction(SAR_FILING)` for MLRO oversight gate
- `cbr-path-advisor` -- fires at case start when retrieved CBR experiences exist
- `investigation-triage` -- fires once all specialist findings present; gates on CBR path advice when experiences exist
- `compliance-review-opening` -- fires after SAR narrative is written; opens compliance officer WorkItem (30-day FinCEN SLA)

**Key bindings:**
- `senior-analyst-required-prior-context` -- fires when `priorEntityContext.knownHighRisk == true` AND `entityResolution == null` (routes known high-risk entities immediately, before entity resolution confirms)
- `senior-analyst-required-resolution` -- fires when entity resolution outputs PEP or riskScore > 0.8, OR CBR path advice recommends senior analyst (frequency > 0.6). Suppressed when prior-context routing already dispatched.
- `investigation-triage` -- gates on CBR path advice when experiences exist (waits for advisor to complete)
- `sar-drafting` -- gates on `investigationTriage.decision == "SAR_WARRANTED"`
- `compliance-review-opening` -- fires when `sarNarrative != null and complianceTaskId == null`

**Goals:**
- `investigation-complete` -- `.complianceTaskId != null`
- `investigation-cleared` -- triage decided `FALSE_POSITIVE` or `INCONCLUSIVE`

**Completion:** anyOf `investigation-complete` or `investigation-cleared`.

### Oversight Investigation (`aml-oversight-investigation.yaml`)

3 capabilities, 3 bindings, 1 goal. Self-contained case: `entity-resolution` -> `entity-link-proposal` (declares `PlannedAction(ENTITY_LINK_CREATION)`, gated for PEP/high-risk) -> `investigation-summary`. Goal: `investigationSummary != null`.

---

## CBR Integration

AML uses Case-Based Reasoning for investigation triage and path advice.

### Components

- `AmlCaseProfileStoreObserver implements CaseOutcomeObserver` -- domain-specific retain with `CaseProfile` feature extraction (flag reason, amount, entity type, jurisdiction risk, network complexity) and compliance ledger entries. Replaces generic `CbrCaseRetainObserver`.
- `CaseProfileExtractor` -- extracts initial (at case open) and complete (post-entity-resolution) profiles
- `CbrPathAdvisorWorker` -- analyses CBR experiences, produces `CbrPathAdvice` with capability frequencies, similar SAR narratives, and `active` field (activation threshold gating via `AmlCbrPolicyKeys.ACTIVATION_THRESHOLD`, default 30)
- `InvestigationTriageWorker` -- rule-based triage evaluation: `RiskScorer` (weighted score from findings) -> `HardGateEvaluator` (sanctions, law enforcement) -> `CbrAdjuster` (adjusts based on similar case outcomes, only when `CbrPathAdvice.active == true`). Pure function; no CDI. Uses `PreferenceProvider` for tunable thresholds.
- `SarNarrativeSeeder` -- extracts sanitised narratives from similar past cases (SAR_WARRANTED outcomes with similarity score > 0) for seeding the SAR drafting worker. Uses `ContentSanitiser` for PII protection.
- `CbrSyntheticSeeder` -- generates `PlanCbrCase` entries directly to `CbrCaseMemoryStore` for dev/demo cold-start bootstrapping. Deterministic (seeded `Random`). Not a CDI bean -- constructed in `AmlSimulationService`.
- `AmlCbrPolicyKeys` -- `PreferenceProvider` keys for CBR activation threshold (default: 30 retrieved cases required before CBR influences routing)
- `AmlCbrSchema` + `AmlCbrSchemaRegistrar` -- registers AML-specific CBR feature schema

### Configuration

- `CbrCaseRetainObserver` excluded from both main and test `application.properties` -- AML uses its own domain-aware retain logic via `AmlCaseProfileStoreObserver`
- CBR store isolation in tests: call `cbrStore.eraseByScope(Path.root(), TENANT)` at test start since `InMemoryCbrCaseMemoryStore` retains cases across test classes

### Triage Logic

The triage pipeline is pure Java (no CDI) in `api/triage/`:

1. `RiskScorer` computes a weighted risk score from entity resolution, pattern analysis, OSINT findings
2. `HardGateEvaluator` checks for mandatory-SAR conditions (sanctions match, law enforcement referral) -- overrides scoring
3. `InvestigationTriageEvaluator` combines scoring + hard gates into a `TriageResult` with decision `SAR_WARRANTED`, `FALSE_POSITIVE`, or `INCONCLUSIVE`
4. `CbrAdjuster` adjusts the initial decision using CBR path advice from similar past cases (outcome frequency, confidence)

All thresholds are configurable via `PreferenceProvider` with `AmlTriagePolicyKeys`.

---

## Foundation Layers

Each layer corresponds to a foundation module integration step:

```
Layer 1: Domain baseline -- hexagonal architecture, @DefaultBean displacement pattern,
         REST API for AML investigations.

Layer 2: + casehub-work -- compliance officer WorkItem with 30-day FinCEN claimDeadline;
         CDI displacement pattern.

Layer 3: + casehub-qhorus -- typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent;
         composer pattern, SpecialistOutcome sealed interface.

Layer 4: + casehub-ledger -- FinCEN audit trail, Merkle chain, GDPR Art.17 erasure;
         AmlCaseOpenedLedgerEntry + AmlComplianceReviewLedgerEntry, causedByEntryId chain.

Layer 5: + casehub-engine -- adaptive investigation paths (PEP routing, parallel checks);
         YAML bindings, AmlInvestigationCaseHub, AmlInvestigationCaseDescriptor.

Layer 6: Trust routing -- trust-weighted agent selection from SAR outcome attestations;
         AmlTrustRoutingPolicyProvider, SarOutcomeFeedbackService.
         YAML-backed trust routing config at casehub/aml/trust-routing.yaml.

Layer 7: Compliance evidence -- accountability properties mapped against FinCEN/FATF
         requirements; GDPR Art.17 erasure (actor-level and entity-level);
         retention expiry job; tamper-evident erasure receipts.

Layer 8: + casehub-platform CaseMemoryStore -- prior entity context (AmlMemoryService,
         AmlPriorContext); SAR outcome memories; YAML binding split for prior-context
         routing; ledger subclass redesign (AmlCaseOpenedLedgerEntry,
         AmlComplianceReviewLedgerEntry replace AmlInvestigationLedgerEntry).

Layer 9: + casehub-engine-work-adapter (ActionRiskClassifier oversight gate) --
         AmlActionType + AmlActionRiskClassifier + Layer 9 oversight harness
         (AmlOversightCaseHub, AmlOversightCoordinator, AmlLayer9Resource).
```

**Status: All layers complete (1-9).**

Additional features built on top of the layer stack:

- **CBR integration** (aml#92) -- case-based reasoning for investigation triage and path advice
- **Investigation triage** (aml#112) -- rule-based SAR/FP/INCONCLUSIVE classification with CBR adjustment
- **SAR narrative seeding** (aml#98) -- narrative seeding from similar past cases
- **Web UI** (aml#91) -- production investigation workbench
- **Trust score snapshots** (aml#87) -- historical trend persistence
- **Retention expiry** (aml#81) -- automated GDPR retention expiry job
- **Investigation query model** -- read-side projections for UI (InvestigationSummaryView)

---

## Key Epics

1. Project scaffold
2. Domain model -- AML entities and capability tags
3. Investigation CasePlanModel -- adaptive paths
4. Compliance officer WorkItem -- 30-day FinCEN SLA
5. Failure handling -- DECLINED vs FAILED routing
6. Trust-weighted routing and post-investigation feedback
7. GDPR and regulatory audit
8. LLM supervisor mode -- investigation triage
9. Tutorial layers 1-7 (comparison showcase)
10. Operational tooling -- MCP tools and observability
11. AML workbench UI -- production investigation workbench (aml#91)
12. Case-Based Reasoning for AML investigation (aml#92)

Issues: https://github.com/casehubio/aml/issues

---

## Worker Architecture

### Worker Types

8 workers in `AmlInvestigationCaseDescriptor` (main investigation):
- 6 use `FlowWorkerFunction` (FuncWorkflowBuilder per PP-20260531)
- 2 SAR-drafting workers (`sar-drafting-agent-junior`, `sar-drafting-agent-senior`) use `WorkerFunction.Sync` for `PlannedAction(SAR_FILING)` support (engine#564 blocks Flow support for PlannedAction)

3 workers in `AmlOversightCaseHub` (oversight investigation):
- 2 use `FlowWorkerFunction` (`entityResolutionWorker`, `investigationSummaryWorker`)
- 1 uses `WorkerFunction.Sync` (`entityLinkProposalWorker` -- PlannedAction support pending)

Worker primitives (`Worker`, `WorkerResult`, `PlannedAction`) are in `io.casehub.worker.api`.

### CBR Workers

- `InvestigationTriageWorker` -- static factory method `create(ObjectMapper, PreferenceProvider)`; deserialises `TriageInput` from worker input, delegates to `InvestigationTriageEvaluator`, serialises `TriageResult`
- `CbrPathAdvisorWorker` -- analyses CBR experiences, produces path recommendations
- Both are wired as `FlowWorkerFunction` in `AmlInvestigationCaseDescriptor`

---

## Trust Routing Configuration

Trust routing policies are configurable via YAML at `app/src/main/resources/casehub/aml/trust-routing.yaml`:

| Capability | Threshold | Min Obs | Borderline Margin | Blend Factor | Quality Floor |
|---|---|---|---|---|---|
| entity-resolution | 0.70 | 10 | 0.10 | 0.60 | -- |
| pattern-analysis | 0.65 | 10 | 0.10 | 0.60 | -- |
| osint-screening | 0.70 | 10 | 0.10 | 0.65 | -- |
| sar-drafting | 0.75 | 10 | 0.10 | 0.70 | investigation-accuracy: 0.65 |
| senior-analyst-review | 0.80 | 10 | 0.10 | 0.70 | -- |

`AmlTrustRoutingPolicyProvider` resolves from `PreferenceProvider` first (YAML-backed via `casehub-platform-config`), falls back to hardcoded defaults.

### Cold-Start Beta Seeds

`AmlTrustScoreSeeder` seeds at `@Observes @Priority(20) StartupEvent`:

| Worker | Capability | Beta(alpha, beta) | Mean |
|---|---|---|---|
| sar-drafting-agent-senior | sar-drafting | Beta(9,1) | 0.90 |
| sar-drafting-agent-junior | sar-drafting | Beta(2,8) | 0.20 |
| osint-screening-agent-senior | osint-screening | Beta(9,2) | 0.82 |
| osint-screening-agent | osint-screening | Beta(3,7) | 0.30 |
| entity-resolution-agent | entity-resolution | Beta(8,2) | 0.80 |
| pattern-analysis-agent | pattern-analysis | Beta(8,2) | 0.80 |
| senior-analyst-agent | senior-analyst-review | Beta(10,1) | 0.91 |

---

## Flyway Migration Layout

Two datasources, both pinned explicitly:

**Default datasource** (`db/migration`): casehub-work migrations only.

**Qhorus datasource** (5 migration paths):
- `classpath:db/qhorus/migration` -- qhorus base tables
- `classpath:db/ledger/migration` -- casehub-ledger base tables (V1000-V1999)
- `classpath:db/aml-ledger/migration` -- AML ledger subclass tables (V2001, V2007+)
- `classpath:db/aml-engine-ledger/migration` -- local re-numbered engine-ledger copies (V2002, V2003)
- `classpath:db/aml-trust-routing/migration` -- trust routing attestation (V2004+)

Pinning is mandatory -- default classpath scan picks up jar-shipped migrations causing V-number conflicts.

---

## Oversight Gate Architecture

`AmlActionRiskClassifier @RiskClassifier @ApplicationScoped` -- discovered by the engine via `@RiskClassifier` CDI qualifier. Five action types with gate policies:

| Action Type | Gate Policy | Candidate Groups | Reversibility |
|---|---|---|---|
| `SAR_FILING` | `ALWAYS` | `["aml-mlro"]` | Irreversible |
| `ACCOUNT_RESTRICTION` | `RISK_SCORE_THRESHOLD` | `["aml-compliance"]` | Reversible |
| `TRANSACTION_BLOCKING` | `RISK_SCORE_THRESHOLD` | `["aml-compliance"]` | Reversible |
| `ENTITY_LINK_CREATION` | `CONFIDENCE_THRESHOLD` | `["aml-senior-analyst"]` | Reversible |
| `LAW_ENFORCEMENT_REFERRAL` | `ALWAYS` | `["aml-senior-compliance"]` | Irreversible |

**Fail-closed:** known action type with missing context -> `GateRequired("Risk assessment unavailable")`. Unknown action type -> `Autonomous`.

---

## Design Documents

| Document | What it covers |
|----------|---------------|
| `ARC42STORIES.MD` (project root) | Primary architecture record -- all layers, chapters, gotchas, patterns |
| `LAYER-LOG.md` (project root) | Source-of-truth draft for layer entries; feeds ARC42STORIES.MD |
| `docs/tutorial-strategy.md` (if present) | Tutorial structure and teaching objectives per layer |
| `docs/specs/` | Design specs per feature |

---

## Build and Test

```bash
# Full build (Java 26 JVM, Java 21 source)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install

# App module only
mvn verify -pl api,app -am
```

**Test infrastructure:**
- H2 MODE=PostgreSQL for both datasources
- `casehub.ledger.hash-chain.enabled=false` in test properties -- H2 lacks row-level locking for concurrent Quartz jobs
- Investigation drain pattern: every `@QuarkusTest` that starts an engine investigation polls until `status = "completed"` before returning
- CBR store isolation: call `cbrStore.eraseByScope(Path.root(), TENANT)` at test start

**Key test exclusions** (`quarkus.arc.exclude-types` in test `application.properties`):
- `CbrCaseRetainObserver` -- AML uses its own domain-aware retain logic
- `LedgerVerificationService`, `LedgerComplianceReportService`, `LedgerRetentionJob` -- inject reactive repos vetoed in JDBC-only mode
- `CaseLedgerEntryRepository` -- CDI ambiguity with engine internals
- Casehub-work scheduler beans (4) -- Quartz/cron format clash
- `LeastLoadedAgentStrategy` -- engine SNAPSHOT CDI ambiguity

---

## Anti-patterns

The four failure modes most likely when extending casehub-aml (from ARC42STORIES.MD):

1. **`@Transactional` on the investigation coordinator** -- causes "Unable to acquire JDBC Connection" because the coordinator spans two persistence units. Fix: remove `@Transactional` from coordinator; each service owns its own transaction boundary.

2. **Injecting a concrete implementation type instead of the port interface** -- causes `AmbiguousResolutionException`. Fix: always inject the port interface (`AmlInvestigator`, `AmlInvestigationApplicationService`). `@DefaultBean` displacement works only at the interface level.

3. **`@TestTransaction` on ledger chain tests** -- causes writes to be invisible to queries. Fix: remove `@TestTransaction`; use unique identifiers per test for isolation.

4. **Sealed interface completeness is a compiler guarantee, not a test guarantee** -- `SpecialistOutcome<T>` switch expressions compile exhaustively but untested arms silently misbehave. Fix: one test per permit type per specialist.

---

## Known Technical Debt

| Item | Description | Tracked |
|---|---|---|
| SAR drafting workers still `WorkerFunction.Sync` | Cannot use `FlowWorkerFunction` because FlowWorkerExecutor does not support PlannedAction | engine#564 |
| `entityLinkProposalWorker` in `AmlOversightCaseHub` also `Sync` | Same PlannedAction limitation | engine#564 |
| Trust score not in engine `WorkerDecisionEntry` | `AmlTrustRoutingAttestation` is the workaround; upstream gap | engine#403 |
| No public WorkItem read API | Evidence SLA builder uses direct `EntityManager.find()` | work#241 |
| Account-scoped memory accumulation | Not entity-scoped; beneficial owner across multiple accounts builds no cross-account intelligence | Deferred |
| Cascade GDPR erasure for network cross-references | Account A's erasure removes A's memories but B's memory still names A | Requires legal assessment |
