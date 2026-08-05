# io.casehub.aml.api.model.ThroughputMetrics

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Throughput metrics for AML investigations.
Aggregated from `InvestigationSummaryView` by status, flag reason, and outcome.

## Fields

### `byFlagReason` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `byOutcomeType` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `totalInvestigations` (`long`)

## Record Components

### `byFlagReason` (`java.util.Map<java.lang.String,java.lang.Long>`)

Count by flag reason (e.g., "high-risk-jurisdiction", "velocity-anomaly")

### `byOutcomeType` (`java.util.Map<java.lang.String,java.lang.Long>`)

Count by outcome type (SAR_FILED, SAR_DECLINED, ESCALATED)

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

Count by investigation status (IN_PROGRESS, COMPLETED, CANCELLED)

### `totalInvestigations` (`long`)

Total count of investigations in the query window

## Constructors

### `public ThroughputMetrics(long totalInvestigations, java.util.Map<java.lang.String,java.lang.Long> byStatus, java.util.Map<java.lang.String,java.lang.Long> byFlagReason, java.util.Map<java.lang.String,java.lang.Long> byOutcomeType)`

#### Parameters

- `totalInvestigations` (`long`)
- `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `byFlagReason` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `byOutcomeType` (`java.util.Map<java.lang.String,java.lang.Long>`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.Long> byFlagReason()`

### `public java.util.Map<java.lang.String,java.lang.Long> byOutcomeType()`

### `public java.util.Map<java.lang.String,java.lang.Long> byStatus()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public long totalInvestigations()`
