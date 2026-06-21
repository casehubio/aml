# aml Workspace

**Name:** casehub-aml

**Project repo:** /Users/mdproctor/claude/casehub/aml
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/aml` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session: a **workspace** (methodology artifacts: handover, blog, specs, plans, ADRs) and the **project repo** (source code).

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace

**Pre-push hook (`core.hooksPath = .githooks`):** The project repo has a hook that blocks all pushes containing commits — including empty ones. All `chore: branch closed` stamps require `--no-verify` on push, regardless of whether the branch has an existing remote upstream. See garden entry GE-20260531-2f51fd for root cause detail.

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | epic journal stays in workspace |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# casehub-aml — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
../parent/docs/PLATFORM.md
```

**Foundation repo deep-dives:**
- casehub-engine: `../parent/docs/repos/casehub-engine.md`
- casehub-ledger: `../parent/docs/repos/casehub-ledger.md`
- casehub-work: `../parent/docs/repos/casehub-work.md`
- casehub-qhorus: `../parent/docs/repos/casehub-qhorus.md`
- casehub-connectors: `../parent/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## Agentic Harness Goals

**Read first:** `../parent/docs/AGENTIC-HARNESS-GUIDE.md`

**Goal:** Production-grade AML investigation harness demonstrating that financial crime investigation, SAR filing, and FinCEN/FATF regulatory compliance are structurally better served by a formal accountability layer than by best-effort agentic coordination.

**Architecture record:** `ARC42STORIES.MD` (project root) is the primary architecture record. `LAYER-LOG.md` remains as the source-of-truth draft that feeds it; both must be kept in sync when layers are extended. When a layer is extended or a new layer opens, write the LAYER-LOG entry first, then sync to `ARC42STORIES.MD §9.4`.

---

## What This Project Is

`casehub-aml` is the **Anti-Money Laundering investigation application** built on the CaseHub platform foundation.

This is an **application layer**, not a framework. The foundation (casehub-engine, casehub-qhorus, casehub-ledger, casehub-work, casehub-connectors) provides coordination, accountability, audit, and compliance primitives. casehub-aml provides the financial crime investigation domain logic on top: what a suspicious transaction is, how an AML investigation proceeds, which specialists handle what, and how a SAR reaches a compliance officer.

### Why AML

Java dominates banking and financial services infrastructure. Enterprise Java developers at major financial institutions have built or integrated transaction monitoring, case management, and compliance reporting systems. They recognise the failure modes first-hand: audit trails that cannot reconstruct the decision chain, human escalation that fires too late, and SAR filings where nobody can say which agent made the call.

### Accountability Properties Delivered

| FinCEN/FATF requirement | Without casehub-aml | With casehub-aml |
|---|---|---|
| Auditable evidence chains — who recommended what and why | Append-only logs inconsistent; no decision attribution | Commitment per agent task; `causedByEntryId` chains the full investigation |
| Human sign-off on SAR filing with 30-day SLA | Ad-hoc escalation; no formal deadline | WorkItem with `claimDeadline`; auto-escalation to head of compliance |
| GDPR on transaction data and PII | Not addressed | `LedgerErasureService` + `DecisionContextSanitiser` |
| Tamper-evident investigation record | No cryptographic audit | Merkle inclusion proofs; independently verifiable |
| Trust-weighted routing — experienced analysts on complex cases | No trust model | Bayesian Beta from SAR outcome attestations |

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

---

## Reference Documents (in casehub-parent)

| Document | What it covers |
|----------|---------------|
| `../parent/docs/use-case-analysis.md` | Use case scoring, AML selection rationale (§8.2), compliance gap analysis |
| `../parent/docs/repos/casehub-aml.md` | AML domain ownership — entities, capability tags, trust dimensions, epics |

---

## Design Phase References

Read these **before designing**, not after. The concern column tells you when each applies.

### Domain model and API design

| Concern | Read first |
|---------|-----------|
| Designing a new entity, record, or SPI | `casehub-aml.md` — does AML already own this? `PLATFORM.md` capability ownership table — does the foundation already own this? |
| Deciding api vs app module placement | `PLATFORM.md` persistence module split rule — JPA-free api, JPA in app. Use-case orchestration lives in app/ (PP-20260512-9b8847, parent#18) |
| Naming capability tags or trust dimensions | `casehub-aml.md` §What It Owns — existing tag and dimension names |
| Mapping entities to FinCEN/FATF requirements | `use-case-analysis.md §8.2` — compliance gap table, which requirement drives each entity |

### Layer design

| Concern | Read first |
|---------|-----------|
| Deciding which layer a feature belongs in | Foundation Layers section below |
| Documenting a completed layer | LAYER-LOG.md — write the entry before closing the issue |

### Foundation integration

| Concern | Read first |
|---------|-----------|
| Using casehub-work (WorkItem, SLA, escalation) | `../parent/docs/repos/casehub-work.md` |
| Using casehub-qhorus (COMMAND/RESPONSE/DONE/DECLINE) | `../parent/docs/repos/casehub-qhorus.md` |
| Using casehub-ledger (Merkle audit, GDPR, trust scoring) | `../parent/docs/repos/casehub-ledger.md` |
| Using casehub-engine (CasePlanModel, adaptive paths, bindings) | `../parent/docs/repos/casehub-engine.md` |

> **Engine worker return type:** `Worker.Builder.function()` requires `Function<Map<String, Object>, WorkerResult>`. Return `WorkerResult.of(Map.of(...))` — not `Map.of(...)` directly. Applies to all `YamlCaseHub` worker lambdas (casehubio/aml#54).
> **LedgerEntryRepository + LedgerVerificationService tenancyId parameter:** All methods in `LedgerEntryRepository` and `LedgerVerificationService` require a `String tenancyId` second parameter (SNAPSHOT change). Use `TenancyConstants.DEFAULT_TENANT_ID` at all AML call sites. Affected methods: `save()`, `findBySubjectId()`, `findLatestBySubjectId()`, `findEntryById()`, `verify()`, `treeRoot()`, `inclusionProof()`.
| Boundary check — does this belong in foundation or here? | `PLATFORM.md` boundary rules and application tier rule |

### Persistence and migrations

| Concern | Read first |
|---------|-----------|
| Writing a new Flyway migration | `../garden/docs/protocols/universal/flyway-migration-rules.md` — naming, H2 MODE=PostgreSQL |
| Assigning a migration version number | `../garden/docs/protocols/casehub/flyway-version-range-allocation.md` — V1–V999 domain, V1004+ ledger subclass joins |
| Adding a named persistence unit or datasource | *(protocol not yet written — `quarkus-named-datasource-schema-generation`)* |
| Extending LedgerEntry (adding a tamper-evident subclass) | `casehub-ledger.md` Consumer Pattern section — JOINED inheritance, V2001+ migration (V2000 = qhorus join table; consumer joins start V2001) |

### Testing

| Concern | Read first |
|---------|-----------|
| Writing a `@QuarkusTest` | `../garden/docs/protocols/universal/quarkus-test-database.md` — H2 MODE=PostgreSQL, datasource config |
| Naming test classes | *(protocol not yet written — `quarkus-test-naming-convention`)* |
| Testing SPI wiring | *(protocol not yet written — `spi-testing-alternative-inner-classes`)* |
| Writing integration tests | *(protocol not yet written — `quarkus-integration-test-module-separation`)* |

---

## What casehub-aml Must Build

### Domain Model

**Investigation entities:**
- `SuspiciousTransaction` — the flagged transaction that opens a case
- `AmlInvestigationCase` — the case: `{transaction, entityGraph, patternFindings, osintFindings, riskScore, sarNarrative}`
- `SuspiciousActivityReport` — the SAR: structured filing with narrative, compliance officer sign-off, filing timestamp

**Capability tags:**
- `entity-resolution` — resolve beneficial ownership chains from flagged transaction
- `pattern-analysis` — detect layering, structuring, smurfing patterns across related transactions
- `osint-screening` — sanctions lists (OFAC/SDN), PEP databases, adverse media
- `sar-drafting` — synthesise investigation findings into SAR narrative
- `compliance-review` — compliance officer human WorkItem — review and sign SAR
- `senior-escalation` — head of compliance when officer SLA missed
- `investigation-triage` — LLM supervisor mode: select investigation path based on accumulated context

**Trust dimensions:**
- `investigation-accuracy` — SAR quality: was the SAR upheld, withdrawn, or flagged post-submission?
- `pep-clearance` — track record on politically exposed person screening specifically
- `scope-awareness` — does the agent DECLINE correctly when outside its clearance level?

### Investigation CasePlanModel (adaptive, not fixed pipeline)

Goals:
- `investigation-complete` — all required specialist findings present
- `sar-approved` — compliance officer WorkItem DONE with `file` decision
- `evidence-chain-complete` — all `causedByEntryId` links present in ledger

Key bindings:
- `entity-resolution` fires first on any new transaction — no prior analysis required
- `pattern-analysis` fires when entity graph complete
- `osint-screening` fires in parallel with pattern-analysis — simultaneous, no declaration
- `senior-analyst-required` fires if entity type is PEP or risk score > 0.8 — routing to senior, not junior
- `sar-drafting` fires when all specialist findings complete
- `compliance-officer-review` creates WorkItem with 30-day `claimDeadline` (FinCEN SLA)
- `escalate-to-head-of-compliance` fires if officer WorkItem expires
- `osint-agent-declined` handles DECLINE (agent outside clearance — immediately re-route, agent is healthy)
- `pattern-agent-failed` handles FAILURE — try backup, escalate if backup also fails

### Foundation Layers

Each layer corresponds to a foundation module integration step. LAYER-LOG.md tracks completion — a layer is not complete until its entry is written. Layers map to arc42stories §9.4 Layer Entries.

```
Layer 1: Domain baseline — hexagonal architecture, @DefaultBean displacement pattern,
         REST API for AML investigations. ✅

Layer 2: + casehub-work — compliance officer WorkItem with 30-day FinCEN claimDeadline;
         CDI displacement pattern. ✅

Layer 3: + casehub-qhorus — typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent;
         composer pattern, SpecialistOutcome sealed interface. ✅

Layer 4: + casehub-ledger — FinCEN audit trail, Merkle chain, GDPR Art.17 erasure;
         AmlInvestigationLedgerEntry, causedByEntryId chain. ✅

Layer 5: + casehub-engine — adaptive investigation paths (PEP routing, parallel checks);
         YAML bindings, AmlInvestigationCaseHub. ✅

Layer 6: Trust routing — trust-weighted agent selection from SAR outcome attestations;
         AmlTrustRoutingPolicyProvider, SarOutcomeFeedbackService. ✅

Layer 7: Compliance evidence — accountability properties mapped against FinCEN/FATF
         requirements. See LAYER-LOG.md §Layer 7. ✅

Layer 8: + casehub-platform CaseMemoryStore — prior entity context (AmlMemoryService,
         AmlPriorContext); SAR outcome memories; YAML binding split for prior-context
         routing; trust seeder corrected. See LAYER-LOG.md §Layer 8. ✅

Layer 9: + casehub-engine-work-adapter (ActionRiskClassifier oversight gate) —
         AmlActionType + AmlActionRiskClassifier + Layer 9 oversight harness
         (AmlOversightCaseHub, AmlOversightCoordinator, AmlLayer9Resource).
         See LAYER-LOG.md §Layer 9. ✅
```

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Adaptive investigation paths | P0 complete (engine#186 ✅) |
| DECLINE vs FAILED routing | P0 complete |
| Parallel specialist checks | P0 complete |
| Compliance officer WorkItem | casehub-work ✅ production |
| Trust-weighted routing | P1.3 TrustWeightedSelectionStrategy wired in engine |
| LLM triage supervisor | LlmPlanningStrategy SPI (engine) |
| GDPR erasure | LedgerErasureService (casehub-ledger ✅) |
| FinCEN Merkle audit | CaseLedgerEntry ✅ (2026-04-26) |
| ActionRiskClassifier oversight gate | casehub-engine-work-adapter ✅ (aml#42, 2026-06-09) |

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Java on this machine:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # native only
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**Multi-module test scoping:** Always scope Maven with `-pl <module> -am`. When combining `-am` with `-Dtest=ClassName`, add `-Dsurefire.failIfNoSpecifiedTests=false` — otherwise upstream modules that have no matching tests fail the build.

---


## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
Before committing: `superpowers:requesting-code-review`

Living docs — check for drift after significant changes:
- `docs/adr/INDEX.md`

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/aml

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Every issue must be linked to its parent epic — no orphan issues.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`. No commit may be made without an issue reference.

---

## Engineering Standards

### IntelliJ MCP — availability and preference

**Two MCPs are available:** `mcp__intellij__*` (file ops, refactoring, search, terminal) and `mcp__intellij-index__*` (symbol search, find references, go-to-definition, type hierarchy, call hierarchy).

**Check availability at session start.** If either MCP is unavailable, stop and report before doing any work — do not silently fall back to Bash.

**Always prefer IntelliJ over Bash** for operations the IDE can perform. IntelliJ is more correct, context-aware, and less error-prone than shell commands for:

| Operation | Use IntelliJ tool, not Bash |
|-----------|----------------------------|
| Find a class, symbol, or file | `ide_find_class`, `ide_find_file`, `ide_search_text` |
| Navigate to a definition | `ide_find_definition` |
| Find all references before renaming/deleting | `ide_find_references` |
| Rename a symbol across the project | `ide_refactor_rename` |
| Move a file | `ide_move_file` |
| Check for errors in a file | `ide_diagnostics` |
| Build the project | `build_project` |
| Read a file by project-relative path | `get_file_text_by_path` |
| Search for text across files | `search_in_files_by_text` |

Only use Bash when the operation is outside IntelliJ's scope: git commands, Maven, file creation, shell scripts.

### Test-driven development

Tests are part of the implementation plan — not written after the fact. Every implementation plan must include a testing section covering all four layers:

| Layer | Scope | Convention to follow |
|-------|-------|---------------------|
| Unit tests | Pure domain logic in `api/` — records, interfaces, value types | Standard JUnit 5, no Quarkus |
| `@QuarkusTest` | Service integration within the running application | `quarkus-test-database.md`, `quarkus-test-naming-convention.md` |
| Integration tests | Full HTTP round-trip via REST Assured | `quarkus-integration-test-module-separation.md` — separate `integration-tests/` module |
| SPI wiring tests | CDI alternative pattern for testing SPI implementations | `spi-testing-alternative-inner-classes.md` |

For each test layer, cover: **happy path**, **robustness** (bad input, nulls, boundary values), and **correctness** (business rule enforcement, not just "no exception").

Consult `docs/conventions/` in the local parent before writing any test — the platform has resolved many Quarkus testing edge cases that will bite you otherwise.

**Test schema note:** Both datasources use Flyway in `@QuarkusTest`. Flyway locations are pinned explicitly in both `application.properties` files (test and main) to prevent future classpath additions from silently adding unexpected migrations:
- Default datasource: `quarkus.flyway.locations=classpath:db/migration` (casehub-work migrations only)
- Qhorus datasource: `quarkus.flyway.qhorus.locations=classpath:db/qhorus/migration,classpath:db/ledger/migration` — the qhorus PU manages ledger entities (`casehub.ledger.datasource=qhorus`), so ledger migrations must run on this datasource. This overrides the qhorus extension default (`db/qhorus/migration` only).

**Investigation @QuarkusTest conventions (Layer 8+):**
- `casehub.ledger.hash-chain.enabled=false` in test `application.properties` — H2 lacks row-level locking; concurrent Quartz jobs for the same case violate `UQ_MERKLE_FRONTIER_SUBJECT_LEVEL` (protocol PP-20260604-f45c95). Hash chain correctness is tested in casehub-ledger; consumer app tests verify entry structure only.
- Every test that starts an engine investigation must drain to `status=completed` by polling `GET /api/layer6/investigations/<id>` before the test method returns (protocol PP-20260604-820c35). Tests asserting partial progress (e.g. "senior-analyst was scheduled") must still drain to prevent pending Quartz jobs from contaminating subsequent tests.
- **Ledger subject isolation:** AML `LedgerEntry` subclasses must not share `subjectId` with engine entries for the same case. Use `UUID.nameUUIDFromBytes("aml-<concern>:" + caseId)` as the `subjectId`. The `IDX_LEDGER_ENTRY_SUBJECT_SEQ` constraint is global across all dtypes — sequence assignment scoped to a single subclass silently misses domain entries and causes phantom violations (GE-20260607-1c0a05).
- **casehub-engine-work-adapter CDI exclusions:** With `casehub-engine-work-adapter` on the classpath, `JpaPlanItemStore @ApplicationScoped` (non-alternative) competes with `MemoryPlanItemStore @Alternative`. Add `io.casehub.workadapter.JpaPlanItemStore` to `quarkus.arc.exclude-types` in test properties.
- **casehub-work SNAPSHOT (June 2026) — `TenantScopedPrincipal @RequestScoped`:** The work SNAPSHOT added `TenantScopedPrincipal @RequestScoped`, creating a three-way `CurrentPrincipal` ambiguity with `MockCurrentPrincipal` and `QhorusInboundCurrentPrincipal`. Fixed in AML by excluding `TenantScopedPrincipal` from BOTH `application.properties` (main and test) — AML uses `QhorusInboundCurrentPrincipal` as its principal context (aml#59). The main `application.properties` exclusion is required because `quarkus:build` (invoked by `mvn verify`) validates CDI against the main classpath only, not the test properties.
- **casehub-work SNAPSHOT (June 2026) — timer job renames:** `ExpiryCleanupJob` → `ExpiryTimerJob`, `ClaimDeadlineJob` → `ClaimDeadlineTimerJob`. Update `quarkus.arc.exclude-types` in test `application.properties` if these classes are excluded.
- **qhorus SNAPSHOT (2026-06-14) — `QhorusInboundCurrentPrincipal @Default @ApplicationScoped`:** qhorus#269 added `QhorusInboundCurrentPrincipal` with `@Default @ApplicationScoped`, which displaces `MockCurrentPrincipal @DefaultBean` in `@QuarkusTest` contexts. Exclude from test `application.properties` only — NOT from main properties, where it is the active production principal (aml#63).
- **casehub-ledger SNAPSHOT (feat/#128) — `domainContentBytes()` enforcement:** All `@Entity` `LedgerEntry` subclasses with persistent fields must override `domainContentBytes()` — build-time guard in `LedgerProcessor`. Returns pipe-delimited UTF-8 bytes of all non-`@Transient` fields. See `KeyRotationEntry` for the pattern.
- **casehub-ledger SNAPSHOT (feat/#130) — SYSTEM actor tokenisation exemption:** Only `ActorType.HUMAN` actors are pseudonymised. `SYSTEM` and `AGENT` actors are returned unchanged — not natural persons, no GDPR obligation. Erasure for SYSTEM actors returns `mappingFound=false`.
- **Awaitility on default-datasource EntityManager:** Awaitility polling lambdas run on the test thread, which has no JTA transaction context. Wrap `EntityManager` queries in `QuarkusTransaction.requiringNew().call(() -> ...)` to avoid `ContextNotActiveException`. Use `@PersistenceContext` (no unitName) for `WorkItem` entities (default datasource, not qhorus).
- **Layer 6 and 9 gate test completion:** Both `AmlLayer6Resource.getInvestigation()` and `AmlLayer9Resource.getInvestigation()` use `CaseInstanceCache.get(caseId).getState() == CaseStatus.COMPLETED` rather than `WorkerDecisionEntry` — resilient to async `@ObservesAsync` delivery delays and H2 concurrent INSERT races. `WorkerDecisionEntry` presence is not a reliable completion signal in test contexts (aml#63).
- **casehub-ledger SNAPSHOT (feat/#148) — `JpaActorTrustScoreRepository` must be activated explicitly:** `NoOpActorTrustScoreRepository` is `@DefaultBean` — it silently swallows all `upsert()` calls and returns `Optional.empty()` on all reads. Add `io.casehub.ledger.runtime.repository.jpa.JpaActorTrustScoreRepository` to `quarkus.arc.selected-alternatives` in test `application.properties`. Without it, `AmlTrustScoreSeeder` writes to the no-op and trust routing tests fail because scores are never persisted. Same pattern as `JpaLedgerEntryRepository` (aml#64).
- **casehub-ledger SNAPSHOT (feat/#148) — `TrustScoreSource` SPI replaces `TrustScoreCache`:** `TrustScoreCache` (from `io.casehub.ledger.routing`) is removed. Use `TrustScoreSource` (`io.casehub.ledger.api.spi`). `MaterializedTrustScoreSource` is `@DefaultBean` (reads DB fresh per call — no `hydrate()` needed). `CachedTrustScoreSource` is `@Alternative`. Method rename: `getCapabilityScore()` → `capabilityScore()`.
- **casehub-ledger SNAPSHOT (feat/#148) — `LedgerErasureService.erase()` requires `ErasureReason`:** Second parameter added. AML GDPR Art.17 erasure endpoint uses `ErasureReason.GDPR_ART_17_REQUEST`.

### Code review

- After completing any implementation: invoke `superpowers:requesting-code-review` before committing.
- When receiving review feedback: invoke `superpowers:receiving-code-review` — do not blindly implement suggestions; verify them first.

### Platform protocol compliance

Before designing or implementing anything, consult the local parent repo protocols in order:

1. **Platform Coherence Protocol** — `../parent/docs/PLATFORM.md` — capability ownership, boundary rules, consolidation check
2. **CaseHub Protocols** — `../garden/docs/protocols/casehub/HARNESS-INDEX.md` — conventions for building on top of CaseHub; workspace-local protocols at `docs/protocols/casehub/HARNESS-INDEX.md`
3. **Design phase references** — the table in this CLAUDE.md above — concern-specific docs for the current design decision
4. **Conventions index** — `../parent/docs/conventions/INDEX.md` — check if a relevant convention exists before inventing a pattern

The local parent folder is at `/Users/mdproctor/claude/casehub/parent/`. Always `Read` docs from there first; fall back to `WebFetch` only if the file does not exist locally.

### Documentation maintenance

After any code change, systematically check and update:

1. **This CLAUDE.md** — does any section describe something that no longer exists or no longer matches the code?
2. **`casehub-aml.md`** in the parent repo — reflects the current state of domain ownership, epics, dependencies
3. **Cross-references** — any path or URL referenced in docs: verify it resolves, rename if the target moved
4. **Drift and gaps** — code that exists without doc coverage; docs that describe code that was removed or renamed
5. **Redundancy** — the same fact stated in multiple places; consolidate to one authoritative location and reference it from others

Run this check before every handover. If a doc update requires changes in the parent repo, create a GitHub issue on `casehubio/parent` — do not commit to that repo directly.
