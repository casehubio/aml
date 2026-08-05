# io.casehub.aml.domain.FailureContext

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `failureEvents` (`java.util.List<io.casehub.aml.domain.FailureEvent>`)

### `occurredAt` (`java.time.Instant`)

### `triggerGoalKind` (`java.lang.String`)

### `triggerGoalName` (`java.lang.String`)

## Record Components

### `failureEvents` (`java.util.List<io.casehub.aml.domain.FailureEvent>`)

### `occurredAt` (`java.time.Instant`)

### `triggerGoalKind` (`java.lang.String`)

### `triggerGoalName` (`java.lang.String`)

## Constructors

### `public FailureContext(java.lang.String triggerGoalName, java.lang.String triggerGoalKind, java.util.List<io.casehub.aml.domain.FailureEvent> failureEvents, java.time.Instant occurredAt)`

#### Parameters

- `triggerGoalName` (`java.lang.String`)
- `triggerGoalKind` (`java.lang.String`)
- `failureEvents` (`java.util.List<io.casehub.aml.domain.FailureEvent>`)
- `occurredAt` (`java.time.Instant`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.aml.domain.FailureEvent> failureEvents()`

### `public final int hashCode()`

### `public java.time.Instant occurredAt()`

### `public final java.lang.String toString()`

### `public java.lang.String triggerGoalKind()`

### `public java.lang.String triggerGoalName()`
