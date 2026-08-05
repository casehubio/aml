# io.casehub.aml.api.model.TrustScoreMetrics

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Trust score metrics for all known AML agents.
Fetched from `TrustScoreSource` SPI for agent/capability pairs
registered in the trust routing policy.

## Fields

### `scores` (`java.util.List<io.casehub.aml.api.model.AgentTrustScore>`)

## Record Components

### `scores` (`java.util.List<io.casehub.aml.api.model.AgentTrustScore>`)

Trust scores for each agent-capability pair

## Constructors

### `public TrustScoreMetrics(java.util.List<io.casehub.aml.api.model.AgentTrustScore> scores)`

#### Parameters

- `scores` (`java.util.List<io.casehub.aml.api.model.AgentTrustScore>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.aml.api.model.AgentTrustScore> scores()`

### `public final java.lang.String toString()`
