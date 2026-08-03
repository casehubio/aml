# Case Timeline Component — Design Spec

**Date:** 2026-08-03
**Status:** Approved
**Issue:** casehubio/aml#105
**Scope:** Vertical timeline of investigation milestones in the Overview tab

---

## Purpose

Replace the placeholder in the Overview tab (`aml-investigation-overview.ts`) with a
vertical timeline showing investigation milestones. The timeline renders the same audit
trail data shown in the Audit tab, but as a chronological vertical timeline with visual
hierarchy instead of a flat table.

Default view: investigation milestones (case opened, specialist dispatches/completions,
gate decisions, compliance review, SAR decision). Click any node to expand and see the
underlying raw ledger entries.

---

## Approach

Frontend grouping over an enriched backend response. The backend exposes the JPA
discriminator and domain-specific fields on each audit trail entry. The frontend
strategy groups related entries into milestone nodes for the timeline.

Three pieces:
1. **Backend** — enrich `AuditTrailEntryResponse` with discriminator + domain fields
2. **Frontend** — `AmlInvestigationTimelineStrategy` for `blocks-timeline`
3. **Integration** — replace placeholder in overview component

---

## 1. Backend — Enriched Audit Trail Response

### Record change

`AuditTrailEntryResponse` gains two fields:

```java
public record AuditTrailEntryResponse(
    UUID entryId,
    String entryType,
    String discriminator,                // NEW — @DiscriminatorValue string
    String actorId,
    String actorRole,
    Instant occurredAt,
    UUID causedByEntryId,
    String digest,
    int sequenceNumber,
    Map<String, Object> domainFields     // NEW — subclass-specific fields
) {}
```

### Resource change

`AmlAuditTrailResource.getAuditTrail()` reads the `@DiscriminatorValue` annotation
from each entry's class and extracts domain fields via `instanceof` pattern matching:

| Subclass | Discriminator | Domain fields |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `AML_CASE_OPENED` | transactionId, originAccountId, destinationAccountId |
| `AmlComplianceReviewLedgerEntry` | `AML_COMPLIANCE_REVIEW` | taskId |
| `AmlSarOfficerReviewedLedgerEntry` | `AML_SAR_OFFICER_REVIEWED` | reviewDecision, rejectionReason, actorRole |
| `AmlCaseProfileLedgerEntry` | `AML_CASE_PROFILE` | flagReason, transactionAmount, outcome, entityType, investigationPath |
| `AmlCbrAdvisoryLedgerEntry` | `AML_CBR_ADVISORY` | (fields TBD during implementation — read from entity) |
| Qhorus `MessageLedgerEntry` | `QHORUS_MESSAGE` | messageType, target, correlationId, topic, durationMs |
| Engine `CaseLedgerEntry` | `CASE` | caseStatus, eventType, commandType |
| Engine `WorkerDecisionEntry` | `WORKER_DECISION` | workerId, capabilityTag, trustScoreAtRouting, routingRationale |
| Any unrecognised subclass | raw discriminator string | empty map |

The discriminator is read from the `@DiscriminatorValue` annotation at runtime
with a null guard: if the annotation is absent, fall back to the class simple name.
This prevents a NullPointerException from crashing the entire audit trail response.

```java
var ann = entry.getClass().getAnnotation(DiscriminatorValue.class);
String discriminator = ann != null ? ann.value() : entry.getClass().getSimpleName();
```

### Field semantics

`entryType` is the base ledger category (`LedgerEntryType`: EVENT, ATTESTATION,
CONTROL). `discriminator` is the JPA subclass identity (e.g., `AML_CASE_OPENED`,
`QHORUS_MESSAGE`). The frontend strategy keys off `discriminator` to determine
milestone type. The existing Audit tab uses `entryType` for its column display —
both fields are retained, they serve different purposes.

### TypeScript type update

```typescript
export interface AuditTrailEntry {
  entryId: string;
  entryType: string;
  discriminator: string;                    // NEW
  actorId: string;
  actorRole: string;
  occurredAt: string;
  causedByEntryId: string | null;
  digest: string;
  sequenceNumber: number;
  domainFields: Record<string, unknown>;    // NEW
}
```

### Frontend type safety

The frontend uses a discriminated union keyed on `discriminator` to narrow
`domainFields` to concrete types per entry kind:

```typescript
interface AmlCaseOpenedFields { transactionId: string; originAccountId: string; destinationAccountId: string }
interface AmlSarReviewedFields { reviewDecision: string; rejectionReason: string | null; actorRole: string }
interface QhorusMessageFields { messageType: string; target: string; correlationId: string; topic: string; durationMs: number }
// ... per discriminator
```

This catches key mismatches at compile time on the frontend side. The backend
`Map<String, Object>` remains untyped — the instanceof dispatch is acceptable
for pre-release with a bounded set of subclasses.

### Backwards compatibility

Additive change. The Audit tab table continues to work unchanged — it ignores the
new fields. The inclusion proof endpoint is unaffected.

---

## 2. Frontend — Strategy and Component Integration

### Dependency

Add `@casehubio/blocks-ui-blocks-timeline` to `package.json` dependencies and
resolutions using the same portal pattern as other blocks-ui packages.

### Strategy file

`app/src/main/webui/src/strategies/aml-investigation-timeline.ts`

Implements `TimelineStrategy<AuditTrailEntry[]>`.

#### Pass 1 — Group related entries into milestones

Qhorus message entries (`discriminator: "QHORUS_MESSAGE"`) are paired by capability.
The pairing uses `domainFields.correlationId` to match a COMMAND with its DONE/DECLINE
response. The `domainFields.messageType` field distinguishes COMMAND from DONE/DECLINE.
The `domainFields.topic` field carries the capability name (e.g., `"entity-resolution"`)
used for the milestone label.

Pairing algorithm:
1. Partition entries: qhorus messages vs all others
2. Group qhorus messages by `correlationId`
3. Within each group, find the COMMAND entry and its terminal entry (DONE or DECLINE)
4. If no terminal entry exists, the specialist is still in progress (unpaired COMMAND)
5. If multiple COMMANDs share a correlationId (retry scenario), pair with the latest

All other entry types are standalone milestones — one `TimelineNode` per entry.

**Observer failure entries:** `AmlSarOfficerReviewedLedgerEntry` entries with
`actorRole` containing `"observer-failed"` are mapped to a distinct milestone label
("SAR Review — observer failure") rather than appearing as genuine SAR decisions.

#### Pass 2 — Map each milestone to a TimelineNode

| Discriminator | Label | Category | Status | actor | timestamp |
|---|---|---|---|---|---|
| `AML_CASE_OPENED` | "Investigation Opened" | `lifecycle` | `completed` | actorId | occurredAt |
| Qhorus COMMAND+DONE pair | "{Capability} completed" | `agent` | `completed` | DONE entry's actorId | DONE entry's occurredAt |
| Qhorus COMMAND+DECLINE pair | "{Capability} declined" | `agent` | `skipped` | DECLINE entry's actorId | DECLINE entry's occurredAt |
| Qhorus COMMAND only (unpaired) | "{Capability} in progress" | `agent` | `active` | COMMAND entry's actorId | COMMAND entry's occurredAt |
| `CASE` (engine) | "Case {caseStatus}" | `lifecycle` | mapped from caseStatus | actorId | occurredAt |
| `WORKER_DECISION` (engine) | "{capabilityTag} routed" | `agent` | `completed` | workerId from domainFields | occurredAt |
| `AML_COMPLIANCE_REVIEW` | "Compliance Review Opened" | `milestone` | `completed` | actorId | occurredAt |
| `AML_SAR_OFFICER_REVIEWED` | "SAR Officer: {decision}" | `milestone` | `completed`/`failed` based on decision | actorId | occurredAt |
| `AML_SAR_OFFICER_REVIEWED` (observer-failed) | "SAR Review — observer failure" | `milestone` | `failed` | actorId | occurredAt |
| `AML_CASE_PROFILE` | "Case Profile Retained" | `orchestration` | `completed` | actorId | occurredAt |
| `AML_CBR_ADVISORY` | "CBR Path Advisory" | `orchestration` | `completed` | actorId | occurredAt |
| Unknown | Raw discriminator string | `orchestration` | `completed` | actorId | occurredAt |

The `CASE` engine entries with `caseStatus: "COMPLETED"` map to "Case Closed" —
satisfying issue #105's "Case closed with outcome" requirement. The outcome details
come from the `AML_SAR_OFFICER_REVIEWED` entry earlier in the chain.

Each node's `detail` holds the raw entry (or array of entries for grouped pairs).
`renderDetail` renders them as a property tree when expanded.

#### TimelineNode.key derivation

- Single-entry milestones: `key = entryId`
- Grouped COMMAND+DONE/DECLINE pairs: `key = correlationId` (the pairing key)
- Engine entries: `key = entryId`

#### Ordering

Nodes are ordered by `sequenceNumber` (ascending). Tiebreaker for equal sequence
numbers (parallel writes): `occurredAt` ascending, then `entryId` for determinism.

#### Capability label mapping

```typescript
const CAPABILITY_LABELS: Record<string, string> = {
  'entity-resolution': 'Entity Resolution',
  'pattern-analysis': 'Pattern Analysis',
  'osint-screening': 'OSINT Screening',
  'sar-drafting': 'SAR Drafting',
};
```

Fallback for unknown capabilities: title-case the raw slug (replace hyphens with
spaces, capitalize each word). New specialist capabilities display without a
frontend change — the label is just less polished until added to the map.

#### Category filtering

`filterCategories: ['lifecycle', 'agent', 'milestone', 'orchestration']` — all
shown by default. User can toggle off `orchestration` to hide CBR/internal entries.

### Integration — Overview component

In `aml-investigation-overview.ts`, replace `_renderTimelinePlaceholder()`:

```typescript
import '@casehubio/blocks-ui-blocks-timeline';
import { amlInvestigationTimelineStrategy } from '../strategies/aml-investigation-timeline.js';

private _renderTimeline() {
  if (!this.caseId) return nothing;
  return html`
    <div class="section">
      <div class="section-title">Case Timeline</div>
      <blocks-timeline
        endpoint="/api/investigations/${this.caseId}/audit-trail"
        .strategy=${amlInvestigationTimelineStrategy()}
        layout="vertical"
      ></blocks-timeline>
    </div>
  `;
}
```

`blocks-timeline` manages its own fetch lifecycle via `DataSourceMixin` — no new
fetch methods or state fields needed in the overview component.

### Import registration

Add `import '@casehubio/blocks-ui-blocks-timeline'` to `index.ts` alongside the
other panel imports.

### No changes to `aml-app.ts`

The timeline lives inside the existing Overview tab, not as a new tab.

---

## 3. Testing

### Backend — Java

| Layer | What | Key cases |
|---|---|---|
| Unit test | Discriminator extraction | Each AML subclass maps to correct discriminator; unknown subclass -> empty domain fields; null domain field values handled |
| Unit test | Domain field extraction per subclass | `AmlCaseOpenedLedgerEntry` -> transactionId/accounts; `AmlSarOfficerReviewedLedgerEntry` -> reviewDecision/rejectionReason including null rejection |
| `@QuarkusTest` | Enriched response | Start investigation via Layer 9, drain to completion, GET audit trail, verify discriminator and domainFields present on each entry type. Verify existing fields unchanged |

### Frontend — TypeScript (vitest)

| What | Key cases |
|---|---|
| `toNodes()` | Empty array -> empty nodes; single AML_CASE_OPENED -> one lifecycle node; COMMAND+DONE pair -> one completed agent node; COMMAND+DECLINE -> one skipped agent node; unpaired COMMAND -> active node; mixed types -> correct ordering by sequenceNumber |
| Milestone grouping | Two COMMANDs for different capabilities don't get paired; parallel specialists each get their own milestone |
| Category mapping | Each discriminator maps to correct category; unknown discriminator -> orchestration fallback |
| `renderDetail` | Expanded node shows raw entry fields; grouped node shows both entries |

### Integration (manual)

Start app with `mvn quarkus:dev`, run investigation via simulation, select case,
verify Overview tab shows timeline with correct milestones, expand nodes, toggle
category filters.

---

## Garden entries consulted

- GE-20260607-b6d999 — dedicated per-concern ledger subject isolation
- GE-20260607-1c0a05 — ledger subject isolation constraint violation
- GE-20260628-dbc656 — PlannedAction workers block at oversight gate; attestation ordering
- GE-20260727-fc7a9a — investigation test flag reasons after #112 triage logic
- GE-20260720-6ea915 — CbrCaseRetainObserver CDI exclusion

---

## Out of scope

- Real-time updates via WebSocket/SSE (tracked in casehubio/aml#89)
- Trust routing attestation display in the timeline (different subjectId — shown in Routing tab)
- Entity erasure display in the timeline (different subjectId — shown in Accountability tab)
- Custom icons per milestone type (use category-based dot styling from blocks-timeline)
