# io.casehub.aml.api.model.FlowNode

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

A single specialist worker node in the investigation flow graph.

## Fields

### `capabilityTag` (`java.lang.String`)

### `status` (`java.lang.String`)

### `timestamp` (`java.time.Instant`)

### `trustScoreAtRouting` (`java.lang.Double`)

### `workerId` (`java.lang.String`)

## Record Components

### `capabilityTag` (`java.lang.String`)

the capability this worker provides (e.g., "entity-resolution", "osint-screening")

### `status` (`java.lang.String`)

worker status: "scheduled", "completed", or "failed"

### `timestamp` (`java.time.Instant`)

when the worker was scheduled

### `trustScoreAtRouting` (`java.lang.Double`)

trust score at the time of routing (null if not trust-routed or attestation missing)

### `workerId` (`java.lang.String`)

the selected worker ID (e.g., "osint-screening-agent-senior")

## Constructors

### `public FlowNode(java.lang.String capabilityTag, java.lang.String workerId, java.lang.Double trustScoreAtRouting, java.lang.String status, java.time.Instant timestamp)`

#### Parameters

- `capabilityTag` (`java.lang.String`)
- `workerId` (`java.lang.String`)
- `trustScoreAtRouting` (`java.lang.Double`)
- `status` (`java.lang.String`)
- `timestamp` (`java.time.Instant`)

## Methods

### `public java.lang.String capabilityTag()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String status()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`

### `public java.lang.Double trustScoreAtRouting()`

### `public java.lang.String workerId()`
