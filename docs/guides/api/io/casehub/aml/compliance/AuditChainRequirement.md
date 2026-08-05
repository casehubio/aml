# io.casehub.aml.compliance.AuditChainRequirement

**Package:** `io.casehub.aml.compliance`

**Kind:** `record`

## Fields

### `CITATION` (`java.lang.String`)

### `MECHANISM` (`java.lang.String`)

### `REQUIREMENT_ID` (`java.lang.String`)

### `chainVerified` (`boolean`)

### `citation` (`java.lang.String`)

### `events` (`java.util.List<io.casehub.aml.compliance.LedgerEventRecord>`)

### `id` (`java.lang.String`)

### `mechanism` (`java.lang.String`)

### `status` (`RequirementStatus`)

### `treeRoot` (`java.lang.String`)

## Record Components

### `chainVerified` (`boolean`)

### `citation` (`java.lang.String`)

### `events` (`java.util.List<io.casehub.aml.compliance.LedgerEventRecord>`)

### `id` (`java.lang.String`)

### `mechanism` (`java.lang.String`)

### `status` (`RequirementStatus`)

### `treeRoot` (`java.lang.String`)

## Constructors

### `public AuditChainRequirement(java.lang.String id, java.lang.String citation, java.lang.String mechanism, RequirementStatus status, java.lang.String treeRoot, boolean chainVerified, java.util.List<io.casehub.aml.compliance.LedgerEventRecord> events)`

#### Parameters

- `id` (`java.lang.String`)
- `citation` (`java.lang.String`)
- `mechanism` (`java.lang.String`)
- `status` (`RequirementStatus`)
- `treeRoot` (`java.lang.String`)
- `chainVerified` (`boolean`)
- `events` (`java.util.List<io.casehub.aml.compliance.LedgerEventRecord>`)

## Methods

### `public boolean chainVerified()`

### `public java.lang.String citation()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.aml.compliance.LedgerEventRecord> events()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public java.lang.String mechanism()`

### `public RequirementStatus status()`

### `public final java.lang.String toString()`

### `public java.lang.String treeRoot()`
