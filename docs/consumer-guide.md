# casehub-aml -- Consumer Guide

> Anti-Money Laundering investigation application built on the CaseHub agentic harness -- coordinates specialist agents, compliance officer gates, and adaptive investigation paths to produce a FinCEN-compliant, independently verifiable audit trail.

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
| GDPR on transaction data and PII | Not addressed | `LedgerErasureService` + `ContentSanitiser` |
| Tamper-evident investigation record | No cryptographic audit | Merkle inclusion proofs; independently verifiable |
| Trust-weighted routing -- experienced analysts on complex cases | No trust model | Bayesian Beta from SAR outcome attestations |

---

## Tutorial Layers

The tutorial structure emerges from the natural adoption sequence -- each layer adds one foundation module and makes its value tangible. The code at every layer is production-grade. See `docs/tutorial-strategy.md` for teaching objectives per layer.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Naive Java -- no CaseHub | Baseline anti-pattern | complete |
| 2 | casehub-work | No formal SLA or human task lifecycle for compliance officer review | complete |
| 3 | casehub-qhorus | No formal obligation per specialist agent interaction | complete |
| 4 | casehub-ledger | No tamper-evident FinCEN audit trail | complete |
| 5 | casehub-engine | Fixed investigation pipeline; no adaptive paths | complete |
| 6 | Trust routing | No trust model; random agent selection | complete |
| 7 | Comparison vs IBM AMLSim | -- | pending |
| 8 | casehub-platform `CaseMemoryStore` | No prior entity context across investigations; SAR outcomes not fed back to memory | complete |
| 9 | casehub-engine-work-adapter (`ActionRiskClassifier` oversight gate) | No human oversight gate for consequential agent actions (SAR filing, entity link creation) | complete |

---

## What It Owns

### Domain Model

- `SuspiciousTransaction` -- the flagged transaction that opens a case
- `AmlInvestigationCase` -- the case: transaction, entity graph, pattern findings, OSINT findings, risk score, SAR narrative
- `SuspiciousActivityReport` -- structured filing with narrative, compliance officer sign-off, filing timestamp
- `InvestigationStatus` -- expanded enum: `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`, `SUSPENDED`
- `InvestigationOutcome` -- record(type, reason) surfaced in Layer 6 and Layer 9 APIs
- `InvestigationResolution`, `FailureContext`, `FailureEvent` -- structured investigation status reporting

### Capability Tags

- `entity-resolution` -- resolve beneficial ownership chains from flagged transaction
- `pattern-analysis` -- detect layering, structuring, smurfing patterns across related transactions
- `osint-screening` -- sanctions lists (OFAC/SDN), PEP databases, adverse media
- `sar-drafting` -- synthesise investigation findings into SAR narrative
- `compliance-review` -- compliance officer human WorkItem
- `senior-escalation` -- head of compliance when officer SLA missed
- `investigation-triage` -- LLM supervisor mode: select investigation path based on accumulated context
- `entity-link-proposal` -- propose entity links for cross-investigation correlation
- `investigation-summary` -- generate investigation summary

### Trust Dimensions

- `investigation-accuracy` -- SAR quality: was the SAR upheld, withdrawn, or flagged post-submission?
- `pep-clearance` -- track record on politically exposed person screening
- `scope-awareness` -- does the agent DECLINE correctly when outside its clearance level?

### REST APIs

- `AmlLayer6Resource` -- `/api/layer6/investigations` (async POST, polling GET, outcome POST)
- `AmlLayer7Resource` -- GDPR Art.17 erasure endpoints (actor-level and entity-level)
- `AmlLayer9Resource` -- `/api/layer9/investigations/{caseId}` (GET investigation with outcome, POST suspend, POST resume)

### Key Services

- `AmlTrustRoutingPolicyProvider` -- per-capability trust routing policies (Preferences API with AML defaults)
- `AmlTrustScoreSeeder` -- seeds initial Beta(alpha,beta) trust scores at startup
- `SarOutcomeFeedbackService` -- writes `LedgerAttestation` on SAR outcome, closing the trust feedback loop
- `AmlMemoryService`, `AmlPriorContext`, `AmlMemoryDomains` -- Layer 8 entity context injection before each investigation
- `AmlErasureService` -- GDPR Art.17 erasure (actor-level via `LedgerErasureService`, entity-level via `CaseMemoryStore`)
- `SarDraftingService` SPI -- pure function interface for SAR narrative assembly
- `ComplianceReviewLifecycle` -- consolidates WorkItem creation + ledger write into single call
- `AmlInvestigationOutcomeService` -- resolves investigation resolution from engine state + ledger entries
- `AmlActionRiskClassifier @RiskClassifier` -- Layer 9 ActionRiskClassifier SPI implementation; fail-closed paths derive all gate metadata from domain type

### Ledger Entries

- `AmlCaseOpenedLedgerEntry`, `AmlComplianceReviewLedgerEntry` -- finer-grained audit trail
- `AmlSarOfficerReviewedLedgerEntry` -- records compliance officer's SAR approval or rejection decision

---

## Web UI

Lit-based web UI built with casehub-blocks-ui components and casehub-pages. Three views:

- **Investigations view** -- case workbench with split-pane layout: investigation list (left) and detail tabs (right). Five detail panels: overview, findings, routing, compliance, audit trail
- **Compliance view** -- compliance officer work queue with three-tab perspective (My Work / Claimable / All) and SSE live updates
- **Operations view** -- operational dashboard: throughput metrics, trust scores, oversight gates, intervention reasons

Built with Quinoa (Quarkus frontend integration) -- TypeScript compiled with esbuild, hot-reload in dev mode.

---

## Dependencies

```
casehub-aml
  -> casehub-engine          (investigation CasePlanModel, adaptive paths)
  -> casehub-engine-flow     (FuncWorkflowBuilder worker execution)
  -> casehub-engine-ledger   (TrustWeightedAgentStrategy, WorkerDecisionEventCapture)
  -> casehub-ledger          (Merkle audit, FinCEN evidence chain, GDPR erasure, trust scoring)
  -> casehub-work            (compliance officer WorkItem, 30-day SLA, escalation)
  -> casehub-qhorus          (COMMAND/RESPONSE per specialist agent, commitment lifecycle)
  -> casehub-connectors      (Slack/Teams for SAR assignment notifications)
  -> casehub-neocortex-memory-jpa   (JPA-backed CaseMemoryStore for production)
  -> casehub-neocortex-memory-inmem (in-memory CaseMemoryStore for test isolation)
  -> casehub-blocks                 (reusable building blocks -- routing, oversight, conversation)
  -> casehub-work-engine-adapter    (ActionGateWorkItemHandler + WorkItemLifecycleAdapter for oversight gate)
  -> casehub-engine-planning        (PlanningRegistry -- required for gate signal routing)
  -> casehub-engine-actor-state     (GET /actors/{actorId}/state view)
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
- Oversight gate mechanics -- **casehub-work-engine-adapter**

If a capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation.
