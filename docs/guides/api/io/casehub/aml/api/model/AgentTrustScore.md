# io.casehub.aml.api.model.AgentTrustScore

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Trust score for a single agent-capability pair.

## Fields

### `agentId` (`java.lang.String`)

### `capabilityTag` (`java.lang.String`)

### `score` (`java.lang.Double`)

## Record Components

### `agentId` (`java.lang.String`)

Agent identifier (e.g., "sar-drafting-agent-senior")

### `capabilityTag` (`java.lang.String`)

Capability tag (e.g., "sar-drafting")

### `score` (`java.lang.Double`)

Trust score (0.0 to 1.0), or null if no observations yet

## Constructors

### `public AgentTrustScore(java.lang.String agentId, java.lang.String capabilityTag, java.lang.Double score)`

#### Parameters

- `agentId` (`java.lang.String`)
- `capabilityTag` (`java.lang.String`)
- `score` (`java.lang.Double`)

## Methods

### `public java.lang.String agentId()`

### `public java.lang.String capabilityTag()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double score()`

### `public final java.lang.String toString()`
