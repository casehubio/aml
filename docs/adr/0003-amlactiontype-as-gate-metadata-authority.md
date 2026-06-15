# 0003 — AmlActionType enum as sole authority for gate metadata

Date: 2026-06-15
Status: Accepted

## Context and Problem Statement

The `AmlActionRiskClassifier` must return a `GateRequired` decision carrying metadata:
reason, reversibility, candidate approver groups, expiry duration, and oversight scope.
This metadata could live in the classifier logic itself (hardcoded), in a configuration
file, or in the domain type that identifies the action. With FinCEN/FCA regulatory
backing, these properties are not runtime-tunable — they are facts about the regulatory
regime that apply to every instance of the action type.

## Decision Drivers

* Fail-closed paths (missing context) must still produce correct gate metadata — a
  classifier that can't assess risk must still route to the right approver group
* Gate metadata is regulatory, not environmental — it must not be overridable at runtime
* The `AmlActionType` enum already carries the semantic identity of each action type;
  co-locating metadata keeps the domain model self-describing

## Considered Options

* **Option A** — Encode all gate metadata on `AmlActionType` enum constants
* **Option B** — Hardcode metadata in `AmlActionRiskClassifier` switch branches
* **Option C** — Externalise metadata to a configuration file or database

## Decision Outcome

Chosen option: **Option A**, because it ensures fail-closed paths read from the same
authoritative source as normal paths, and it keeps regulatory facts in the domain model
rather than scattered across classifier logic or externalised where they could be altered.

### Positive Consequences

* `missingContext()` paths in the classifier return fully-correct gate metadata without
  special-casing each action type
* Adding a new action type forces the author to supply all required metadata at compile time
* The enum is pure Java with no framework dependencies — usable in both `api` and `app` modules

### Negative Consequences / Tradeoffs

* Gate metadata is not runtime-configurable — requires a code change and deployment to
  update approver groups or reason strings (acceptable: these are regulatory, not operational)
* The enum grows in size as more action types are added (manageable at AML's scale)

## Pros and Cons of the Options

### Option A — Metadata on `AmlActionType` enum

* ✅ Fail-closed paths automatically get correct metadata
* ✅ Compile-time guarantee: no action type can be added without metadata
* ✅ Single source of truth — classifier reads type, not a parallel config structure
* ❌ Not runtime-configurable

### Option B — Hardcoded in classifier switch branches

* ✅ Simple to write initially
* ❌ Fail-closed paths require a parallel hardcoded lookup — duplication risk
* ❌ Adding a new action type requires changes in two places (enum + classifier)

### Option C — External configuration

* ✅ Runtime-configurable without deployment
* ❌ Regulatory metadata should not be changeable at runtime — this is a liability
* ❌ Configuration drift risk; increases operational complexity

## Links

* Protocol PP-20260610-66fc79 — fail-closed classifier metadata rule
* casehubio/aml#42 — Layer 9 ActionRiskClassifier implementation
