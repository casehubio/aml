# io.casehub.aml.compliance.SlaRequirement

**Package:** `io.casehub.aml.compliance`

**Kind:** `record`

## Fields

### `CITATION` (`java.lang.String`)

### `ESCALATION_POLICY` (`java.lang.String`)

### `MECHANISM` (`java.lang.String`)

### `REQUIREMENT_ID` (`java.lang.String`)

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `citation` (`java.lang.String`)

### `claimDeadline` (`java.time.Instant`)

### `completedAt` (`java.time.Instant`)

### `escalationPolicy` (`java.lang.String`)

### `id` (`java.lang.String`)

### `mechanism` (`java.lang.String`)

### `slaMet` (`boolean`)

### `status` (`RequirementStatus`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `citation` (`java.lang.String`)

### `claimDeadline` (`java.time.Instant`)

### `completedAt` (`java.time.Instant`)

### `escalationPolicy` (`java.lang.String`)

### `id` (`java.lang.String`)

### `mechanism` (`java.lang.String`)

### `slaMet` (`boolean`)

### `status` (`RequirementStatus`)

### `workItemId` (`java.util.UUID`)

## Constructors

### `public SlaRequirement(java.lang.String id, java.lang.String citation, java.lang.String mechanism, RequirementStatus status, java.util.UUID workItemId, java.time.Instant claimDeadline, java.time.Instant completedAt, boolean slaMet, java.util.List<java.lang.String> candidateGroups, java.lang.String escalationPolicy)`

#### Parameters

- `id` (`java.lang.String`)
- `citation` (`java.lang.String`)
- `mechanism` (`java.lang.String`)
- `status` (`RequirementStatus`)
- `workItemId` (`java.util.UUID`)
- `claimDeadline` (`java.time.Instant`)
- `completedAt` (`java.time.Instant`)
- `slaMet` (`boolean`)
- `candidateGroups` (`java.util.List<java.lang.String>`)
- `escalationPolicy` (`java.lang.String`)

## Methods

### `public java.util.List<java.lang.String> candidateGroups()`

### `public java.lang.String citation()`

### `public java.time.Instant claimDeadline()`

### `public java.time.Instant completedAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String escalationPolicy()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public java.lang.String mechanism()`

### `public boolean slaMet()`

### `public RequirementStatus status()`

### `public final java.lang.String toString()`

### `public java.util.UUID workItemId()`
