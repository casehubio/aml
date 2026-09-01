# casehub-aml -- Consumer Guide

> Anti-Money Laundering investigation application built on the CaseHub agentic harness -- coordinates specialist agents, compliance officer gates, adaptive investigation paths, case-based reasoning, and investigation triage to produce a FinCEN-compliant, independently verifiable audit trail.

**GitHub:** [casehubio/aml](https://github.com/casehubio/aml)
**Tier:** Application

---

## Purpose

`casehub-aml` is a field showcase and tutorial for Java developers in financial services. It demonstrates that financial crime investigation, SAR filing, and FinCEN/FATF regulatory compliance are structurally better served by a formal accountability layer than by best-effort agentic coordination.

Java dominates banking and financial services infrastructure. Enterprise Java developers at major financial institutions have built or integrated transaction monitoring, case management, and compliance reporting systems. They recognise the failure modes first-hand: audit trails that cannot reconstruct the decision chain, human escalation that fires too late, and SAR filings where nobody can say which agent made the call.

### The Compliance Gap It Closes

| FinCEN/FATF requirement | Without casehub-aml | With casehub-aml |
|---|---|---|
| Auditable evidence chains -- who recommended what and why | Append-only logs inconsistent; no decision attribution | Commitment per agent task; `causedByEntryId` chains the full investigation |
| Human sign-off on SAR filing with 30-day SLA | Ad-hoc escalation; no formal deadline | WorkItem with `claimDeadline`; auto-escalation to head of compliance |
| GDPR on transaction data and PII | Not addressed | `AmlErasureService` + `ContentSanitiser`; entity-level memory erasure |
| Tamper-evident investigation record | No cryptographic audit | Merkle inclusion proofs; independently verifiable |
| Trust-weighted routing -- experienced analysts on complex cases | No trust model | Bayesian Beta from SAR outcome attestations |
| Investigation triage -- SAR warranted vs false positive | Manual analyst judgement with no decision audit | Rule-based triage with CBR adjustment; `InvestigationTriageEvaluator` |
| Prior entity context across investigations | Every investigation starts cold | `AmlMemoryService` queries `CaseMemoryStore` before case start |

---

## Tutorial Layers

The tutorial structure emerges from the natural adoption sequence -- each layer adds one foundation module and makes its value tangible. The code at every layer is production-grade.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Naive Java -- no CaseHub | Baseline anti-pattern | complete |
| 2 | casehub-work | No formal SLA or human task lifecycle for compliance officer review | complete |
| 3 | casehub-qhorus | No formal obligation per specialist agent interaction | complete |
| 4 | casehub-ledger | No tamper-evident FinCEN audit trail | complete |
| 5 | casehub-engine | Fixed investigation pipeline; no adaptive paths | complete |
| 6 | Trust routing | No trust model; random agent selection | complete |
| 7 | Compliance evidence | No externally verifiable evidence endpoint | complete |
| 8 | casehub-platform `CaseMemoryStore` | No prior entity context across investigations; SAR outcomes not fed back to memory | complete |
| 9 | casehub-engine-work-adapter (`ActionRiskClassifier` oversight gate) | No human oversight gate for consequential agent actions (SAR filing, entity link creation) | complete |

---

## What It Owns

### Domain Model

Records in `api/src/main/java/io/casehub/aml/domain/`:

- `SuspiciousTransaction` -- the flagged transaction that opens a case; fields: `id` (String), `flagReason` (FlagReason), `amount` (BigDecimal), `originAccountId`, `destinationAccountId`
- `AmlInvestigationResult` -- investigation output: `summary`, `complianceReviewTaskId`, `caseId`, `ledgerCaseEntryId`
- `InvestigationSummary` -- three `SpecialistOutcome<T>` fields for entity resolution, pattern analysis, OSINT
- `SpecialistOutcome<T>` -- sealed interface: `Completed<T>`, `Declined`, `Failed`; pattern-matched in specialist workers
- `EntityResolutionResult` -- gains `entityType` (EntityType enum) and `riskScore` (double) for PEP routing
- `PatternAnalysisResult` -- pattern findings record
- `OsintResult` -- OSINT screening result
- `SuspiciousActivityReport` -- structured filing with narrative, compliance officer sign-off, filing timestamp
- `SarOutcome` -- record: `verdict` (SarVerdict), `reason`, `investigationAccuracyScore`; compact constructor validates score in [0.0, 1.0]
- `SarVerdict` -- enum: `UPHELD`, `WITHDRAWN`, `FLAGGED`
- `InvestigationStatus` -- enum: `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`, `SUSPENDED`
- `InvestigationOutcome` -- record(type, reason) surfaced in Layer 6 and Layer 9 APIs
- `InvestigationResolution`, `FailureContext`, `FailureEvent` -- structured investigation status reporting
- `FlagReason` -- enum classifying why a transaction was flagged
- `EntityType` -- enum: `PEP`, `CORPORATE`, `INDIVIDUAL`, etc.
- `HardGate` -- enum of hard-gate conditions that override triage (e.g. sanctions match)

#### CBR Domain Types

- `CaseProfile` -- feature vector for case similarity: `flagReason`, `amount`, `priorCaseCount`, `entityType`, `jurisdictionRisk`, `networkComplexity`
- `CbrPathAdvice` -- path recommendations from similar past cases
- `SeedNarrative` -- sanitised SAR narrative from a similar past case for narrative seeding
- `TriageInput` -- input record for `InvestigationTriageEvaluator`
- `TriageDecision` -- enum: `SAR_WARRANTED`, `FALSE_POSITIVE`, `INCONCLUSIVE`
- `TriageResult` -- record: `decision`, `score`, `hardGate`, `cbrAdjustment`
- `JurisdictionRisk`, `NetworkComplexity`, `RiskFactor` -- CBR feature enums

#### API Model Types (Web UI)

Records in `api/src/main/java/io/casehub/aml/api/model/`:

- `ThroughputMetrics`, `TrustScoreMetrics`, `GateMetrics` -- operations dashboard data
- `AgentTrustScore`, `TrustScoreSnapshotResponse` -- trust score display and history
- `InvestigationFlowResponse`, `FlowNode`, `FlowEdge` -- case flow visualisation
- `InvestigationFindingsResponse`, `SpecialistFindingResponse` -- specialist findings detail
- `InvestigationGatesResponse`, `GateDecisionResponse` -- oversight gate decisions
- `InvestigationSummaryResponse`, `PagedResponse` -- investigation list with pagination

#### Compliance API Types

Records in `api/src/main/java/io/casehub/aml/compliance/`:

- `ComplianceEvidence` -- root aggregate: four requirement-scoped records
- `RequirementStatus` -- enum: `CLOSED`, `PARTIAL`, `BREACHED`, `GAP`
- `AuditChainRequirement` -- `FINCEN-31CFR1020.320-AUDIT-CHAIN`; includes `chainVerified`, `treeRoot`, per-event `AmlInclusionProof` list
- `SlaRequirement` -- `FINCEN-SAR-30DAY-SLA`; includes deadline, completion time
- `TrustRoutingRequirement` -- `FATF-R20-TRUST-ROUTING`
- `GdprErasureRequirement` -- `GDPR-ART17-ERASURE`; dynamic -- queries config flags and receipt count; `retentionCitation` and `retentionAdrRef` surface Art.17(3)(b) regulatory retention exemption (ADR-0004)
- `ActorErasureResult` -- flattened actor erasure result
- `LedgerEventRecord`, `AmlInclusionProof`, `AmlProofStep` -- evidence structure types

#### Triage API Types

Pure-Java triage logic in `api/src/main/java/io/casehub/aml/triage/`:

- `InvestigationTriageEvaluator` -- orchestrates risk scoring, hard gate checks, and CBR adjustment
- `RiskScorer` -- weighted risk scoring from specialist findings
- `HardGateEvaluator` -- checks for mandatory-SAR conditions (sanctions, law enforcement referral)
- `CbrAdjuster` -- adjusts triage decision using CBR path advice from similar past cases

### Capability Tags

YAML case definition capabilities in `aml-investigation.yaml`:

- `entity-resolution` -- resolve beneficial ownership chains from flagged transaction
- `pattern-analysis` -- detect layering, structuring, smurfing patterns across related transactions
- `osint-screening` -- sanctions lists (OFAC/SDN), PEP databases, adverse media
- `senior-analyst-review` -- senior analyst investigation for PEP entities and high-risk cases
- `sar-drafting` -- synthesise investigation findings into SAR narrative; declares `PlannedAction(SAR_FILING)`
- `cbr-path-advisor` -- analyse similar past cases and produce path recommendations
- `investigation-triage` -- evaluate specialist findings and decide whether SAR is warranted
- `compliance-review-opening` -- open compliance officer review WorkItem after MLRO gate approval

Oversight case definition capabilities in `aml-oversight-investigation.yaml`:

- `entity-resolution` -- identify entities and risk profile
- `entity-link-proposal` -- propose entity network link; declares `PlannedAction(ENTITY_LINK_CREATION)`
- `investigation-summary` -- summarise findings after entity link is confirmed

### Trust Dimensions

- `investigation-accuracy` -- SAR quality: was the SAR upheld, withdrawn, or flagged post-submission?
- `pep-clearance` -- track record on politically exposed person screening
- `scope-awareness` -- does the agent DECLINE correctly when outside its clearance level?

### REST APIs

#### Investigation Lifecycle

- `AmlInvestigationResource` -- `POST /api/investigations` (Layer 1 baseline entry point)
- `AmlLayer5Resource` -- `POST /api/layer5/investigations` (engine-driven, Layer 5)
- `AmlLayer6Resource` -- `POST /api/layer6/investigations` (trust-weighted, Layer 6, async 202); `GET /api/layer6/investigations/{caseId}` (routing decisions + trust scores + outcome); `POST /api/layer6/investigations/{caseId}/outcome` (SAR verdict feedback, 204)
- `AmlLayer9Resource` -- `GET /api/layer9/investigations/{caseId}` (investigation with outcome); `POST /api/layer9/investigations/{caseId}/suspend`; `POST /api/layer9/investigations/{caseId}/resume`

#### Investigation Query

- `AmlInvestigationQueryResource` -- `GET /api/investigations` (paginated list with status filter); `GET /api/investigations/{caseId}/prior-context`; `GET /api/investigations/{caseId}/flow` (case flow graph); `GET /api/investigations/{caseId}/findings` (specialist findings); `GET /api/investigations/{caseId}/gates` (oversight gate decisions)

#### Compliance and GDPR

- `AmlLayer7Resource` -- `GET /api/investigations/{caseId}/compliance-evidence` (four requirement-scoped records with Merkle proofs)
- `AmlErasureService` -- GDPR Art.17 erasure: `POST /api/actors/{actorId}/erasure` (actor-level), entity-level memory erasure, tenant-scoped and cross-tenant variants via `CrossTenantErasureRequest`/`CrossTenantErasureResult`
- `AmlProvenanceResource` -- `GET /api/investigations/{caseId}/provenance` (W3C PROV-DM JSON export of investigation lineage)

#### Audit Trail

- `AmlAuditTrailResource` -- `GET /api/investigations/{caseId}/audit-trail` (ledger entries for case); `GET /api/investigations/{caseId}/audit-trail/{entryId}/proof` (Merkle inclusion proof for entry)

#### Operational

- `AmlMetricsResource` -- `GET /api/metrics/throughput` (investigation throughput); `GET /api/metrics/trust-scores` (current trust scores); `GET /api/metrics/gates` (gate decision counts); `GET /api/metrics/trust-scores/history` (historical snapshots, query params: `agentId`, `capability`)
- `AmlSimulationResource` -- `POST /api/simulation/seed` (seed all scenarios); `POST /api/simulation/seed/{scenario}` (seed specific scenario); `DELETE /api/simulation/seed` (reset); `POST /api/simulation/investigate` (start live investigation from scenario); `POST /api/simulation/seed/cbr` (seed CBR case base with synthetic cases); `DELETE /api/simulation/seed/cbr` (clear CBR case base)
- `AmlCbrResource` -- `GET /api/cbr/bootstrap-report` (case base coverage by dimension + advisory metrics; not simulation-gated)

### Key Services

#### Investigation Coordination

- `AmlEngineCoordinator` -- starts engine case, writes `CASE_OPENED` ledger entry with engine-returned UUID
- `AmlInvestigationCaseHub extends YamlCaseHub` -- thin CDI wrapper; delegates worker functions to `AmlInvestigationCaseDescriptor`
- `AmlInvestigationCaseDescriptor` -- plain POJO carrying 8 worker functions (6 `FlowWorkerFunction`, 2 `WorkerFunction.Sync` for PlannedAction support)
- `AmlOversightCaseHub` -- Layer 9 oversight harness case definition
- `AmlOversightCoordinator` -- starts oversight investigation case
- `ComplianceReviewLifecycle` -- consolidates WorkItem creation + ledger write into single call; stable across Layers 3-9

#### Trust Routing

- `AmlTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider` -- per-capability trust routing policies; resolves overrides from YAML-backed `PreferenceProvider` at `casehub/aml/trust-routing.yaml`, falls back to hardcoded defaults
- `AmlTrustScoreSeeder` -- seeds initial Beta(alpha,beta) trust scores at startup; idempotency-guarded
- `SarOutcomeFeedbackService` -- writes `LedgerAttestation` on SAR outcome, closing the trust feedback loop
- `AmlTrustRoutingObserver` -- `@ObservesAsync WorkerDecisionEvent`; captures `trustScoreAtRouting` immutably before cache drifts
- `TrustScoreSnapshotService` -- periodic trust score snapshots for historical trend persistence

#### Memory and Prior Context

- `AmlMemoryService` -- central `CaseMemoryStore` gateway; `queryPriorContext()` retrieves prior entity risk, network, and pattern facts before case start
- `AmlPriorContext` -- value record with `hasHistory()`, `isKnownHighRisk()`, `toContextMap()`
- `AmlMemoryDomains` -- three `MemoryDomain` constants: `ENTITY_RISK`, `NETWORK`, `PATTERN`
- `AmlSarOutcomeMemoryObserver` -- CDI observer writing SAR outcome memories for both account IDs

#### Case-Based Reasoning (CBR)

- `AmlCaseProfileStoreObserver implements CaseOutcomeObserver` -- domain-specific retain; extracts `CaseProfile` features and writes compliance ledger entries on case completion
- `CaseProfileExtractor` -- extracts `CaseProfile` from transaction and prior context
- `CbrPathAdvisorWorker` -- analyses similar past cases and produces `CbrPathAdvice` with `active` field (activation threshold gating)
- `InvestigationTriageWorker` -- rule-based SAR/FP/INCONCLUSIVE classification with CBR adjustment (only when `CbrPathAdvice.active == true`)
- `SarNarrativeSeeder` -- extracts sanitised narratives from similar past cases for narrative seeding; uses `ContentSanitiser` for PII protection
- `CbrSyntheticSeeder` -- generates `PlanCbrCase` entries directly to `CbrCaseMemoryStore` for dev/demo bootstrapping; deterministic coverage across flag reasons, entity types, and outcomes
- `AmlCbrPolicyKeys` -- `PreferenceProvider` key for CBR activation threshold (default: 30 similar cases)
- `AmlCbrSchema`, `AmlCbrSchemaRegistrar` -- CBR schema definition and registration

#### Compliance and GDPR

- `AmlComplianceEvidenceService` -- assembles four requirement-scoped evidence records
- `AmlErasureService` -- wraps `LedgerErasureService` for actor-level erasure + `CaseMemoryStore.eraseEntity()` for entity-level memory erasure
- `AmlWorkItemLifecycleObserver` -- observes WorkItem lifecycle events for SAR officer review
- `RetentionExpiryJob` -- `@Scheduled` automated retention expiry erasure job

#### Oversight Gate

- `AmlActionRiskClassifier @RiskClassifier` -- Layer 9 `ActionRiskClassifier` SPI implementation; fail-closed; recognises five `AmlActionType` values
- `AmlActionType` -- enum: `SAR_FILING`, `ACCOUNT_RESTRICTION`, `TRANSACTION_BLOCKING`, `ENTITY_LINK_CREATION`, `LAW_ENFORCEMENT_REFERRAL`; each encodes `GatePolicy`, reversibility, candidate groups, reason string

#### Investigation Outcome

- `AmlInvestigationOutcomeService` -- resolves `InvestigationResolution` from engine state + ledger entries
- `AmlInvestigationFindingsService` -- specialist findings for UI
- `AmlInvestigationFlowService` -- case flow graph for UI
- `AmlInvestigationGatesService` -- oversight gate decisions for UI
- `AmlInvestigationPriorContextService` -- prior context data for UI

#### Investigation Summary (Query/Read Model)

- `InvestigationSummaryService` -- creates and updates investigation summary view
- `InvestigationSummaryObserver` -- CDI observer maintaining the read model
- `InvestigationSummaryRepository` -- JPA repository for paginated queries
- `InvestigationSummaryView` -- JPA entity for the investigation list

#### Metrics and Simulation

- `AmlMetricsService` -- throughput, trust score, and gate decision metrics
- `AmlSimulationService` -- scenario seeding for demo/showcase
- `AmlScenarioTemplate` -- predefined investigation scenarios

### Ledger Entries

JPA entities in `app/src/main/java/io/casehub/aml/ledger/`:

- `AmlCaseOpenedLedgerEntry` -- CASE_OPENED with `originAccountId`, `destinationAccountId` (non-nullable)
- `AmlComplianceReviewLedgerEntry` -- COMPLIANCE_REVIEW_OPENED with `taskId`
- `AmlSarOfficerReviewedLedgerEntry` -- records compliance officer's SAR approval or rejection decision
- `AmlCaseProfileLedgerEntry` -- CBR case profile retained on case completion
- `AmlCbrAdvisoryLedgerEntry` -- CBR advisory recommendations ledger record
- `AmlEntityErasureLedgerEntry` -- entity-level GDPR erasure receipt

### Trust Routing Attestation

- `AmlTrustRoutingAttestation` -- JPA entity; JOINED from `LedgerEntry`; captures `trustScoreAtRouting` and `thresholdApplied` immutably at routing time
- `AmlAttestationReconciler` -- fills attestation gaps lazily on evidence read when observer async call failed
- `TrustScoreSnapshot` -- JPA entity for historical trust score persistence

---

## Web UI

Lit-based web UI built with casehub-blocks-ui components and casehub-pages. Frontend is in `app/src/main/webui/`, compiled with esbuild via Quinoa (Quarkus frontend integration), hot-reload in dev mode.

### Views

- **Investigations view** (`aml-app.ts`) -- case workbench with split-pane layout: investigation list (left, paginated) and detail tabs (right)
- **Compliance view** (`aml-compliance-panel.ts`) -- compliance officer work queue
- **Operations view** (`views/operations.ts`) -- operational dashboard with throughput metrics, trust scores, oversight gates, intervention reasons

### Detail Panels

- `aml-investigation-overview.ts` -- case overview
- `aml-findings-panel.ts` -- specialist findings detail
- `aml-routing-panel.ts` -- trust routing decisions
- `aml-compliance-panel.ts` -- compliance status
- `aml-audit-trail.ts` -- tamper-evident audit trail with Merkle proofs

### Showcase

- `showcase/` directory -- standalone showcase page for AML workbench UI components with mock data and element guards

---

## Dependencies

```
casehub-aml
  -> casehub-engine               (investigation CasePlanModel, adaptive paths)
  -> casehub-engine-flow          (FuncWorkflowBuilder worker execution; FlowWorkerExecutor)
  -> casehub-engine-ledger        (TrustWeightedAgentStrategy, WorkerDecisionEventCapture)
  -> casehub-engine-scheduler-quartz  (Quartz-based async binding dispatch)
  -> casehub-engine-planning      (PlanningRegistry -- required for gate signal routing)
  -> casehub-engine-actor-state   (GET /actors/{actorId}/state view)
  -> casehub-engine-persistence-memory  (in-memory engine repos for dev/test)
  -> casehub-ledger               (Merkle audit, FinCEN evidence chain, GDPR erasure, trust scoring)
  -> casehub-work                 (compliance officer WorkItem, 30-day SLA, escalation)
  -> casehub-work-engine-adapter  (ActionGateWorkItemHandler + WorkItemLifecycleAdapter for oversight gate)
  -> casehub-qhorus               (COMMAND/RESPONSE per specialist agent, commitment lifecycle)
  -> casehub-neocortex-memory-api (CaseMemoryStore interface)
  -> casehub-neocortex-memory     (runtime memory implementation)
  -> casehub-neocortex-memory-jpa (JPA-backed CaseMemoryStore for production)
  -> casehub-neocortex-memory-inmem  (in-memory CaseMemoryStore for test isolation)
  -> casehub-neocortex-memory-cbr-inmem  (in-memory CBR store for test isolation)
  -> casehub-blocks               (reusable building blocks -- routing, oversight, conversation)
  -> casehub-platform             (MockPreferenceProvider @DefaultBean)
  -> casehub-platform-config      (YAML-backed PreferenceProvider -- displaces MockPreferenceProvider)
  -> casehub-platform-expression  (JQEvaluator for engine CDI beans)
  -> casehub-pages-npm            (frontend page framework)
  -> casehub-blocks-ui-npm        (frontend UI component library)
  -> quarkus-quinoa               (frontend build integration)
```

---

## What It Does NOT Own

This is an application, not a framework. The following belong in foundation modules:

- Case lifecycle, plan execution, adaptive paths -- **casehub-engine**
- Commitment lifecycle (COMMAND/RESPONSE/DONE/DECLINE) -- **casehub-qhorus**
- Merkle audit, trust scoring, GDPR erasure primitives -- **casehub-ledger**
- WorkItem lifecycle, SLA management, escalation -- **casehub-work**
- Notification routing (Slack, Teams) -- **casehub-connectors**
- Entity memory store -- **casehub-neocortex**
- CBR case memory store -- **casehub-neocortex** (cbr module)
- Oversight gate mechanics -- **casehub-work-engine-adapter**
- Content sanitisation -- **casehub-ledger** (`ContentSanitiser`)

If a capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation.
