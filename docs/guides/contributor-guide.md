# casehub-aml -- Contributor Guide

> Internal architecture, module structure, and development context for contributors modifying casehub-aml's internals or extension points.

**GitHub:** [casehubio/aml](https://github.com/casehubio/aml)

---

## Internal Architecture

### Hexagonal Module Structure

Follows hexagonal architecture ([PP-20260512-9b8847](../../parent/docs/protocols/casehub/hexagonal-application-service-placement.md)):

- **`api/`** -- domain layer: pure Java records and service interfaces, zero framework dependencies
- **`app/`** -- application + infrastructure layer: CDI beans, REST resources, Quarkus integration

### Layering Rule

This is an application, not a framework. If the capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

### Investigation CasePlanModel

Adaptive investigation paths (not a fixed pipeline). Key bindings:

- `entity-resolution` fires first on any new transaction -- no prior analysis required
- `pattern-analysis` fires when entity graph complete
- `osint-screening` fires in parallel with pattern-analysis
- `senior-analyst-required` fires if entity type is PEP or risk score > 0.8
- `sar-drafting` fires when all specialist findings complete
- `compliance-officer-review` creates WorkItem with 30-day `claimDeadline` (FinCEN SLA)
- `escalate-to-head-of-compliance` fires if officer WorkItem expires
- `osint-agent-declined` handles DECLINE (agent outside clearance -- immediately re-route)
- `pattern-agent-failed` handles FAILURE -- try backup, escalate if backup also fails

Goals: `investigation-complete`, `sar-approved`, `evidence-chain-complete`.

---

## CBR Integration

AML uses Case-Based Reasoning for investigation triage. Key components:

- `AmlCaseProfileStoreObserver` -- domain-specific retain with `CaseProfile` feature extraction and compliance ledger entries (replaces generic `CbrCaseRetainObserver`)
- `CbrCaseRetainObserver` excluded from both main and test `application.properties` -- AML uses its own domain-aware retain logic
- CBR store isolation in tests: call `cbrStore.eraseByScope(Path.root(), TENANT)` at test start since `InMemoryCbrCaseMemoryStore` retains cases across test classes

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
         AmlInvestigationLedgerEntry, causedByEntryId chain.

Layer 5: + casehub-engine -- adaptive investigation paths (PEP routing, parallel checks);
         YAML bindings, AmlInvestigationCaseHub.

Layer 6: Trust routing -- trust-weighted agent selection from SAR outcome attestations;
         AmlTrustRoutingPolicyProvider, SarOutcomeFeedbackService.

Layer 7: Compliance evidence -- accountability properties mapped against FinCEN/FATF
         requirements; GDPR Art.17 erasure (actor-level and entity-level).

Layer 8: + casehub-platform CaseMemoryStore -- prior entity context (AmlMemoryService,
         AmlPriorContext); SAR outcome memories; YAML binding split for prior-context
         routing; trust seeder corrected.

Layer 9: + casehub-engine-work-adapter (ActionRiskClassifier oversight gate) --
         AmlActionType + AmlActionRiskClassifier + Layer 9 oversight harness
         (AmlOversightCaseHub, AmlOversightCoordinator, AmlLayer9Resource).
```

Status: Layers 1-6, 8, 9 complete; Layer 7 pending.

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

Issues: https://github.com/casehubio/aml/issues?label=epic

---

## Current State

**Status:** In progress -- Layers 1-6, 8, 9 complete; Layer 7 pending.

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Adaptive investigation paths | Complete (engine) |
| DECLINE vs FAILED routing | Complete |
| Parallel specialist checks | Complete |
| Compliance officer WorkItem | casehub-work production |
| Trust-weighted routing | TrustWeightedSelectionStrategy wired in engine |
| LLM triage supervisor | LlmPlanningStrategy SPI (engine) |
| GDPR erasure | LedgerErasureService (casehub-ledger) |
| FinCEN Merkle audit | CaseLedgerEntry complete |
| ActionRiskClassifier oversight gate | casehub-engine-work-adapter complete |

### Web UI (aml#91)

Lit-based web UI built with casehub-blocks-ui components and casehub-pages. Three views: Investigations (case workbench), Compliance (officer work queue), Operations (dashboard with throughput, trust, gates, intervention tabs). Built with Quinoa -- TypeScript compiled with esbuild, hot-reload in dev mode.

---

## Design Documents

| Document | What it covers |
|----------|---------------|
| `ARC42STORIES.MD` (project root) | Primary architecture record |
| `LAYER-LOG.md` (project root) | Source-of-truth draft for layer entries; feeds ARC42STORIES.MD |
| `docs/use-case-analysis.md` (parent) | Use case scoring, AML selection rationale, compliance gap analysis |
| `docs/tutorial-strategy.md` (this repo) | Tutorial structure and teaching objectives per layer |
