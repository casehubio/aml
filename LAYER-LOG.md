# casehub-aml Agentic Harness — Layer Log

**Status:** Migrated to `ARC42STORIES.MD` as of 2026-06-02. LAYER-LOG.md remains as the source-of-truth draft. When a layer is extended or a new layer opens, write the LAYER-LOG entry first, then sync to `ARC42STORIES.MD §9.4`.

Architecture record of what was built at each integration layer. Entries are ordered for
reading comprehension, not chronology. Each entry is complete when the layer closes.

**Migration note:** This file will migrate to `ARC42STORIES.MD §9.4` Layer Entries when
that document is bootstrapped. Format: `../parent/docs/arc42stories-spec.md` and
`../parent/docs/arc42stories-casehub-profile.md`.

**Vertical slices:** The recommended build approach is vertical slice first — the thinnest
working path through all layers — then deepen each layer to production completeness. See
`../parent/docs/AGENTIC-HARNESS-GUIDE.md` §Build Order. Layers 1–3 were built before this
guidance existed; later layers follow vertical slice first.

---

## Vertical Slice Index

A vertical slice is the thinnest working path through all relevant layers that produces a testable result.

| Slice | Layers | Deliverable | Status |
|-------|--------|-------------|--------|
| S1 | 1 + 2 + 3 | Transaction flagged → typed specialist dispatch (COMMAND/DONE/DECLINE) → compliance WorkItem created with 30-day FinCEN SLA | ✅ complete |
| S2 | + 4 | S1 + tamper-evident ledger audit trail; `causedByEntryId` links each finding to the commitment that produced it | ✅ complete |
| S3 | + 5 | S2 + adaptive investigation path: PEP routing, parallel OSINT/pattern, DECLINE as formal scope boundary | ✅ complete |
| S4 | + 6 | S3 + trust-weighted agent selection from SAR outcome attestations; cold-start Beta seeding | ✅ complete |
| S5 | 7 | Compliance evidence — accountability properties mapped against FinCEN/FATF requirements | ✅ complete |

**Ordering rationale:** S1→S2 is soft — ledger can record any entry type without qhorus, but auditing typed COMMAND/DONE/DECLINE events (Layer 3) makes the audit trail meaningful rather than sparse. S2→S4 is hard — trust scoring reads attestation data written by ledger; S4 cannot exist without S2. S3→S4 is hard — trust routing selects among engine-dispatched workers; S4 cannot exist without S3.

---

## Layer 1 — Domain baseline (no CaseHub foundation)

**Participates in:** S1, S2, S3, S4
**Completed:** 2026-05-10
**Issue:** casehubio/aml#12
**Navigation:** `git log --grep="#12" --oneline`
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/` — pure domain records: `SuspiciousTransaction`, `InvestigationSummary`, `AmlInvestigationResult`, `EntityResolutionResult`, `PatternAnalysisResult`, `OsintResult`
- `api/src/main/java/io/casehub/aml/investigation/` — specialist service interfaces: `EntityResolutionService`, `PatternAnalysisService`, `OsintScreeningService`, `SarDraftingService`
- `app/src/main/java/io/casehub/aml/DefaultAmlInvestigationService.java` — `@DefaultBean` baseline implementation
- `app/src/main/java/io/casehub/aml/Default*.java` — default implementations of each specialist service
- `app/src/main/java/io/casehub/aml/AmlInvestigationApplicationService.java` — use-case port interface
- `app/src/main/java/io/casehub/aml/AmlInvestigationResource.java` — REST entry point: `POST /api/investigations`

### What it adds

Hexagonal architecture foundation with AML domain vocabulary and no CaseHub foundation modules. Domain records and service interfaces in `api/`; baseline implementation with `@DefaultBean` in `app/`. A REST API for AML investigations — without SLA enforcement, commitment tracking, or tamper-evident audit.

The accountability gaps are structural: no record of which agent made a recommendation, no SLA on the compliance review, no formal obligation tracking per specialist, no escalation when deadlines pass.

### Accountability gaps

| Gap | What breaks | Closed by |
|-----|-------------|-----------|
| No attribution | Who resolved this entity graph? No record of which agent made this decision or when. | Layer 3 (casehub-qhorus COMMAND/RESPONSE) |
| No failure resilience | If a service call times out, the entire investigation is lost with no trace of partial work. | Layer 3 (formal FAILURE outcome type) |
| No deadline tracking | OSINT runs sequentially. No FinCEN 30-day SLA. No parallel execution. No formal obligation. | Layer 2 (casehub-work claimDeadline) + Layer 5 (engine parallel binding) |
| No audit trail | The SAR narrative cannot be proven to FinCEN. No tamper-evident record of the reasoning chain. | Layer 4 (casehub-ledger Merkle chain) |

### Key wiring

**Hexagonal architecture from day one.** `api/` is a pure Java module — no JPA, no Quarkus, no framework dependencies. Domain records and service interfaces live here. `app/` owns use-case orchestration and all framework wiring. This split is mandatory (platform protocol PP-20260512-9b8847).

**`@DefaultBean` on the baseline service.** `DefaultAmlInvestigationService` carries `@DefaultBean` so that each subsequent layer can add a `@ApplicationScoped` implementation that takes priority via CDI displacement — without touching the baseline code. This is how layers coexist: each new service displaces the previous one at the CDI level.

```java
@ApplicationScoped
@DefaultBean  // displaced by any @ApplicationScoped impl in the same deployment
public class DefaultAmlInvestigationService implements AmlInvestigator {
```

**`AmlInvestigationApplicationService`** is the use-case port interface in `app/` (not `api/`) — it takes domain types from `api/` but lives in `app/` because it references the orchestration concern, not pure domain. This placement follows the hexagonal rule.

### Gotchas

- None for Layer 1 — it has no framework dependencies. Any complexity here is a sign that domain logic leaked into infrastructure.

### Pattern to replicate (in another domain)

1. Create `api/` Maven module — pure Java, zero framework imports, zero JPA
2. Define domain records in `api/src/main/java/{package}/domain/` — immutable, no behaviour
3. Define specialist service interfaces in `api/src/main/java/{package}/investigation/` (or equivalent) — one interface per agent concern
4. Create `app/` Maven module — depends on `api/`; owns all Quarkus/CDI wiring
5. Define the use-case port interface in `app/` — takes domain types, returns domain types
6. Implement the default baseline service with `@ApplicationScoped @DefaultBean` — direct method calls, no CaseHub
7. Add gap comments for every regulatory/compliance requirement not yet met — these are the teaching mechanism
8. Expose `POST /api/{domain-noun}` via a REST resource that injects the port interface
9. Write unit tests for the baseline service (no Quarkus needed — plain `new`)

---

## Layer 2 — + casehub-work (compliance officer WorkItem with 30-day FinCEN SLA)

**Participates in:** S1, S2, S3, S4
**Completed:** 2026-05-13
**Issue:** casehubio/aml#15
**Navigation:** `git log --grep="#15" --oneline`
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/AmlInvestigationResult.java` — extended to carry `complianceReviewTaskId`
- `app/src/main/java/io/casehub/aml/tutorial/WorkItemAmlInvestigationService.java` — Layer 2 implementation
- `app/src/test/java/io/casehub/aml/tutorial/WorkItemAmlInvestigationServiceTest.java` — unit test
- `app/src/test/java/io/casehub/aml/AmlInvestigationResourceTest.java` — `@QuarkusTest`
- `app/src/test/resources/application.properties` — qhorus workarounds (see Gotchas)

### What it adds

casehub-work WorkItem created for the compliance officer review with a 30-day FinCEN `claimDeadline`. The baseline investigation delegates to `DefaultAmlInvestigationService`; the WorkItem is the formal accountability layer above it. `AmlInvestigationResult` gains `complianceReviewTaskId`.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| No compliance SLA | Compliance review can sit indefinitely; officer has no formal deadline | WorkItem with 30-day `claimDeadline`; `candidateGroups=compliance-officers` |
| No escalation path | Missed SLA sits silently — no notification or auto-escalation to head of compliance | casehub-work SLA breach policy (auto-escalation wired in later layers) |

### Key wiring

**`casehub-work-api` in `api/`, `casehub-work` in `app/`.** The api module is JPA-free — `casehub-work-api` contains only the request/response types (no JPA), so it is safe to add to `api/`. The full runtime (`casehub-work`) with JPA entities goes in `app/` only.

**Hibernate scan packages — two packages required.** When adding `casehub-work`, both `io.casehub.work.runtime.model` and `io.casehub.work.runtime.filter` must be declared in the Hibernate scan packages. Omitting `runtime.filter` causes silent failures where filter beans are not found.

```properties
quarkus.hibernate-orm.packages=io.casehub.work.runtime.model,io.casehub.work.runtime.filter
```

**`WorkItemCreateRequest` uses a fluent builder** (casehubio/work#168 shipped). Set only the fields you need: `title`, `category`, `candidateGroups`, `claimDeadline`, `callerRef`. Positional constructor approach is gone — the record field count grew past 24, making null-passing unmaintainable.

**CDI displacement pattern.** `WorkItemAmlInvestigationService` is `@ApplicationScoped` without `@DefaultBean` — it displaces `DefaultAmlInvestigationService` at the CDI level. Both classes exist in the build; the one without `@DefaultBean` wins.

```java
@ApplicationScoped  // no @DefaultBean — displaces DefaultAmlInvestigationService
public class WorkItemAmlInvestigationService implements AmlInvestigationApplicationService {
    @Inject DefaultAmlInvestigationService defaultInvestigation;  // delegate for the investigation itself
    @Inject WorkItemService workItemService;
```

### Gotchas

- **Symptom:** Quarkus test startup fails with a Flyway error about duplicate migration version V2.
  **Cause:** `casehub-work` and `casehub-qhorus` both ship a `V2` Flyway migration. When both are on the test classpath, Flyway refuses to start because two migrations share the same version number.
  **Fix:** Disable Flyway in tests and use drop-and-create instead. Do not restore `migrate-at-start=true` until casehubio/qhorus#142 and casehubio/work#162 are resolved. (GE-20260513-74dc72)

  ```properties
  # app/src/test/resources/application.properties
  quarkus.flyway.migrate-at-start=false
  quarkus.flyway.qhorus.migrate-at-start=false
  quarkus.hibernate-orm.database.generation=drop-and-create
  quarkus.hibernate-orm.qhorus.database.generation=drop-and-create
  ```

- **Symptom:** Quarkus test startup fails with a reactive datasource or H2 connection error even though the app uses only JDBC.
  **Cause:** `casehub-qhorus` unconditionally pulls in `quarkus-hibernate-reactive-panache`, which expects a reactive datasource. A JDBC-only consumer has none, causing startup failure.
  **Fix:** Suppress reactive activation in test properties. Do not remove until casehubio/qhorus#141 is resolved. (GE-20260513-4f26a7)

  ```properties
  casehub.qhorus.reactive.enabled=false
  quarkus.datasource.reactive=false
  quarkus.datasource.qhorus.reactive=false
  ```

- **Symptom:** CDI ambiguity error or wrong service injected when both `DefaultAmlInvestigationService` and `WorkItemAmlInvestigationService` are present.
  **Cause:** Both implement `AmlInvestigationApplicationService`. Without `@DefaultBean` on the default service, CDI sees two equal candidates and fails.
  **Fix:** `@DefaultBean` on the default baseline service makes it the fallback; any `@ApplicationScoped` without `@DefaultBean` takes priority. This is how layer coexistence works — intentional and reliable, but only if the default service carries `@DefaultBean`.

### Pattern to replicate (in another domain)

1. Add `casehub-work-api` to `api/pom.xml` — JPA-free, safe in the pure domain module
2. Add `casehub-work` to `app/pom.xml` — brings JPA entities and `WorkItemService`
3. Configure Hibernate scan packages in `application.properties`:
   ```properties
   quarkus.hibernate-orm.packages=io.casehub.work.runtime.model,io.casehub.work.runtime.filter
   ```
4. Add Flyway/reactive workarounds to test `application.properties` if qhorus is on the classpath (copy from AML — tracked as upstream bugs)
5. Extend your result type to carry a `taskId` field
6. Implement a new `@ApplicationScoped` service (no `@DefaultBean`) that:
   - Delegates the domain work to the default baseline service
   - Calls `WorkItemService.create()` with your domain's SLA as `claimDeadline`
   - Sets `candidateGroups` to your domain's human reviewer group
   - Sets `callerRef` to a URI identifying the domain entity (e.g. `aml:investigation/{id}`)
   - Returns the result with the new `taskId`
7. Write a unit test: verify `WorkItemCreateRequest` fields without Quarkus
8. Write a `@QuarkusTest`: `POST` the domain endpoint, assert the task ID is present in the response and the WorkItem exists in the DB with correct `claimDeadline` and `candidateGroups`
9. Run: `mvn verify -pl api,app -am -Dsurefire.failIfNoSpecifiedTests=false`

---

## Layer 3 — + casehub-qhorus (typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent)

**Participates in:** S1, S2, S3, S4
**Completed:** 2026-05-17
**Issue:** casehubio/aml#19
**Navigation:** `git log --grep="#19" --oneline`
**Epic:** casehubio/aml#9 (Tutorial layers 1–7), epic branch `epic-layer3-qhorus`
**Blog:** 2026-05-16-mdp01-broken-promise-layer-2.md — architectural investigation leading to the composer pattern
**Spec:** workspace `specs/2026-05-17-layer3-composer-qhorus-design.md`
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/SpecialistOutcome.java` — new sealed interface
- `api/src/main/java/io/casehub/aml/domain/InvestigationSummary.java` — three fields now `SpecialistOutcome<T>`
- `api/src/main/java/io/casehub/aml/investigation/SarDraftingService.java` — all three params now `SpecialistOutcome<T>`
- `app/src/main/java/io/casehub/aml/AmlInvestigator.java` — new inner interface (investigation concern)
- `app/src/main/java/io/casehub/aml/ComplianceReviewLifecycle.java` — WorkItem concern extracted from Layer 2
- `app/src/main/java/io/casehub/aml/AmlInvestigationCoordinator.java` — stable outer coordinator
- `app/src/main/java/io/casehub/aml/AmlJacksonConfig.java` — Jackson mixin for sealed interface type discriminator
- `app/src/main/java/io/casehub/aml/agents/` — AgentBehaviour, AgentDispatchMechanism SPIs + three stub behaviours
- `app/src/main/java/io/casehub/aml/tutorial/QhorusAmlInvestigator.java` — Layer 3 investigator
- `app/src/main/java/io/casehub/aml/DefaultAmlInvestigationService.java` — now implements AmlInvestigator (not outer port); moved from tutorial/ package
- DELETED: `WorkItemAmlInvestigationService.java` — replaced by coordinator + ComplianceReviewLifecycle

### What it adds

casehub-qhorus typed messaging per specialist agent: each dispatch sends a COMMAND, receives DONE or DECLINE. `DECLINE` is a formal scope boundary — `OsintScreeningBehaviour` declines for PEP database access (outside clearance); the investigation completes regardless.

Corrects the Layer 2 design: concrete type injection replaced with the **composer pattern** — `AmlInvestigationCoordinator` composes `AmlInvestigator` (CDI-swappable) and `ComplianceReviewLifecycle` (stable WorkItem concern). `QhorusAmlInvestigator` (no `@DefaultBean`) displaces `DefaultAmlInvestigationService` at the inner investigator interface.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| No attribution | No record of which agent made a specialist decision or when | COMMAND per specialist; DONE/DECLINE persisted in qhorus `MessageLedgerEntry` |
| No failure resilience | Service timeout loses all partial investigation work with no trace | Each agent interaction is a formal Commitment; FAILURE is an explicit outcome type |

### Key wiring

**`SpecialistOutcome<T>` in `api/` — pure Java, no Jackson in domain module.**
The sealed interface lives in `api/` (zero external dependencies). Jackson type
discriminator is added via a mixin in `app/src/main/java/io/casehub/aml/AmlJacksonConfig.java`:

```java
@Singleton
public class AmlJacksonConfig implements ObjectMapperCustomizer {
    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SpecialistOutcome.Completed.class, name = "Completed"),
        @JsonSubTypes.Type(value = SpecialistOutcome.Declined.class,  name = "Declined"),
        @JsonSubTypes.Type(value = SpecialistOutcome.Failed.class,    name = "Failed")
    })
    interface SpecialistOutcomeMixin {}
    @Override public void customize(ObjectMapper mapper) {
        mapper.addMixIn(SpecialistOutcome.class, SpecialistOutcomeMixin.class);
    }
}
```

Without this, `SpecialistOutcome` fields in JSON responses have no `"type"` discriminator and
REST assertions on `summary.osintScreening.type` return null.

**CDI displacement — concrete type injection was the Layer 2 bug.**
`WorkItemAmlInvestigationService` injected `DefaultAmlInvestigationService` by concrete type.
CDI `@DefaultBean` displacement works at the interface level — injecting by concrete type
prevents any other bean from substituting. Layer 3 fixes this by introducing `AmlInvestigator`
as the injection type. `QhorusAmlInvestigator` (no `@DefaultBean`) displaces
`DefaultAmlInvestigationService` (`@DefaultBean`) automatically.

**Direct dispatch — not ChannelGateway.fanOut().**
`QhorusAmlInvestigator` calls `AgentBehaviour.handle()` directly after sending the COMMAND
message. `channelGateway.fanOut()` does NOT trigger `PushAgentDispatch.post()` as designed —
root cause unknown (#22). The COMMAND and DONE/DECLINE messages ARE persisted to qhorus
via `MessageService.send()`. The formal commitment lifecycle exists in the DB even without
fan-out triggering.

**`casehub.qhorus.reactive.enabled=false` removed — upstream bug resolved.**
The property no longer maps to any config root in the current qhorus version.
Removing it fixes the SmallRye config validation failure at test startup.
Tracked as casehubio/qhorus#141; marked as resolved.

**LedgerVerificationService excluded in tests.**
`LedgerVerificationService` (casehub-ledger) now injects `ReactiveLedgerEntryRepository`
which is vetoed in JDBC-only test mode. Three services excluded from CDI context via
`quarkus.arc.exclude-types` in test `application.properties`. None are exercised by AML tests.

**`@Typed` on `PushAgentDispatch` prevents CDI ambiguity.**
`PushAgentDispatch` implements `AgentChannelBackend`. Without `@Typed`, CDI sees it as a
candidate for `AgentChannelBackend` injection — conflicting with `QhorusChannelBackend`
(the default backend already registered with `ChannelGateway`). Fix:
`@Typed({AgentDispatchMechanism.class, PushAgentDispatch.class})`.

**`WorkItemCreateRequest` field count grew past 24 since Layer 2.** `ComplianceReviewLifecycle` now uses the fluent builder (casehubio/work#168 shipped) — sets only the fields AML needs and is immune to future field additions. No null-passing required.

**`IllegalStateException` maps to HTTP 409 via casehub-work's exception mapper.**
`IllegalStateExceptionMapper` in casehub-work maps `IllegalStateException` → 409 Conflict.
During implementation, the original poll-timeout exception was typed as `IllegalStateException`
and produced confusing 409 responses. Changed to `RuntimeException`.

### Gotchas

- **Symptom:** Jackson serializes `SpecialistOutcome<T>` fields without a `"type"` discriminator — REST assertions on `summary.osintScreening.type` return null.
  **Cause:** Sealed interfaces don't carry `@JsonTypeInfo` — Jackson doesn't know to add a type field.
  **Fix:** Register a mixin via `ObjectMapperCustomizer` in app/ — keeps api/ pure Java.

- **Symptom:** Tests return HTTP 409 (Conflict) with a 5-second delay instead of 500.
  **Cause:** `casehub-work` ships `IllegalStateExceptionMapper` that maps `IllegalStateException` → 409. A poll-timeout throwing `IllegalStateException` triggered it.
  **Fix:** Use `RuntimeException` for infrastructure failures that shouldn't map to HTTP 409.

- **Symptom:** `@QuarkusTest` startup fails with "SmallRye config validation: casehub.qhorus.reactive.enabled does not map to any root".
  **Cause:** The upstream qhorus bug (unconditional hibernate-reactive activation) was fixed; the config property no longer exists in the current version.
  **Fix:** Remove `casehub.qhorus.reactive.enabled=false` from test `application.properties`.

- **Symptom:** `AmbiguousResolutionException: Ambiguous dependencies for AgentChannelBackend` at CDI startup.
  **Cause:** `PushAgentDispatch` implements `AgentChannelBackend`, making it a CDI candidate alongside `QhorusChannelBackend` (the default). CDI can't choose.
  **Fix:** `@Typed({AgentDispatchMechanism.class, PushAgentDispatch.class})` — restricts CDI visibility to `AgentDispatchMechanism` only.

- **Symptom:** `channelGateway.fanOut()` is called but `PushAgentDispatch.post()` is never invoked.
  **Cause:** Unknown — tracked as casehubio/aml#22. Likely requires specific channel initialization sequence via qhorus internals before `registerBackend()` works.
  **Fix (for tutorial):** Direct dispatch — `QhorusAmlInvestigator` calls `AgentBehaviour.handle()` in-process. COMMAND/DONE/DECLINE messages are still persisted via `MessageService`.

### Pattern to replicate (in another domain)

1. Add `casehub-qhorus` to `app/pom.xml`; qhorus named datasource is already configured
2. Define `SpecialistOutcome<T>` in `api/` — sealed interface with Completed/Declined/Failed records
3. Update domain summary record to use `SpecialistOutcome<T>` for all specialist result fields
4. Update `SarDraftingService`/equivalent to accept `SpecialistOutcome<T>` — pattern-match all three variants
5. Introduce an inner investigator interface in `app/` (e.g. `AmlInvestigator`) — separates investigation from compliance lifecycle
6. Implement the orchestrator investigator (`QhorusXxxInvestigator`, no `@DefaultBean`):
   - Inject `Instance<AgentBehaviour>` to find agents by capability
   - For each specialist: `messageService.send(channelId, ORCHESTRATOR, COMMAND, ...)`, call `behaviour.handle()`, `messageService.send(channelId, capability, DONE/DECLINE, ...)`
   - Return `SpecialistOutcome` for each specialist
7. Implement stub `AgentBehaviour` beans (`@ApplicationScoped @DefaultBean`):
   - Entity/pattern stubs return `Completed` with default service results
   - Specialised agents (OSINT, PI authorisation) DECLINE with scope reason
8. Register `ObjectMapperCustomizer` in `app/` to add `@JsonTypeInfo` + `@JsonSubTypes` mixin for the sealed interface
9. Exclude `LedgerVerificationService`, `LedgerComplianceReportService`, `LedgerRetentionJob` from test CDI context (add to `quarkus.arc.exclude-types` in test `application.properties`)
10. Add `@Typed({AgentDispatchMechanism.class, YourPushDispatch.class})` to any `AgentChannelBackend` implementation to prevent CDI ambiguity with `QhorusChannelBackend`
11. Test: assert `summary.osintScreening.type` equals `"Declined"` in `@QuarkusTest`; assert `"type": "Completed"` for other specialists

---

## Layer 4 — + casehub-ledger (FinCEN audit trail with AML domain entries)

**Participates in:** S2, S3, S4
**Issue:** casehubio/aml#30
**Navigation:** `git log --grep="#30" --oneline`  
**Spec:** `docs/specs/2026-05-22-message-dispatch-builder-design.md` (qhorus side)  
**Completed:** 2026-05-23

### What changed

**New entities and services:**
- `app/src/main/java/io/casehub/aml/ledger/AmlInvestigationLedgerEntry.java` — JPA entity, JOINED inheritance from `LedgerEntry`, `@DiscriminatorValue("AML_INVESTIGATION")`. Fields: `transactionId` (context reference), `eventType` (CASE_OPENED | COMPLIANCE_REVIEW_OPENED)
- `app/src/main/java/io/casehub/aml/ledger/AmlLedgerService.java` — writes CASE_OPENED and COMPLIANCE_REVIEW_OPENED entries. Populates all 8 required base fields. Both entries carry `subjectId = caseId`.
- `app/src/main/resources/db/aml-ledger/migration/V2001__aml_investigation_ledger_entry.sql` — Flyway V2001, `aml_investigation_ledger_entry` join table.

**Interface change:**
- `AmlInvestigator.investigate(SuspiciousTransaction, UUID caseId)` — `caseId` added as second param. All implementations updated. `DefaultAmlInvestigationService` receives and ignores it (no foundation module to use it in Layer 1).

**Result change:**
- `AmlInvestigationResult` gains two new fields: `caseId` (UUID of the investigation case) and `ledgerCaseEntryId` (UUID of the CASE_OPENED entry). Backward-compat 2-arg constructor retained for Layer 1/2.

**qhorus migration:**
- `QhorusAmlInvestigator` migrated from `messageService.send()` to `messageService.dispatch(MessageDispatch.builder()...)` per qhorus#184. Each dispatch carries `subjectId=caseId` (links qhorus MessageLedgerEntry to the AML domain chain) and `inReplyTo=commandResult.messageId()` (correct protocol, was missing before).
- `PushAgentDispatch` migrated to `dispatch()`. Resolves `inReplyTo` via correlationId lookup. Cannot propagate `subjectId` until qhorus#190 (OutboundMessage missing field).

**configuration:**
- Both `application.properties` files: `io.casehub.aml.ledger` added to `quarkus.hibernate-orm.qhorus.packages`; `classpath:db/aml-ledger/migration` added to `quarkus.flyway.qhorus.locations`
- `casehub-platform` added as test dependency for `MockPreferenceProvider` CDI bean (required since work#218)

### Gotchas

- **Symptom:** `@TestTransaction` in `AmlLedgerChainTest` causes "Unable to acquire JDBC Connection" errors. **Cause:** The outer test transaction prevents a second connection being acquired from the pool (H2 in-memory has limited pool). `LedgerWriteService.record()` uses `@Transactional(REQUIRED)` — the issue is that `@TestTransaction` wraps everything and subsequent ledger queries don't see the entries (rolled back). **Fix:** Remove `@TestTransaction`. Use unique transaction IDs per test for isolation.

- **Symptom:** `UnsatisfiedResolutionException: Unsatisfied dependency for type PreferenceProvider`. **Cause:** Updated qhorus/work snapshot requires `casehub-platform` CDI beans (specifically `MockPreferenceProvider`) to be indexed. **Fix:** Add `casehub-platform` as test dependency + `quarkus.index-dependency.casehub-platform.*` to test `application.properties`.

- **Symptom:** qhorus tests pass but `MESSAGE_LEDGER_ENTRY` table not found in AML tests. **Cause:** qhorus tests use `hibernate-orm.database.generation=drop-and-create` (bypasses Flyway); AML tests use `generation=none` (Flyway only). The V2000 migration in the installed qhorus jar was stale. **Fix:** `mvn install` qhorus from source to pick up the correct migration.

### Pattern to replicate (in another domain)

1. Generate a `UUID caseId` in the coordinator at investigation start
2. Create a `XxxLedgerEntry extends LedgerEntry` entity in `app/src/main/java/.../ledger/` — set `@DiscriminatorValue`, `@Table(name = "xxx_ledger_entry")`
3. Create a `XxxLedgerService` with `write*()` methods — populate all 8 base fields (id, subjectId, sequenceNumber, entryType=EVENT, actorId, actorType, actorRole, occurredAt) + your domain fields
4. Add Flyway migration `V2001__xxx_ledger_entry.sql` in `db/xxx-ledger/migration/` (V2001 = first consumer join; V2000 = qhorus join)
5. Update `application.properties` (both main and test): add the package to `quarkus.hibernate-orm.qhorus.packages`; add the migration path to `quarkus.flyway.qhorus.locations`
6. Pass `caseId` through all `messageService.dispatch()` calls as `subjectId`
7. Tests: no `@TestTransaction` when the tested code uses `@Transactional(REQUIRED)` for the ledger write

---

## Layer 5 — + casehub-engine (adaptive investigation paths)

**Participates in:** S3, S4
**Issue:** casehubio/aml#31
**Navigation:** `git log --grep="#31" --oneline`
**Spec:** `docs/specs/2026-05-24-layer5-engine-design.md`
**Completed:** 2026-05-25

### What changed

**New package `io.casehub.aml.engine`:**
- `AmlInvestigationCaseHub extends YamlCaseHub` — thin CDI wrapper; creates `AmlInvestigationCaseDescriptor` in `@PostConstruct`; augments the YAML-loaded definition with 7 worker functions; overrides `getDefinition()` to return the augmented definition.
- `AmlInvestigationCaseDescriptor` — plain POJO (no CDI); carries all 7 worker lambdas that capture CDI proxies (`ComplianceReviewLifecycle`, `ObjectMapper`) and delegate to existing stub behaviours; testable without Quarkus (per protocol PP-20260518).
- `AmlEngineCoordinator` — starts the engine case, writes `CASE_OPENED` ledger entry using the engine-returned case UUID (both identifiers are now the same), returns the UUID.
- `AmlLayer5Resource` — `POST /api/layer5/investigations` returning `Layer5InvestigationResponse { UUID caseId, String status }`.
- `Layer5InvestigationResponse` — response record.

**YAML case definition `app/src/main/resources/aml/aml-investigation.yaml`:**
5 capabilities, 5 `contextChange` bindings:
- `entity-resolution` — fires first (no prior context required)
- `pattern-analysis` — fires after entity (parallel with OSINT)
- `osint-screening` — fires after entity (parallel with pattern)
- `senior-analyst-required` — fires only when `entityType == "PEP"` or `riskScore > 0.8`
- `sar-drafting` — fires when entity + pattern + osint are all non-null; calls `ComplianceReviewLifecycle.openReview()` internally

1 goal: `investigation-complete` when `.complianceTaskId != null`.

**Domain model change:**
- `EntityResolutionResult` gains `entityType` (String) and `riskScore` (double). All callers updated in the same PR. The entity-resolution worker uses `flagReason.contains("PEP")` heuristic to set these fields for tutorial purposes.

**Dependencies added to `app/pom.xml`:**
- `casehub-engine`, `casehub-engine-scheduler-quartz`, `casehub-platform-expression`, `casehub-engine-persistence-memory` (compile), `casehub-engine-testing` (test), `awaitility` (test)
- `casehub-platform` scope changed from `test` to `runtime` — required for production augmentation (`MockPreferenceProvider @DefaultBean` must be visible to the `quarkus:build` goal)
- Versions explicit until parent#65 adds these to BOM dependencyManagement

### What it adds

casehub-engine binding evaluation replaces the fixed sequential pipeline. The engine evaluates ALL matching binding conditions simultaneously on every context update:
- **PEP routing** — `senior-analyst-required` fires automatically when entity-resolution output contains `entityType == "PEP"`. No conditional code in the coordinator.
- **Parallel execution** — `pattern-analysis` and `osint-screening` share identical preconditions; the engine fires both simultaneously on the same context change.
- **DECLINE as a first-class outcome** — OSINT decline writes `{declined: true, ...}` to context (satisfies the `osintScreening != null` condition); sar-drafting proceeds without modification.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| Sequential specialist pipeline | OSINT blocked behind pattern analysis; investigation time doubles on complex cases | Engine fires `pattern-analysis` and `osint-screening` in parallel on identical binding conditions |
| Hardcoded routing | PEP cases routed to standard analysts — no differentiation based on entity type | `senior-analyst-required` binding fires when `entityType == "PEP"` — no coordinator code needed |

### Key wiring

**Worker functions are in `AmlInvestigationCaseDescriptor`.** The descriptor is a plain POJO testable without Quarkus; `AmlInvestigationCaseHub` creates it in `@PostConstruct` and calls `super.getDefinition().getWorkers().addAll(descriptor.workers())`. The YAML `workers:` section is empty — YAML supplies bindings and capabilities; Java supplies the worker functions. `cap()` helper creates `Capability` objects with passthrough schemas (`.`) because name-matching is all that's needed for binding dispatch; the actual schemas are on the YAML capabilities.

**Engine case UUID is the shared stable identifier.** `AmlEngineCoordinator` starts the case first, receives the engine-generated UUID, then writes the `CASE_OPENED` ledger entry using that UUID. This ensures the engine event log, AML ledger entries, and compliance officer WorkItem all share one identifier.

**GE-20260523-4ca5e7 — Quartz/casehub-work cron format clash.** `casehub-engine-scheduler-quartz` pulls in `quarkus-quartz`. `casehub-work` ships `@Scheduled` beans with 5-field Unix cron; Quartz requires 6-field. Fix in test `application.properties`: `quarkus.scheduler.start-mode=forced`, `quarkus.quartz.store-type=ram`, and `quarkus.arc.exclude-types` for the four casehub-work scheduler beans.

**GE-20260523-86ed13 — engine requires casehub-platform and casehub-platform-expression.** Without `casehub-platform-expression` on the classpath, `JQEvaluator` CDI injection fails and engine beans don't start. Added as compile dep.

**GE-20260428-9311f8 — JpaWorkloadProvider conflicts with engine's internal WorkloadProvider.** In tests: exclude `JpaWorkloadProvider`. In production: exclude `CasehubWorkloadProvider`. Both in `quarkus.arc.exclude-types`.

**Event log metadata key for assertion.** `WORKER_SCHEDULED` events carry `workerName` in metadata. `WORKER_EXECUTION_COMPLETED` events carry `inputDataHash` and `contextChanges` — NOT `workerName`. Integration tests poll `WORKER_SCHEDULED` events, not `WORKER_EXECUTION_COMPLETED`, to detect which workers fired.

### Gotchas

- **Symptom:** `(RECIPIENT_FAILURE,8191) CaseDefinition not found for case: <uuid>` — `SchedulerService.registerScheduledTriggers()` calls `getCaseDefinition()` which returns null.
  **Cause:** `engine-testing` jar not indexed — `TestCaseMetaModelRepository @Priority(1)` not discovered. Without it, `InMemoryCaseMetaModelRepository @Alternative @ApplicationScoped` (no priority) is the only option. In the test run before retry, registration completes, but between retry attempts Quarkus restarts and a timing issue causes the failure on retry.
  **Fix:** Add `quarkus.index-dependency.engine-testing.*` to test `application.properties`. This activates `TestCaseMetaModelRepository @Priority(1)`, `TestCaseInstanceRepository @Priority(1)`, and `TestEventLogRepository @Priority(1)`.

- **Symptom:** Awaitility condition never fires even though workers complete in < 1 second (visible in debug logs).
  **Cause:** `WORKER_EXECUTION_COMPLETED` events don't have `workerName` in metadata — only `inputDataHash` and `contextChanges`. Query filter for `workerName` always returns empty set.
  **Fix:** Poll `WORKER_SCHEDULED` events instead. These always carry `workerName`. A worker being scheduled proves its binding condition was satisfied (all prerequisite workers completed and wrote to context).

- **Symptom:** `casehub-engine` artifacts missing from parent BOM `dependencyManagement`, causing Maven error `'dependencies.dependency.version' is missing`.
  **Fix:** Use explicit `${casehub.version}` in `app/pom.xml` until parent#65 is resolved.

### Pattern to replicate (in another domain)

1. Create `resources/domain/xxx-case.yaml` — define capabilities, bindings (all `contextChange` + JQ `when:` conditions), goals, completion
2. Create `XxxCaseDescriptor` (plain POJO, no CDI): constructor takes CDI dependencies as parameters; `workers()` returns `List<Worker>`; each worker: `Worker.builder().name(...).capabilities(List.of(cap(name))).function(input -> {...}).build()`
   Create `XxxCaseHub extends YamlCaseHub` in a new `engine/` package: `@ApplicationScoped`; inject CDI deps; `@PostConstruct init()` creates the descriptor and calls `super.getDefinition().getWorkers().addAll(descriptor.workers())`; override `getDefinition()` to return the field set in `init()`
3. Create `XxxEngineCoordinator`:
   - Call `caseHub.startCase(initialContext).toCompletableFuture().get(5, SECONDS)` first
   - Write ledger `CASE_OPENED` entry with the engine-returned UUID
   - Return the UUID
4. Add `engine-testing` index-dependency in test `application.properties`
5. Add GE-20260523-4ca5e7 fix (Quartz/cron exclusions) to test `application.properties`
6. Change `casehub-platform` from `test` to `runtime` scope in `app/pom.xml`
7. Integration tests: poll `WORKER_SCHEDULED` events (not `WORKER_EXECUTION_COMPLETED`) — `WORKER_SCHEDULED` metadata carries `workerName`
8. Assert adaptive paths: for each decision point, start a case with context that triggers the branch, Awaitility-poll for the expected worker to be scheduled

---

## Layer 6 — + trust-weighted routing (experienced agents on complex cases)

**Participates in:** S4
**Issue:** casehubio/aml#38
**Navigation:** `git log --grep="#38" --oneline`
**Blog:** `blog/2026-05-29-mdp01-trust-loop-complete.md`
**Spec:** `docs/specs/2026-05-29-layer6-trust-routing-design.md`
**Completed:** 2026-05-29

### What changed

**New domain types in `api/`:**
- `api/src/main/java/io/casehub/aml/domain/SarVerdict.java` — enum: `UPHELD`, `WITHDRAWN`, `FLAGGED`
- `api/src/main/java/io/casehub/aml/domain/SarOutcome.java` — record: `verdict`, `reason`, `investigationAccuracyScore`; compact constructor validates score in `[0.0, 1.0]` and asserts non-null fields

**New package `io.casehub.aml.routing`:**
- `AmlTrustRoutingPolicyProvider` — implements `TrustRoutingPolicyProvider` SPI; `TrustRoutingPolicy(threshold, minObs, borderlineMargin, blendFactor, qualityFloors)` per capability: `entity-resolution` (0.70, 10, 0.10, 0.60), `pattern-analysis` (0.65, 10, 0.10, 0.60), `osint-screening` (0.70, 10, 0.10, 0.65), `sar-drafting` (0.75, 10, 0.10, 0.70, `investigation-accuracy` quality floor 0.65), `senior-analyst-review` (0.80, 10, 0.10, 0.70); resolves overrides from `PreferenceProvider` first, falls back to hardcoded map

**New package `io.casehub.aml.trust`:**
- `AmlTrustScoreSeeder` — seeds 7 workers with Beta(α,β) at `@Observes @Priority(20) StartupEvent`; idempotency-guarded (skips if capability score already exists); seeds: `sar-drafting-agent-senior` Beta(9,1), `sar-drafting-agent-junior` Beta(2,8), `osint-screening-agent-senior` Beta(9,2), `osint-screening-agent` Beta(3,7), `entity-resolution-agent` Beta(8,2), `pattern-analysis-agent` Beta(8,2), `senior-analyst-agent` Beta(8,2); calls `trustScoreCache.hydrate()` after seeding
- `SarOutcomeFeedbackService` — writes `LedgerAttestation` on the `sar-drafting` `WorkerDecisionEntry` for the case; `UPHELD → AttestationVerdict.SOUND`, `WITHDRAWN/FLAGGED → FLAGGED`; silently skips if no matching entry found
- `AmlWorkerDecisionRepository` — JPQL queries on `WorkerDecisionEntry` via the `qhorus` persistence unit: find latest by caseId + capability (by `sequenceNumber DESC`), find all by caseId

**In `io.casehub.aml.engine`:**
- `AmlEngineCoordinator` — unchanged from Layer 5; reused by `AmlLayer6Resource` to start investigations
- `AmlLayer6Resource` — `POST /api/layer6/investigations` (202 with caseId), `GET /api/layer6/investigations/{caseId}` (routing decisions + current trust scores), `POST /api/layer6/investigations/{caseId}/outcome` (204)
- `Layer6InvestigationResponse` — `record(UUID caseId, String status, List<WorkerRoutingDecision> routingDecisions)`
- `WorkerRoutingDecision` — `record(String capabilityTag, String selectedWorker, Double trustScore)` with `@JsonInclude(NON_NULL)` — trust score is null in Phase 0 (no history yet)

**Flyway — local re-numbered engine-ledger migrations:**
- `app/src/main/resources/db/aml-engine-ledger/migration/V2002__case_ledger_entry.sql` — local copy of engine-ledger V2000 (`case_ledger_entry` join table), re-numbered to avoid collision with qhorus V2000 (Layer 7: renamed from `db/engine-ledger/` to `db/aml-engine-ledger/` to avoid Flyway classpath collision with the upstream jar)
- `app/src/main/resources/db/aml-engine-ledger/migration/V2003__worker_decision_entry.sql` — local copy of engine-ledger V2001 (`worker_decision_entry` join table), re-numbered; both paths added to `quarkus.flyway.qhorus.locations`

**Dependencies (`app/pom.xml`):**
- `casehub-engine-ledger` — provides `WorkerDecisionEntry`, `CaseLedgerEntry`, `ActorTrustScoreRepository`, `TrustScoreCache`, `LedgerAttestation`

### What it adds

Trust-weighted agent selection: `AmlTrustRoutingPolicyProvider` supplies per-capability routing thresholds to the engine's `TrustWeightedAgentStrategy`. Workers below threshold are excluded; above-threshold workers compete by score. The senior SAR drafter (Beta(9,1) = 90% mean) immediately outscores the junior (Beta(2,8) = 20% mean, below threshold).

SAR outcomes close the feedback loop: `POST /{caseId}/outcome` writes a `LedgerAttestation` against the `sar-drafting` `WorkerDecisionEntry`. The next `TrustScoreJob` cycle recomputes the agent's `investigation-accuracy` score from all attestations. `GET /{caseId}` exposes which worker was selected per capability and its current trust score from `TrustScoreCache`.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| Blind worker selection | Complex PEP cases and high-stakes SAR drafting routed to any available agent regardless of track record | `TrustWeightedAgentStrategy` reads `AmlTrustRoutingPolicyProvider` thresholds; workers below threshold excluded |
| No outcome feedback | Agent quality is unobservable; poor agents accumulate work | SAR verdict writes `LedgerAttestation`; `TrustScoreJob` recomputes `investigation-accuracy` from all attestations |

### Key wiring

**`AmlTrustRoutingPolicyProvider` implements the engine SPI directly.** The SPI is `TrustRoutingPolicyProvider` in `casehub-engine-api`. The provider falls back to hardcoded thresholds when `PreferenceProvider` returns null (always the case in tutorial mode — `MockPreferenceProvider @DefaultBean` returns null for every key).

```java
@ApplicationScoped
public class AmlTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {
    private static final Map<String, TrustRoutingPolicy> POLICIES = Map.of(
            "sar-drafting",      new TrustRoutingPolicy(0.75, 10, 0.10, 0.70,
                                     Map.of("investigation-accuracy", 0.65)),
            "osint-screening",   new TrustRoutingPolicy(0.70, 10, 0.10, 0.65, Map.of()),
            ...
    );

    @Override
    public TrustRoutingPolicy forCapability(final String capabilityName) {
        // resolve from PreferenceProvider first; fall back to hardcoded policies
        return POLICIES.getOrDefault(capabilityName, TrustRoutingPolicy.DEFAULT);
    }
}
```

**`@Observes @Priority(20) StartupEvent` — not `@Startup @PostConstruct`.** The seeder must fire after the engine's `DefaultCaseDefinitionRegistry` (priority=10) to avoid case definition lookup failures when the first investigation starts immediately after startup. `@Startup @PostConstruct` has no ordering guarantee against CDI startup beans; `@Priority(20)` on `StartupEvent` is explicit.

**Direct `upsert()` for seeding — not `TrustBootstrapSource`.** The `TrustBootstrapSource` SPI appears designed for seeding but is never called on a fresh deployment (see Gotchas). Direct `ActorTrustScoreRepository.upsert()` is the correct API for known initial values.

**`trustScoreCache.hydrate()` after seeding.** Without explicit hydration, `TrustScoreCache @Startup` may initialize before the seeder writes rows, leaving the cache empty. Calling `hydrate()` after the `upsert()` loop guarantees a consistent post-seed state regardless of CDI startup ordering.

**`LedgerAttestation` wired to `sar-drafting` `WorkerDecisionEntry`.** `SarOutcomeFeedbackService` finds the entry by caseId + capability, then persists a `LedgerAttestation` using the qhorus `EntityManager` (`@PersistenceContext(unitName = "qhorus")`). The `trustDimension = "investigation-accuracy"` field links the attestation to the correct dimension for `TrustScoreJob` recomputation.

**Flyway V2002/V2003 — local re-numbered copies of engine-ledger migrations.** Engine-ledger ships V2000/V2001 at `classpath:db/migration/` — the generic path Flyway scans for qhorus migrations. Qhorus defines its own V2000. Adding `casehub-engine-ledger` without a separate migration path causes `FlywayException: Found more than one migration with version 2000`. Local copies at V2002/V2003 under `classpath:db/aml-engine-ledger/migration/` resolve this (renamed from `db/engine-ledger/` in Layer 7 to avoid a second Flyway classpath collision with the upstream jar). SQL files carry a comment explaining the re-numbering. Tracked as engine#395.

### Gotchas

- **Symptom:** `TrustBootstrapSource` implementation is registered but no trust scores are ever seeded — application starts with an empty cache on every deployment.
  **Cause:** `TrustScoreJob.runComputation()` calls `bootstrapService.bootstrapIfNew(byActor.keySet())` where `byActor` groups existing `LedgerEntry` records by `actorId`. On a fresh deployment with zero ledger entries, `byActor` is empty and the bootstrap SPI is never called. `TrustBootstrapSource` is designed for cross-deployment federation (importing prior actor history from another deployment), not for seeding known initial values.
  **Fix:** Use `ActorTrustScoreRepository.upsert()` directly in a startup observer; follow with `trustScoreCache.hydrate()`. (GE-20260529-d7b6f8)

- **Symptom:** `FlywayException: Found more than one migration with version 2000` at startup after adding `casehub-engine-ledger`.
  **Cause:** Engine-ledger ships V2000 and V2001 at `classpath:db/migration/` — the same path Flyway scans for qhorus migrations. Qhorus also defines V2000. Two V2000s in one Flyway run is a hard failure.
  **Fix:** Create local re-numbered copies at `classpath:db/engine-ledger/migration/V2002__case_ledger_entry.sql` and `V2003__worker_decision_entry.sql`. Add the path to `quarkus.flyway.qhorus.locations` in both `application.properties` files. Tracked as engine#395.

- **Symptom:** Layer 5 tests regress after adding `casehub-engine-ledger`: `CaseDefinition not found for case: {UUID}` thrown inside the engine's `SchedulerService`.
  **Cause:** `CaseLedgerEntryRepository @ApplicationScoped extends JpaLedgerEntryRepository @Alternative`. In CDI, a non-alternative `@ApplicationScoped` bean beats a `selected-alternatives` config entry, corrupting `DefaultCaseDefinitionRegistry`'s case definition lookup map.
  **Fix:** Add `CaseLedgerEntryRepository` to `quarkus.arc.exclude-types` in test `application.properties`. Tracked as engine#396 — the CDI configuration error is in engine-ledger and the fix belongs there.

- **Symptom:** First investigation request immediately after startup fails with `CaseDefinition not found` even though all CDI beans started cleanly.
  **Cause:** `@Startup @PostConstruct` seeder runs before `DefaultCaseDefinitionRegistry` finishes registering case definitions — no ordering guarantee between `@Startup` beans.
  **Fix:** Use `@Observes @Priority(20) StartupEvent`. CDI fires `StartupEvent` observers in ascending priority order; the engine's registry completes at priority=10. Priority=20 guarantees the seeder runs after.

### Pattern to replicate (in another domain)

1. Define a verdict enum and outcome record in `api/` — `record XxxOutcome(XxxVerdict verdict, String reason, double dimensionScore)`; compact constructor validates score in `[0.0, 1.0]` and null-checks non-null fields
2. Implement `TrustRoutingPolicyProvider` SPI in a `routing/` package — map capability names to `TrustRoutingPolicy` instances with per-capability thresholds, quality floors, and blend factors; fall back to `TrustRoutingPolicy.DEFAULT` for unknown capabilities; resolve overrides from `PreferenceProvider` first
3. Add `casehub-engine-ledger` to `app/pom.xml`
4. Create local re-numbered Flyway copies for engine-ledger tables at a non-colliding path; add the path to `quarkus.flyway.qhorus.locations` in both `application.properties` files; leave a comment in the SQL explaining the re-numbering
5. Add `CaseLedgerEntryRepository` to `quarkus.arc.exclude-types` in test `application.properties` — pending engine#396
6. Implement the trust score seeder with `@Observes @Priority(20) StartupEvent`; guard each `upsert()` with `if (trustRepo.findCapabilityScore(workerId, cap).isEmpty())` — this makes the seeder idempotent and safe to run on redeployments without overwriting live trust scores; call `trustScoreCache.hydrate()` after the loop; do not use `TrustBootstrapSource` for known initial values (it never fires on a fresh deployment)
7. Implement `XxxWorkerDecisionRepository` with JPQL queries on `WorkerDecisionEntry` via `@PersistenceContext(unitName = "qhorus")`
8. Implement `XxxOutcomeFeedbackService` — find `WorkerDecisionEntry` by caseId + capability; persist `LedgerAttestation` with `trustDimension`, `dimensionScore`, `verdict` (SOUND/FLAGGED); silently skip if no entry found — callers must not be blocked by missing history
9. Expose GET `/{caseId}` returning routing decisions per capability with current trust scores from `TrustScoreCache.getCapabilityScore(workerId, capabilityTag)` — note scores reflect the cache at response time, not at routing time
10. Tests: assert a seeded above-threshold worker is selected (GET → routingDecisions non-empty); POST an outcome; invoke `TrustScoreJob.runComputation()` directly; assert the score in `TrustScoreCache` has shifted

---

## Layer 7 — Compliance evidence (accountability properties mapped against FinCEN/FATF)

**Participates in:** S5
**Completed:** 2026-05-30
**Issue:** casehubio/aml#43
**Navigation:** `git log --grep="#43" --oneline`
**Spec:** `docs/specs/2026-05-30-layer7-compliance-evidence-design.md`
**Key files:**
- `api/src/main/java/io/casehub/aml/compliance/` — 10 API types: `ComplianceEvidence`, `RequirementStatus`, `AuditChainRequirement`, `LedgerEventRecord`, `AmlInclusionProof`, `AmlProofStep`, `SlaRequirement`, `TrustRoutingRequirement`, `RoutingDecisionRecord`, `GdprErasureRequirement`
- `app/src/main/java/io/casehub/aml/compliance/AmlComplianceEvidenceService.java` — assembles evidence across ledger, WorkItem, trust, and erasure
- `app/src/main/java/io/casehub/aml/compliance/AmlLayer7Resource.java` — REST endpoints: `GET /api/layer7/evidence/{caseId}`, `POST /api/layer7/actors/{actorId}/erasure`
- `app/src/main/java/io/casehub/aml/trust/AmlTrustRoutingAttestation.java` — JPA entity, JOINED from `LedgerEntry`, captures trust score at routing time
- `app/src/main/java/io/casehub/aml/trust/AmlTrustRoutingObserver.java` — CDI async observer on `WorkerDecisionEvent`, writes attestation before cache drifts
- `app/src/main/java/io/casehub/aml/trust/AmlTrustAttestationRepository.java` — JPQL queries on qhorus PU for attestation and `WorkerDecisionEntry`
- `app/src/main/java/io/casehub/aml/trust/AmlWorkerDecisionRepository.java` — queries `WorkerDecisionEntry` by caseId
- `app/src/main/java/io/casehub/aml/routing/AmlTrustRoutingPolicyProvider.java` — added `capabilities()` method
- `app/src/main/java/io/casehub/aml/ledger/AmlLedgerService.java` — `causedByEntryId` self-derived in `writeComplianceReviewOpened()`
- `app/src/main/resources/db/aml-trust-routing/migration/V2004__aml_trust_routing_attestation.sql`

### What it adds

Structured compliance evidence for a completed AML investigation, mapping accountability properties from Layers 1–6 against four FinCEN/FATF requirements. The evidence endpoint returns requirement-scoped status — `CLOSED`, `PARTIAL`, `BREACHED`, or `GAP` — with the underlying cryptographic proofs and structural data that an examiner needs to verify the claims independently.

### Prerequisite fixes

Two prerequisite issues were discovered and fixed before the evidence endpoint could report accurate status:

1. **`causedByEntryId` chain break.** `AmlLedgerService.writeComplianceReviewOpened()` left `causedByEntryId` null. The `COMPLIANCE_REVIEW_OPENED` event is causally produced by `CASE_OPENED` and must say so. The fix derives `causedByEntryId` inside the method itself — queries for the `CASE_OPENED` entry by `subjectId` — so it works for both the Layer 3 synchronous path and the Layer 5 async engine path where the entry ID is not in scope.

2. **Trust score not captured at routing time.** `WorkerDecisionEntry` records which worker was selected but not the trust score at the moment of routing. The `TrustScoreCache` drifts as attestations accumulate. `AmlTrustRoutingAttestation` (new `LedgerEntry` subclass) captures `trustScoreAtRouting` and `thresholdApplied` immutably when the routing decision is made.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| No externally verifiable evidence | Examiner has no endpoint that surfaces cryptographic proof for a given investigation — `verify()` boolean is a self-attestation, not evidence | `GET /api/layer7/evidence/{caseId}` returns Merkle inclusion proofs per ledger event; examiner reconstructs the tree root independently |
| Broken causal chain | `COMPLIANCE_REVIEW_OPENED` has no `causedByEntryId` — examiner cannot trace the review back to the case that produced it | `writeComplianceReviewOpened()` self-derives the link by querying for `CASE_OPENED` by `subjectId` |
| Trust score drift | Routing decisions recorded without the score used at routing time — post-hoc cache reads give different values | `AmlTrustRoutingObserver` captures `trustScoreAtRouting` from `TrustScoreCache` synchronously on `WorkerDecisionEvent` |
| No GDPR erasure endpoint | Erasure capability wired internally but not accessible to an examiner or data subject | `POST /api/layer7/actors/{actorId}/erasure` delegates to `LedgerErasureService` |

### Key wiring

**Four requirement types, one evidence endpoint.** `ComplianceEvidence` aggregates four requirement records, each with its own status logic:

- **`AuditChainRequirement`** — `FINCEN-31CFR1020.320-AUDIT-CHAIN`: queries `AmlInvestigationLedgerEntry` by `subjectId = caseId`, calls `LedgerVerificationService.verify(caseId)` for chain integrity, and `inclusionProof(entryId)` per event. Status is `CLOSED` when `chainVerified = true` AND all `COMPLIANCE_REVIEW_OPENED` events have `causedByEntryId` non-null. `PARTIAL` when entries exist but chain is not fully verified. `GAP` when no entries exist.

- **`SlaRequirement`** — `FINCEN-SAR-30DAY-SLA`: finds the `COMPLIANCE_REVIEW_OPENED` ledger entry, extracts the WorkItem task ID from `transactionId`, fetches `WorkItem` via `EntityManager.find()`. Status is `CLOSED` when `completedAt < claimDeadline`. `BREACHED` when the deadline has passed. `GAP` when no review entry exists.

- **`TrustRoutingRequirement`** — `FATF-R20-TRUST-ROUTING`: queries `AmlTrustRoutingAttestation` and `WorkerDecisionEntry` by caseId. Compares attested capabilities against dispatched capabilities. Status is `CLOSED` when all dispatched capabilities have attestations. `PARTIAL` when some are missing. `GAP` when none exist.

- **`GdprErasureRequirement`** — `GDPR-ART17-ERASURE`: static capability descriptor. `erasureCapabilityWired = true` (LedgerErasureService on classpath), `pseudonymizationActive = true`, `erasureEndpoint = "POST /api/actors/{actorId}/erasure"`. No per-case status — this is an architectural property, not per-investigation evidence.

**`@Path("/api/layer7")` — intentional routing namespace.** The original design spec placed evidence at `GET /api/investigations/{caseId}/compliance-evidence`. This created a JAX-RS routing conflict: `AmlInvestigationResource @Path("/api/investigations")` is more specific than `@Path("/api")`, so RESTEasy Reactive routed all `/api/investigations/...` requests to the former resource — which had no matching GET method, returning 404. The resource handler was never called (confirmed via debug output). Moving to `@Path("/api/layer7")` with sub-paths `/evidence/{caseId}` and `/actors/{actorId}/erasure` resolves the conflict cleanly.

**`AmlTrustRoutingObserver` uses `@ObservesAsync`, not `@Observes`.** The engine fires `WorkerDecisionEvent` asynchronously (`Event.fireAsync()`). A synchronous `@Observes` listener would never be called. `@ObservesAsync` with `@Transactional(REQUIRES_NEW)` decouples each attestation write from the engine worker's transaction, avoiding Merkle frontier contention on the same `subjectId`.

**`AmlTrustAttestationRepository` bypasses `LedgerEntryRepository.save()`.** The standard `save()` path calls `updateMerkleFrontier()`, which contends with the engine's own ledger writers on the same `subjectId`. Attestation entries use direct `EntityManager.persist()` on the qhorus persistence unit, skipping the Merkle frontier entirely. This means attestation entries are not in the Merkle tree — they are structural metadata about the routing decision, not auditable case events. The compliance evidence endpoint treats them as such.

**WorkItem lookup via `EntityManager.find()`.** `casehub-work-api` has no public query interface for reading a `WorkItem` by ID. `WorkItemStore` is internal. Direct JPA lookup on the default persistence unit is the correct approach since `WorkItem` is already in the Hibernate scan packages.

**`causedByEntryId` self-derived, not parameter-threaded.** The engine path (Layer 5) runs `writeComplianceReviewOpened()` on a Quartz thread where the `CASE_OPENED` entry ID is not in scope. Threading the ID through worker functions and `ComplianceReviewLifecycle` would couple the ledger service to the engine's execution model. Instead, `writeComplianceReviewOpened()` queries for the `CASE_OPENED` entry by `subjectId` and derives the link itself — works for both sync (Layer 3) and async (Layer 5) paths.

### Known gaps in engine path vs. synchronous path

The integration tests use the Layer 6 engine path (async, via Quartz workers). Two gaps exist relative to the Layer 3 synchronous path:

1. **SLA shows GAP.** The engine path does not call `writeComplianceReviewOpened()` — the sar-drafting worker creates the compliance officer WorkItem via `ComplianceReviewLifecycle.openReview()` but does not write a `COMPLIANCE_REVIEW_OPENED` ledger entry linking back to the WorkItem. Without this entry, the SLA requirement shows `GAP`. The Layer 3 path writes both entries and achieves `CLOSED`. Closing this gap for the engine path is tracked as a follow-up.

2. **Merkle chain `PARTIAL` in H2.** Concurrent `CaseLedgerEntry` writes from CDI async observers cause Merkle frontier collisions (unique constraint on `LEDGER_MERKLE_FRONTIER(subject_id, level)`) in the H2 test database. This makes `chainVerified = false` and `auditChain.status = PARTIAL`. Production PostgreSQL with row-level locking does not have this issue. Tests accept both `PARTIAL` and `CLOSED` for `auditChain.status`.

### Gotchas

- **Symptom:** `GET /api/investigations/{caseId}/compliance-evidence` returns 404 even though the investigation completed and ledger entries exist.
  **Cause:** JAX-RS routing conflict. `AmlInvestigationResource @Path("/api/investigations")` is more specific than `AmlLayer7Resource @Path("/api")`. RESTEasy Reactive routes all `/api/investigations/...` requests to `AmlInvestigationResource`, which has no matching GET subpath — returns 404 without ever calling the Layer 7 resource handler.
  **Fix:** Use `@Path("/api/layer7")` on `AmlLayer7Resource`. Endpoints become `/api/layer7/evidence/{caseId}` and `/api/layer7/actors/{actorId}/erasure`.

- **Symptom:** `POST /api/layer7/actors/{actorId}/erasure` returns 415 Unsupported Media Type with no request body.
  **Cause:** Class-level `@Consumes(MediaType.APPLICATION_JSON)` requires the client to set `Content-Type: application/json` even on body-less POST requests.
  **Fix:** Set `.contentType(ContentType.JSON)` on REST Assured POST calls that have no body.

- **Symptom:** `LedgerVerificationService.verify(caseId)` throws `IllegalStateException` — "no Merkle frontier for subject".
  **Cause:** Concurrent writes via async CDI observers can fail the Merkle frontier update (H2 unique constraint). When all frontier rows fail, `verify()` has no frontier to check against.
  **Fix:** Catch `IllegalStateException` in `buildAuditChain()` — set `chainVerified = false` and `treeRoot = null`. This is an infrastructure limitation, not an architectural gap.

- **Symptom:** `sla.status` returns `GAP` when the investigation completed with all workers including sar-drafting.
  **Cause:** The engine path creates the compliance officer WorkItem via `ComplianceReviewLifecycle.openReview()` but does not write a `COMPLIANCE_REVIEW_OPENED` `AmlInvestigationLedgerEntry`. Without this ledger entry, the SLA evidence builder has no WorkItem ID to look up.
  **Fix (deferred):** Add `AmlLedgerService.writeComplianceReviewOpened()` call in the sar-drafting worker or `ComplianceReviewLifecycle`. Test assertion accepts `GAP` for the engine path.

### Pattern to replicate (in another domain)

1. Fix any prerequisite chain breaks first — evidence requires complete causal chains. If `causedByEntryId` is null where it should not be, fix the writer method to self-derive the link via a query rather than threading IDs through parameters.

2. Define API types in `api/` — one record per compliance requirement, each with a `RequirementStatus` and the specific evidence fields an examiner needs. A root record aggregates all requirements. Include a nullable `signature` field as a forward signal for offline verification.

3. Capture trust scores at routing time, not later. Implement a CDI observer on `WorkerDecisionEvent` that reads from `TrustScoreCache` and persists an immutable attestation entry. Use `@ObservesAsync` if the engine fires events asynchronously. Persist via direct `EntityManager.persist()` on the appropriate PU to avoid Merkle frontier contention with the engine's own writers.

4. Create the evidence assembly service in `app/` — one method per requirement, each with its own status logic. Status is computed, not configured. Inject `LedgerEntryRepository`, `LedgerVerificationService`, `EntityManager` (for WorkItem lookup), and the trust attestation repository.

5. Expose a REST resource with:
   - `GET /api/{namespace}/evidence/{caseId}` → `ComplianceEvidence` (200 | 404)
   - `POST /api/{namespace}/actors/{actorId}/erasure` → `ErasureResult` (200)
   Use a dedicated `@Path` namespace to avoid JAX-RS routing conflicts with existing resources.

6. Integration tests: start investigation via engine path, poll until complete, then call the evidence endpoint. Accept both `PARTIAL` and `CLOSED` for Merkle chain status in H2 — the concurrency limitation is infrastructure, not architecture. Test GDPR erasure against the system actor (`aml-orchestrator`) since human actors do not yet write ledger entries.

7. Document known gaps between sync and async investigation paths — the evidence endpoint surfaces these as different status values, which is correct behaviour. The examiner sees the gap; the architecture does not hide it.

## Layer 8 — CaseMemoryStore integration (prior entity context)

**Participates in:** S5
**Completed:** 2026-06-04
**Issue:** casehubio/aml#32
**Navigation:** `git log --grep="#32" --oneline`
**Spec:** `docs/specs/2026-06-03-case-memory-store-design.md` (workspace)
**Key files:**
- `app/src/main/java/io/casehub/aml/memory/AmlMemoryDomains.java` — three `MemoryDomain` constants: `ENTITY_RISK`, `NETWORK`, `PATTERN`
- `app/src/main/java/io/casehub/aml/memory/AmlMemoryPolicyKeys.java` — `ENTITY_RISK_LOOKBACK_DAYS` preference key (default 365 days)
- `app/src/main/java/io/casehub/aml/memory/AmlPriorContext.java` — value record with `hasHistory()`, `isKnownHighRisk()`, `toContextMap()`
- `app/src/main/java/io/casehub/aml/memory/AmlMemoryService.java` — central CaseMemoryStore adapter; `queryPriorContext()` + four write methods; all failure-guarded
- `app/src/main/java/io/casehub/aml/memory/AmlSarOutcomeMemoryObserver.java` — CDI observer writing SAR outcome memories for both account IDs; `@Transactional(REQUIRES_NEW)`
- `app/src/main/java/io/casehub/aml/engine/SarOutcomeRecordedEvent.java` — CDI event decoupling `AmlLayer6Resource` from observers
- `app/src/main/java/io/casehub/aml/ledger/AmlCaseOpenedLedgerEntry.java` — replaces dual-use `AmlInvestigationLedgerEntry` for CASE_OPENED; adds `originAccountId`, `destinationAccountId`
- `app/src/main/java/io/casehub/aml/ledger/AmlComplianceReviewLedgerEntry.java` — replaces dual-use `AmlInvestigationLedgerEntry` for COMPLIANCE_REVIEW_OPENED; has `taskId`
- `app/src/main/resources/db/aml-ledger/migration/V2007__aml_ledger_subclass_split.sql` — Flyway V2007 on qhorus datasource; drops old table, creates two sibling join tables
- `app/src/main/resources/aml/aml-investigation.yaml` — `senior-analyst-required` split into `senior-analyst-required-prior-context` (fires when `entityResolution == null`) + `senior-analyst-required-resolution` (fires when `priorContext.knownHighRisk != true`)

### What it adds

**Before:** Every investigation case starts cold. Prior case outcomes, entity risk classifications, and network relationships are invisible to new cases. Analysts must manually pull SAR history.

**After:** `AmlMemoryService.queryPriorContext()` queries `CaseMemoryStore` across three domains before `startCase()`. The result is injected into `initialContext` as `priorEntityContext`. The YAML binding `senior-analyst-required-prior-context` evaluates `priorEntityContext.knownHighRisk` and can route known-high-risk entities to a senior analyst immediately — before entity resolution confirms it.

What this layer adds:
- **Prior context query at case open** — `AmlEngineCoordinator.startInvestigation()` calls `queryPriorContext()` synchronously before `startCase()`; query uses `withLimit(20).withSince(lookbackCutoff)` per domain
- **Fact accumulation at case close** — `EntityResolutionBehaviour` writes entity-risk + network facts; `PatternAnalysisBehaviour` writes pattern facts; `AmlSarOutcomeMemoryObserver` writes SAR outcome facts (UPHELD with actual confidence; WITHDRAWN/FLAGGED with confidence=0.0 as reversal signal)
- **Ledger subclass redesign** — `AmlInvestigationLedgerEntry` was dual-use (`eventType` string); replaced with two typed siblings. `AmlCaseOpenedLedgerEntry` now carries non-nullable `originAccountId` and `destinationAccountId`, required by `AmlSarOutcomeMemoryObserver` to write both-account memories
- **SAR outcome CDI decoupling** — `AmlLayer6Resource` fires `SarOutcomeRecordedEvent`; `SarOutcomeFeedbackService` and `AmlSarOutcomeMemoryObserver` both observe it independently
- **YAML binding split** — the original merged OR binding caused a double-dispatch race: async Quartz execution fires the merged condition twice before `seniorAnalystReview` is written back to context, causing two concurrent Quartz jobs to race on `UQ_MERKLE_FRONTIER_SUBJECT_LEVEL`. Two complementary bindings with mutually exclusive guards eliminate the race
- **Trust seeder fix** — `senior-analyst-agent` seed changed from Beta(8,2)=0.80 to Beta(10,1)≈0.909; Beta(8,2) was exactly at the threshold and within the borderline zone [threshold−margin, threshold+margin], triggering routing escalation on every investigation

### Test infrastructure fixes

- `casehub.ledger.hash-chain.enabled=false` in test properties — H2 lacks PostgreSQL row-level locking; concurrent Quartz worker jobs for the same case race on `UQ_MERKLE_FRONTIER_SUBJECT_LEVEL`; disabling eliminates the constraint as a cross-test contamination vector (PP-20260604-f45c95)
- Investigation drain pattern — every `@QuarkusTest` that starts an engine investigation polls `GET /api/layer6/investigations/{id}` until `status = "completed"` before returning; prevents pending Quartz jobs from contaminating subsequent tests (PP-20260604-820c35)

---

## Layer 9 — ActionRiskClassifier oversight gate (aml#42)

**Participates in:** S6 (new — oversight gate)
**Completed:** 2026-06-09
**Issue:** casehubio/aml#42
**Also closes:** casehubio/aml#57

### What it adds

`AmlActionRiskClassifier @RiskClassifier @ApplicationScoped` — implements the engine's `ActionRiskClassifier` SPI. Encodes FinCEN/FATF regulatory gate requirements for five consequential AML action types. Discovered automatically by the engine via `@RiskClassifier` CDI qualifier; no explicit wiring.

`AmlActionType` enum — pure Java domain vocabulary in `api/` encoding gate policy, reversibility, candidate groups, reason string, and oversight scope per action type. `AmlGroups` companion holds group name constants.

Layer 9 oversight harness — `AmlOversightCaseHub` + `AmlOversightCoordinator` + `AmlLayer9Resource` demonstrate the gate in a self-contained investigation flow (`entity-resolution` → `entity-link-proposal` (PlannedAction) → `investigation-summary`). The gate fires for PEP or high-risk entities; low-risk CORPORATE cases proceed autonomously.

`casehub-engine-work-adapter` + `casehub-engine-blackboard` added as compile deps — provides `ActionGateWorkItemHandler` (creates WorkItem when gate fires) and `WorkItemLifecycleAdapter` (resumes case on WorkItem approval).

### Key design decisions

- Layer 9 is a DEDICATED oversight harness, NOT wired into the existing Layer 1–8 investigation flow. Layer 1–8 workers are unchanged; the classifier is live in CDI and fires for any future worker that declares a PlannedAction.
- `AmlActionType.GatePolicy` variants: `ALWAYS`, `RISK_SCORE_THRESHOLD`, `CONFIDENCE_THRESHOLD`. Thresholds are regulatory constants, not preferences.
- Fail-closed: known action type with missing context → `GateRequired("Risk assessment unavailable")`. Unknown action type → `Autonomous`.
- `AmlLayer9Resource.getInvestigation()` uses `CaseInstanceCache` for completion detection (resilient to concurrent H2 `ledger_subject_sequence` INSERT race).

### Known issues / deferred

- `sar-drafting` workers still call `openReview()` as an unconditional side effect — correct design would add `PlannedAction(SAR_FILING)` and a separate `compliance-review-opening` step. Tracked in aml#58.
- casehub-work SNAPSHOT (June 2026) added `TenantScopedPrincipal @RequestScoped` which causes `caseInstance.tenancyId = null` on Vert.x event loop threads. This breaks `WorkerDecisionEntry` saves in Layer 5–8 tests. Fix needed in casehub-work (aml#59).
- `ledger_subject_sequence` concurrent INSERT race in H2 when multiple Quartz workers fire `WorkerDecisionEvent` simultaneously. Affects reliability of `WorkerDecisionEntry` in tests. Fix needed in casehub-ledger.
- `docs/sql/V2011__UQ_TRUST_ATTEST_CASE_CAP_RECONSTRUCTED.sql` — partial unique index for `aml_trust_routing_attestation.reconstructed = TRUE`. Apply manually on production PostgreSQL after V2009 (H2 limitation, aml#57 closed).

### Known gaps

- `caseId` in behaviour-generated memory entries is `null` until qhorus#190 ships — `PushAgentDispatch.post()` passes null to `behaviour.handle()` because `OutboundMessage.correlationId` contains the engine's `eventLogId` (a Long), not the case UUID, and no engine context-update API exists to inject the caseId post-`startCase()`
- Cascade GDPR erasure for network cross-references — account A's erasure removes A's network memories but B's memory still names A; legal boundary assessment required (aml#N)
- Account-scoped memory accumulation (not entity-scoped) — beneficial owner X using accounts A, B, C builds no cross-account intelligence; two-phase keying deferred (see spec §Known architectural limitation)
