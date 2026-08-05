# io.casehub.aml.api.model.SpecialistFindingResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Wraps a specialist finding with execution status.

<p><strong>Status values:</strong>
<ul>
  <li>`COMPLETED` — specialist executed and returned a result (may still contain
      `declined=true` in the result Map if the agent declined due to clearance)</li>
  <li>`PENDING` — specialist has not executed yet (result is null)</li>
</ul>

<p><strong>Result structure:</strong> The `result` field contains the raw Map written
by the worker to the CaseHub context. Each specialist writes different keys:
<ul>
  <li>entity-resolution: `entityId, ownershipChain, entityType, riskScore`</li>
  <li>pattern-analysis: `structuringDetected, description`</li>
  <li>osint-screening: `declined, reason, pepHit, sanctionsHit, screeningLevel`</li>
  <li>sar-drafting: `sarNarrative`</li>
</ul>

## Fields

### `result` (`java.lang.Object`)

### `status` (`java.lang.String`)

## Record Components

### `result` (`java.lang.Object`)

the data Map written by the worker (null when status is PENDING)

### `status` (`java.lang.String`)

execution status — "COMPLETED" | "PENDING"

## Constructors

### `public SpecialistFindingResponse(java.lang.String status, java.lang.Object result)`

#### Parameters

- `status` (`java.lang.String`)
- `result` (`java.lang.Object`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Object result()`

### `public java.lang.String status()`

### `public final java.lang.String toString()`
