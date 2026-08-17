# GDPR Entity Data Erasure in Tamper-Evident Ledger Content

**Issue:** #83 — feat: GDPR entity data erasure in tamper-evident ledger content and case context
**Branch:** `issue-7-gdpr-regulatory-audit`
**Date:** 2026-08-14
**Decisions:** D6–D8 in `decisions.md`

---

## Problem Statement

Entity identifiers (account numbers, transaction IDs) appear in AML ledger entry content:

| Ledger entry | Entity data fields | In `domainContentBytes()`? |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `originAccountId`, `destinationAccountId`, `transactionId` | Yes |
| `AmlEntityErasureLedgerEntry` | `erasedEntityId` | Yes |
| `AmlCaseProfileLedgerEntry` | No direct entity IDs | N/A |
| `AmlComplianceReviewLedgerEntry` | `taskId` (WorkItem ref, not PII) | Yes |
| `AmlSarOfficerReviewedLedgerEntry` | No entity IDs | N/A |

`domainContentBytes()` participates in the Merkle leaf hash via `canonicalBytes()`. Modifying content post-save invalidates the tamper-evident chain. There is no existing mechanism for entity-data redaction inside Merkle-chained content.

The engine's `CaseContext` also holds entity data during processing, but this is transient (evicted at case completion) and observers that persist from it (`AmlCaseProfileStoreObserver`) deliberately exclude identifying entity data.

## Scope

**In scope:**
- ADR documenting the Art.17(3)(b) regulatory retention exemption
- `GdprErasureRequirement` record extension with exemption fields
- `AmlComplianceEvidenceService` update to populate exemption fields
- Tests for the new fields
- Follow-up tracking issue for contingency redaction

**Not in scope:**
- Content redaction layer (D6: exemption applies; redaction deferred to contingency issue)
- CaseContext erasure (D8: transient, observers exclude PII from persisted fields)
- Foundation repo changes (no new SPIs or ledger modifications needed)

---

## Design

### Part 1: ADR — Art.17(3)(b) Regulatory Retention Exemption

New ADR in `docs/adr/` documenting the regulatory interpretation.

**Decision:** AML investigation ledger content containing entity identifiers (account numbers, transaction IDs) in `domainContentBytes()` is exempt from GDPR Art.17 erasure under Art.17(3)(b).

**Regulatory basis:**
- **GDPR Art.17(3)(b):** "compliance with a legal obligation which requires processing by Union or Member State law to which the controller is subject"
- **FinCEN BSA 31 CFR 1020.320(d):** 5-year SAR retention obligation
- **4AMLD Art.40:** 5-year record retention for AML investigation documentation
- **FATF Recommendation 11:** Record-keeping requirements for transaction records

**Why Art.17(3)(b), not Art.17(3)(e):** Art.17(3)(e) covers the controller's own legal claims (defending against lawsuits). AML retention is a positive legal obligation imposed by regulators — Art.17(3)(b) is the correct and stronger exemption. Art.17(3)(b) is mandatory (controller MUST retain); Art.17(3)(e) is discretionary and subject to proportionality challenges.

**Scope of exemption — specific entries and fields:**

| Entry | Exempt fields | Rationale |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `originAccountId`, `destinationAccountId`, `transactionId` | Transaction parties and reference — core investigation record |
| `AmlEntityErasureLedgerEntry` | `erasedEntityId` | Erasure receipt — must identify what was erased for Art.5(2) accountability |

Other AML ledger entry subclasses (`AmlCaseProfileLedgerEntry`, `AmlComplianceReviewLedgerEntry`, `AmlSarOfficerReviewedLedgerEntry`) do not contain entity identifiers in `domainContentBytes()` and are not affected.

New ledger entry subclasses must explicitly assess whether their `domainContentBytes()` content falls under this exemption.

**Not affected:**
- Actor-level erasure (`LedgerErasureService.erase()` — actorId pseudonymisation remains fully operational)
- Entity memory erasure (`CaseMemoryStore.eraseEntity()` — memory erasure remains fully operational)

**Jurisdictional note:** EU member states have different implementations of Art.17(3) exemption provisions. UK (post-Brexit), German, and French deployments may have different legal frameworks. The exemption is treated as universal in this implementation but should be validated per jurisdiction in production deployments.

**Revisit trigger:** If legal review determines that Art.17(3)(b) does not fully apply in a specific jurisdiction, or if a persistent `CaseContextStore` is introduced, see the contingency tracking issue for a content redaction approach.

### Part 2: `GdprErasureRequirement` Extension (api/ module)

Add two fields to the existing record:

```java
public record GdprErasureRequirement(
        String id,
        String citation,
        String mechanism,
        RequirementStatus status,
        boolean tokenisationEnabled,
        boolean erasureReceiptEnabled,
        long erasureReceiptCount,
        String erasureEndpoint,
        String retentionCitation,
        String retentionAdrRef
) {
    // ... existing constants ...

    public static final String RETENTION_CITATION =
            "GDPR Art.17(3)(b), BSA 31 CFR 1020.320(d), 4AMLD Art.40";
}
```

**Field semantics:**

| Field | Type | Value |
|---|---|---|
| `retentionCitation` | `String` | `"GDPR Art.17(3)(b), BSA 31 CFR 1020.320(d), 4AMLD Art.40"` — non-null signals exemption is applied |
| `retentionAdrRef` | `String` | ADR reference (e.g. `"ADR-0005"`) |

No boolean flag — `retentionCitation` non-null is sufficient to signal the exemption. A boolean that can only be `true` (setting it to `false` while having no redaction capability creates an incoherent state) adds no information.

**Status semantics (D7):** `status` reflects erasure capability only (`tokenisationEnabled && receiptEnabled`). The exemption fields are informational context for examiners — they document why entity data in ledger content is retained, but do not participate in the status computation.

### Part 3: `AmlComplianceEvidenceService` Update (app/ module)

Update `buildGdprErasure()` to populate the new fields:

```java
private GdprErasureRequirement buildGdprErasure() {
    boolean tokenisationEnabled = ledgerConfig.identity().tokenisation().enabled();
    boolean receiptEnabled = ledgerConfig.erasureReceipt().enabled();

    long receiptCount = 0L;
    try {
        receiptCount = erasureReceiptRepo.countByTenant(
                TenancyConstants.DEFAULT_TENANT_ID);
    } catch (Exception ignored) {
    }

    RequirementStatus status;
    if (tokenisationEnabled && receiptEnabled) {
        status = RequirementStatus.CLOSED;
    } else if (tokenisationEnabled || receiptEnabled) {
        status = RequirementStatus.PARTIAL;
    } else {
        status = RequirementStatus.GAP;
    }

    return new GdprErasureRequirement(
            GdprErasureRequirement.REQUIREMENT_ID,
            GdprErasureRequirement.CITATION,
            GdprErasureRequirement.MECHANISM,
            status, tokenisationEnabled, receiptEnabled, receiptCount,
            GdprErasureRequirement.ERASURE_ENDPOINT,
            GdprErasureRequirement.RETENTION_CITATION,
            "ADR-NNNN");
}
```

The exemption fields are hardcoded — the exemption is a policy decision, not a runtime determination. The ADR reference is set at build time and updated when the ADR number is assigned.

### Part 4: CaseContext Documentation (no code change)

The ADR includes a section documenting why CaseContext entity data is out of scope:
1. `InMemoryCaseContextStore` uses a `LinkedHashMap` evicted at case completion — transient
2. `AmlCaseProfileStoreObserver` deliberately excludes `originAccountId` and `destinationAccountId` from persisted fields
3. No persistent `CaseContextStore` implementation exists in the current architecture

If a persistent context store is introduced, this analysis must be revisited.

### Part 5: Follow-Up Tracking Issue

Create a GitHub issue on `casehubio/aml`:
- **Title:** "contingency: entity data redaction in tamper-evident ledger content"
- **Body:** References the ADR. Describes what a redaction layer would need to do (replace entity IDs in `domainContentBytes()` while maintaining Merkle chain integrity — likely requires a foundation-level `ContentRedactionService` with chain re-computation).
- **Labels:** none (backlog)

---

## Test Specification

### Unit tests (api/ module)

- `GdprErasureRequirementTest`:
  - Construction with all fields including new exemption fields
  - Verify `RETENTION_CITATION` constant value

### @QuarkusTest (app/ module)

- `AmlComplianceEvidenceServiceTest` additions:
  - Verify `gdprErasure.retentionCitation()` contains `"Art.17(3)(b)"`
  - Verify `gdprErasure.retentionAdrRef()` is non-null

### Integration tests

- Layer 7 GET compliance evidence → JSON includes `retentionCitation`, `retentionAdrRef` fields

---

## Protocol Compliance

| Protocol | Status |
|----------|--------|
| `domainContentBytes()` enforcement | N/A — no ledger entry changes |
| Ledger subject isolation | N/A — no new ledger entries |
| api/ module purity | Compliant — `GdprErasureRequirement` record has no framework dependencies |
| ADR conventions | Follow existing `docs/adr/` format |

## Platform Coherence

- No foundation types created or modified
- No foundation repo changes required
- `GdprErasureRequirement` is AML-local — change does not affect other harnesses
- Existing erasure capabilities (actor pseudonymisation, entity memory erasure) remain unchanged
- Follow-up issue tracks contingency redaction if needed

## Garden Entries Applied

- **GE-20260531-46f8ab:** Tokenisation config requirement for erasure tests — context for existing `GdprErasureRequirement` fields
- **GE-20260628-6599e6:** Post-erasure actor-scoped queries return empty — confirms actor erasure works but content is untouched
- **GE-20260814-05ef39:** Supplement must be attached before save — Merkle integrity constraint that makes content redaction non-trivial
