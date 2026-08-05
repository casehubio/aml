# io.casehub.aml.compliance.ComplianceEvidence

**Package:** `io.casehub.aml.compliance`

**Kind:** `record`

## Fields

### `auditChain` (`io.casehub.aml.compliance.AuditChainRequirement`)

### `caseId` (`java.util.UUID`)

### `gdprErasure` (`io.casehub.aml.compliance.GdprErasureRequirement`)

### `generatedAt` (`java.time.Instant`)

### `signature` (`java.lang.String`)

### `sla` (`io.casehub.aml.compliance.SlaRequirement`)

### `trustRouting` (`TrustRoutingRequirement`)

## Record Components

### `auditChain` (`io.casehub.aml.compliance.AuditChainRequirement`)

### `caseId` (`java.util.UUID`)

### `gdprErasure` (`io.casehub.aml.compliance.GdprErasureRequirement`)

### `generatedAt` (`java.time.Instant`)

### `signature` (`java.lang.String`)

### `sla` (`io.casehub.aml.compliance.SlaRequirement`)

### `trustRouting` (`TrustRoutingRequirement`)

## Constructors

### `public ComplianceEvidence(java.util.UUID caseId, java.time.Instant generatedAt, io.casehub.aml.compliance.AuditChainRequirement auditChain, io.casehub.aml.compliance.SlaRequirement sla, TrustRoutingRequirement trustRouting, io.casehub.aml.compliance.GdprErasureRequirement gdprErasure, java.lang.String signature)`

#### Parameters

- `caseId` (`java.util.UUID`)
- `generatedAt` (`java.time.Instant`)
- `auditChain` (`io.casehub.aml.compliance.AuditChainRequirement`)
- `sla` (`io.casehub.aml.compliance.SlaRequirement`)
- `trustRouting` (`TrustRoutingRequirement`)
- `gdprErasure` (`io.casehub.aml.compliance.GdprErasureRequirement`)
- `signature` (`java.lang.String`)

## Methods

### `public io.casehub.aml.compliance.AuditChainRequirement auditChain()`

### `public java.util.UUID caseId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.aml.compliance.GdprErasureRequirement gdprErasure()`

### `public java.time.Instant generatedAt()`

### `public final int hashCode()`

### `public java.lang.String signature()`

### `public io.casehub.aml.compliance.SlaRequirement sla()`

### `public final java.lang.String toString()`

### `public TrustRoutingRequirement trustRouting()`
