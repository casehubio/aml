# io.casehub.aml.api.model.TrustScoreSnapshotResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

REST response DTO for a single trust score snapshot.

## Fields

### `agentId` (`java.lang.String`)

### `alpha` (`double`)

### `beta` (`double`)

### `capability` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `score` (`double`)

### `snapshotTimestamp` (`java.time.Instant`)

## Record Components

### `agentId` (`java.lang.String`)

agent identifier (e.g. "sar-drafting-agent-senior")

### `alpha` (`double`)

Bayesian Beta alpha parameter at snapshot time

### `beta` (`double`)

Bayesian Beta beta parameter at snapshot time

### `capability` (`java.lang.String`)

capability tag (e.g. "sar-drafting")

### `id` (`java.util.UUID`)

snapshot identifier

### `score` (`double`)

pre-computed mean: alpha / (alpha + beta)

### `snapshotTimestamp` (`java.time.Instant`)

when the snapshot was captured

## Constructors

### `public TrustScoreSnapshotResponse(java.util.UUID id, java.lang.String agentId, java.lang.String capability, double alpha, double beta, double score, java.time.Instant snapshotTimestamp)`

#### Parameters

- `id` (`java.util.UUID`)
- `agentId` (`java.lang.String`)
- `capability` (`java.lang.String`)
- `alpha` (`double`)
- `beta` (`double`)
- `score` (`double`)
- `snapshotTimestamp` (`java.time.Instant`)

## Methods

### `public java.lang.String agentId()`

### `public double alpha()`

### `public double beta()`

### `public java.lang.String capability()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public double score()`

### `public java.time.Instant snapshotTimestamp()`

### `public final java.lang.String toString()`
