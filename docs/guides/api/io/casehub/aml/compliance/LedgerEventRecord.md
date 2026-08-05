# io.casehub.aml.compliance.LedgerEventRecord

**Package:** `io.casehub.aml.compliance`

**Kind:** `record`

## Fields

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `causedByEntryId` (`java.util.UUID`)

### `digest` (`java.lang.String`)

### `entryId` (`java.util.UUID`)

### `eventType` (`java.lang.String`)

### `inclusionProof` (`io.casehub.aml.compliance.AmlInclusionProof`)

### `occurredAt` (`java.time.Instant`)

## Record Components

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `causedByEntryId` (`java.util.UUID`)

### `digest` (`java.lang.String`)

### `entryId` (`java.util.UUID`)

### `eventType` (`java.lang.String`)

### `inclusionProof` (`io.casehub.aml.compliance.AmlInclusionProof`)

### `occurredAt` (`java.time.Instant`)

## Constructors

### `public LedgerEventRecord(java.util.UUID entryId, java.lang.String eventType, java.lang.String actorId, java.lang.String actorRole, java.time.Instant occurredAt, java.util.UUID causedByEntryId, java.lang.String digest, io.casehub.aml.compliance.AmlInclusionProof inclusionProof)`

#### Parameters

- `entryId` (`java.util.UUID`)
- `eventType` (`java.lang.String`)
- `actorId` (`java.lang.String`)
- `actorRole` (`java.lang.String`)
- `occurredAt` (`java.time.Instant`)
- `causedByEntryId` (`java.util.UUID`)
- `digest` (`java.lang.String`)
- `inclusionProof` (`io.casehub.aml.compliance.AmlInclusionProof`)

## Methods

### `public java.lang.String actorId()`

### `public java.lang.String actorRole()`

### `public java.util.UUID causedByEntryId()`

### `public java.lang.String digest()`

### `public java.util.UUID entryId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String eventType()`

### `public final int hashCode()`

### `public io.casehub.aml.compliance.AmlInclusionProof inclusionProof()`

### `public java.time.Instant occurredAt()`

### `public final java.lang.String toString()`
