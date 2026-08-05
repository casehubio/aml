# io.casehub.aml.api.model.GateMetrics

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Oversight gate metrics for AML investigations.
Aggregated from WorkItems with callerRef pattern matching gates.

## Fields

### `averageApprovalTimeSeconds` (`java.lang.Double`)

### `byActionType` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `totalGates` (`long`)

## Record Components

### `averageApprovalTimeSeconds` (`java.lang.Double`)

Average approval time in seconds (null if no completed gates)

### `byActionType` (`java.util.Map<java.lang.String,java.lang.Long>`)

Count by action type (e.g., "sar.filing", "account.restriction")

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

Count by WorkItem status (PENDING, COMPLETED, REJECTED, etc.)

### `totalGates` (`long`)

Total count of gates in the query window

## Constructors

### `public GateMetrics(long totalGates, java.util.Map<java.lang.String,java.lang.Long> byActionType, java.util.Map<java.lang.String,java.lang.Long> byStatus, java.lang.Double averageApprovalTimeSeconds)`

#### Parameters

- `totalGates` (`long`)
- `byActionType` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `averageApprovalTimeSeconds` (`java.lang.Double`)

## Methods

### `public java.lang.Double averageApprovalTimeSeconds()`

### `public java.util.Map<java.lang.String,java.lang.Long> byActionType()`

### `public java.util.Map<java.lang.String,java.lang.Long> byStatus()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public long totalGates()`
