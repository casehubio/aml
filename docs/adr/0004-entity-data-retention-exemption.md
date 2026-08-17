# 0004 — GDPR Art.17(3)(b) exemption for entity identifiers in tamper-evident ledger content

Date: 2026-08-17
Status: Accepted

## Context and Problem Statement

AML `LedgerEntry` subclasses include entity identifiers (transaction IDs, account IDs)
in their `domainContentBytes()` override. These bytes participate in the Merkle leaf hash
via `SHA-256(0x00 | entry.canonicalBytes())`. GDPR Art.17 grants data subjects a right
to erasure, but Art.17(3)(b) exempts processing "for compliance with a legal obligation
which requires processing by Union or Member State law."

The question: can entity identifiers remain in `domainContentBytes()` after an erasure
request, or must they be redacted?

## Decision Drivers

* FinCEN/FATF require tamper-evident investigation records — the Merkle chain is the
  mechanism that satisfies this; redacting leaf content breaks chain verifiability
* AML record retention is a legal obligation under the Bank Secrecy Act (31 USC §5318),
  4th Anti-Money Laundering Directive (EU 2015/849), and FATF Recommendation 11
* The existing erasure mechanism (`LedgerErasureService`) operates at the actor-token
  level — it severs the mapping between a pseudonymised token and a natural person's
  identity, leaving ledger entries intact but permanently anonymous
* Entity identifiers (account IDs, transaction IDs) are not natural person identifiers
  — they are references to financial instruments, not to data subjects directly

## Considered Options

* **Option A** — Retain entity identifiers in `domainContentBytes()`, relying on Art.17(3)(b)
  exemption and the fact that entity IDs are not personal data
* **Option B** — Redact entity identifiers post-erasure by replacing them with tokens
  (e.g. `[REDACTED:account]`) and re-computing Merkle leaf hashes
* **Option C** — Exclude entity identifiers from `domainContentBytes()` entirely, so they
  never participate in the Merkle hash

## Decision Outcome

Chosen option: **Option A**, because:

1. AML record retention is an explicit legal obligation — Art.17(3)(b) applies directly
2. Entity identifiers are financial instrument references, not natural person identifiers
   — they fall outside GDPR personal data scope in most jurisdictions
3. The existing actor-token erasure mechanism already handles the personal data dimension
   (who performed the investigation) without touching the financial evidence chain
4. Redacting content (Option B) would require foundation-level `ContentRedactionService`
   with Merkle chain re-computation — substantial complexity for a questionable legal benefit
5. Excluding identifiers (Option C) would weaken the tamper-evidence property that FinCEN
   requires — the hash would no longer cover the financial facts of the investigation

### Contingency

If legal review determines that Art.17(3)(b) does not fully cover AML investigation records
in a specific jurisdiction, or if entity identifiers are reclassified as personal data:

- A content redaction layer will be needed (Option B)
- This is tracked as casehubio/aml#127
- The redaction design is documented in `docs/specs/issue-7-gdpr-regulatory-audit/2026-08-17-entity-data-erasure-exemption-design.md`

### Affected Entries

| Entry class | Fields in `domainContentBytes()` | Personal data? |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `transactionId`, `originAccountId`, `destinationAccountId` | No — financial instrument references |
| `AmlEntityErasureLedgerEntry` | `erasedEntityId`, `erasureReason`, `memoriesErased` | No — `erasedEntityId` is the entity reference being erased, not a person identifier |
| `AmlCaseProfileLedgerEntry` | `flagReason`, `transactionAmount`, `priorIncidentCount`, ... | No — investigation metadata, no entity IDs |
| `AmlSarOfficerReviewedLedgerEntry` | `reviewDecision`, `rejectionReason` | No — decision record |
| `AmlComplianceReviewLedgerEntry` | `taskId` | No — WorkItem reference |
| `AmlTrustRoutingAttestation` | `capabilityTag`, `selectedWorkerId`, routing scores | No — agent routing data |
| `AmlSupervisorDecisionLedgerEntry` | `selectedBindings`, `rationale`, ... | No — supervisor decision record |
| `AmlCbrAdvisoryLedgerEntry` | similarity stats, capabilities | No — CBR advisory data |

### Positive Consequences

* Merkle chain integrity is preserved — no re-computation needed after erasure
* Tamper-evidence covers the full investigation record including financial facts
* No foundation changes required — existing `LedgerErasureService` handles personal data

### Negative Consequences / Tradeoffs

* If the Art.17(3)(b) exemption is challenged in a specific jurisdiction, a content
  redaction layer must be built (casehubio/aml#127)
* Entity identifiers persist permanently in the Merkle chain — they cannot be removed
  without chain re-computation

## Links

* GDPR Art.17(3)(b) — exemption for legal obligation compliance
* Bank Secrecy Act 31 USC §5318 — AML record retention obligation
* EU 4th AML Directive 2015/849 Art.40 — five-year retention requirement
* FATF Recommendation 11 — record-keeping
* casehubio/aml#127 — contingency: entity data redaction if exemption fails
* casehubio/aml#7 — GDPR and regulatory audit epic
