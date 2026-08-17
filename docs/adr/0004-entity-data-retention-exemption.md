# 0004 — entity data retention exemption under GDPR Art.17(3)(b)

Date: 2026-08-14
Status: Accepted

## Context and Problem Statement

AML ledger entry subclasses contain entity identifiers (account numbers, transaction
IDs) in their `domainContentBytes()` — the content that participates in the Merkle
leaf hash via `canonicalBytes()`. GDPR Art.17 grants data subjects the right to
erasure, but modifying `domainContentBytes()` post-save invalidates the tamper-evident
chain. No existing mechanism supports entity-data redaction inside Merkle-chained content.

The question is whether AML investigation records are exempt from Art.17 erasure,
and if so, under which legal basis.

## Decision Drivers

* `domainContentBytes()` participates in the Merkle leaf hash — content modification
  post-save invalidates the tamper-evident chain
* AML investigations are subject to regulatory retention obligations (BSA, 4AMLD, FATF)
* Existing erasure capabilities (actor pseudonymisation, entity memory erasure)
  remain fully operational and are not affected
* A content redaction layer would require foundation-level design (chain re-computation)

## Affected Data

| Entry | Exempt fields | Rationale |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `originAccountId`, `destinationAccountId`, `transactionId` | Transaction parties and reference — core investigation record |
| `AmlEntityErasureLedgerEntry` | `erasedEntityId` | Erasure receipt — must identify what was erased for Art.5(2) accountability |

Other AML ledger entry subclasses (`AmlCaseProfileLedgerEntry`,
`AmlComplianceReviewLedgerEntry`, `AmlSarOfficerReviewedLedgerEntry`) do not
contain entity identifiers in `domainContentBytes()` and are not affected.

New ledger entry subclasses, and modifications to existing subclasses that add
entity identifiers to `domainContentBytes()`, must explicitly assess whether
the content falls under this exemption.

## Considered Options

* **Option A** — Document Art.17(3)(b) exemption; no content redaction
* **Option B** — Build a content redaction layer despite the exemption
* **Option C** — Document exemption now; stub redaction API for future

## Decision Outcome

Chosen option: **Option A** — document the Art.17(3)(b) exemption, because AML
investigation records are retained under positive legal obligations imposed by
financial regulators.

**Regulatory basis:**
- **GDPR Art.17(3)(b):** "compliance with a legal obligation which requires
  processing by Union or Member State law to which the controller is subject"
- **FinCEN BSA 31 CFR 1020.320(d):** 5-year SAR retention obligation
- **4AMLD Art.40:** 5-year record retention for AML investigation documentation
- **FATF Recommendation 11:** Record-keeping requirements for transaction records

**Why Art.17(3)(b), not Art.17(3)(e):** Art.17(3)(e) covers the controller's own
legal claims (defending against lawsuits). AML retention is a positive legal
obligation imposed by regulators. Art.17(3)(b) is mandatory (controller MUST retain);
Art.17(3)(e) is discretionary and subject to proportionality challenges.

### What is NOT affected

- Actor-level erasure (`LedgerErasureService.erase()` — actorId pseudonymisation)
  remains fully operational
- Entity memory erasure (`CaseMemoryStore.eraseEntity()`) remains fully operational
- `ComplianceSupplement` content — already sanitised by `AmlContentSanitiser`

### CaseContext entity data

Engine `CaseContext` is out of scope for GDPR erasure:
1. `InMemoryCaseContextStore` uses a `LinkedHashMap` evicted at case completion
2. `AmlCaseProfileStoreObserver` deliberately excludes `originAccountId` and
   `destinationAccountId` from persisted fields
3. No persistent `CaseContextStore` implementation exists

If a persistent context store is introduced, this analysis must be revisited.

### Jurisdictional note

EU member states have different implementations of Art.17(3) exemption provisions.
The exemption is treated as universal in this implementation but should be validated
per jurisdiction in production deployments.

### Positive Consequences

* No foundation-level design work required
* Existing Merkle chain integrity preserved — no content modification
* Clear regulatory basis documented for examiners
* Compliance evidence report explicitly surfaces the exemption

### Negative Consequences / Tradeoffs

* No content redaction capability exists if the exemption is later found insufficient
* Jurisdictional variability not modelled — exemption treated as universal
* Mitigated by contingency tracking issue (casehubio/aml#127)

## Links

* Implemented in casehubio/aml#83
* Spec: `specs/issue-7-gdpr-regulatory-audit/2026-08-14-entity-data-erasure-exemption-design.md`
* Decisions: D6, D7, D8 in `specs/issue-7-gdpr-regulatory-audit/decisions.md`
