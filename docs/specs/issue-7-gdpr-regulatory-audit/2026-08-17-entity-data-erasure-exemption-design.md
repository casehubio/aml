# Contingency Design — Entity Data Redaction in Tamper-Evident Ledger Content

Date: 2026-08-17
Issue: casehubio/aml#127
ADR: [0004 — GDPR Art.17(3)(b) exemption](../../adr/0004-entity-data-retention-exemption.md)

## Status

**Contingency — not active.** This spec documents the design that would be needed if the
Art.17(3)(b) exemption documented in ADR-0004 does not hold in a specific jurisdiction.
No implementation is required unless a trigger condition is met.

## Trigger Conditions

1. Legal review determines Art.17(3)(b) does not cover AML investigation records in a target jurisdiction
2. A persistent `CaseContextStore` is introduced that stores entity identifiers alongside personal data
3. Regulatory guidance reclassifies financial entity identifiers (account IDs, transaction IDs) as personal data

## Problem

When the exemption holds, entity identifiers in `domainContentBytes()` are retained permanently
in the Merkle chain. If the exemption is invalidated, these identifiers must be redactable
without breaking chain verifiability — two properties that are inherently in tension.

### Affected `domainContentBytes()` Implementations

| Entry | Fields requiring redaction | Current content |
|---|---|---|
| `AmlCaseOpenedLedgerEntry` | `originAccountId`, `destinationAccountId`, `transactionId` | `transactionId\|originAccountId\|destinationAccountId` |
| `AmlEntityErasureLedgerEntry` | `erasedEntityId` | `erasedEntityId\|erasureReason\|memoriesErased` |

Other entry classes (`AmlCaseProfileLedgerEntry`, `AmlSarOfficerReviewedLedgerEntry`,
`AmlComplianceReviewLedgerEntry`, `AmlTrustRoutingAttestation`, `AmlSupervisorDecisionLedgerEntry`,
`AmlCbrAdvisoryLedgerEntry`) do not contain entity identifiers — no redaction needed.

## Design

### Approach: Redaction Tokens with Chain Re-computation

Replace entity identifiers in `domainContentBytes()` with deterministic redaction tokens,
then re-compute the Merkle leaf hash for the affected entry and propagate the change up the tree.

#### Step 1 — Redaction Token Format

```
[REDACTED:<field-type>:<deterministic-hash>]
```

- `field-type`: `account`, `transaction`, `entity` — identifies what was redacted
- `deterministic-hash`: `SHA-256(original-value | erasure-salt)` truncated to 8 hex chars — allows
  cross-reference detection (same value redacted in multiple entries produces the same token)
  without revealing the original value

Example: `TXN-2024-001` becomes `[REDACTED:transaction:a3f8c91b]`

#### Step 2 — Content Redaction Service (foundation level)

A new `ContentRedactionService` SPI in `casehub-ledger-api`:

```java
public interface ContentRedactionService {
    byte[] redact(byte[] originalDomainContent, RedactionRequest request);
}
```

`RedactionRequest` carries the field positions and replacement tokens. The AML implementation
maps field positions from the pipe-delimited format.

#### Step 3 — Merkle Chain Re-computation

After redacting `domainContentBytes()`:

1. Re-compute `canonicalBytes()` for the affected entry (base fields are unchanged; only domain content changes)
2. Re-compute the leaf hash: `SHA-256(0x00 | newCanonicalBytes)`
3. Re-compute all intermediate hashes up to the Merkle root
4. Store the new root — the old root is retained in an `AuditRedactionEntry` for forensic tracing

This breaks independent verifiability of the pre-redaction chain state. Verifiers must
accept the redacted chain as the canonical state post-erasure.

#### Step 4 — Redaction Audit Entry

Write an `AmlRedactionLedgerEntry` to the chain recording:

- Which entry was redacted (by ID)
- Which fields were redacted (by position)
- The erasure request that triggered it
- The pre-redaction Merkle root (for forensic comparison)

This entry is itself tamper-evident — it participates in the post-redaction chain.

### Alternative: Chameleon Hash (Considered and Rejected)

Chameleon hashes allow content modification without changing the root hash. Rejected because:

- Introduces a trapdoor key — whoever holds it can modify any entry silently
- Undermines the FinCEN tamper-evidence requirement (the whole point of the Merkle chain)
- The audit trail of what was redacted is more valuable than preserving the illusion of an unchanged chain

### Alternative: Exclude Entity IDs from Hash (Considered and Rejected)

Removing entity identifiers from `domainContentBytes()` proactively (before any legal challenge)
would weaken the tamper-evidence property — the hash would no longer cover the financial facts.
This trades away a guaranteed property (chain integrity) for a speculative risk (legal challenge).

## Impact Assessment

| Concern | Impact |
|---|---|
| Foundation changes | New `ContentRedactionService` SPI + Merkle re-computation in `casehub-ledger` |
| AML changes | Redaction mapping for `AmlCaseOpenedLedgerEntry` and `AmlEntityErasureLedgerEntry` |
| Chain verifiability | Post-redaction chain is verifiable; pre-redaction state is not reconstructible |
| Performance | Re-computation is O(log N) per affected entry — acceptable for per-request erasure |
| Existing erasure flow | Unchanged — actor-token erasure and content redaction are orthogonal |

## Implementation Estimate

Scale: M — two entry classes, one foundation SPI, Merkle re-computation
Complexity: High — Merkle chain modification is subtle; off-by-one in tree traversal
corrupts the entire chain

## Decision Log

No decisions captured yet — this spec activates only when a trigger condition is met.
