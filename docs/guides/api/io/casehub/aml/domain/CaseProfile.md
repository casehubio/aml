# io.casehub.aml.domain.CaseProfile

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `entityType` (`io.casehub.aml.domain.EntityType`)

### `flagReason` (`io.casehub.aml.domain.FlagReason`)

### `jurisdiction` (`io.casehub.aml.domain.JurisdictionRisk`)

### `network` (`io.casehub.aml.domain.NetworkComplexity`)

### `priorIncidentCount` (`int`)

### `transactionAmount` (`java.math.BigDecimal`)

## Record Components

### `entityType` (`io.casehub.aml.domain.EntityType`)

### `flagReason` (`io.casehub.aml.domain.FlagReason`)

### `jurisdiction` (`io.casehub.aml.domain.JurisdictionRisk`)

### `network` (`io.casehub.aml.domain.NetworkComplexity`)

### `priorIncidentCount` (`int`)

### `transactionAmount` (`java.math.BigDecimal`)

## Constructors

### `public CaseProfile(io.casehub.aml.domain.FlagReason flagReason, java.math.BigDecimal transactionAmount, int priorIncidentCount, io.casehub.aml.domain.EntityType entityType, io.casehub.aml.domain.JurisdictionRisk jurisdiction, io.casehub.aml.domain.NetworkComplexity network)`

#### Parameters

- `flagReason` (`io.casehub.aml.domain.FlagReason`)
- `transactionAmount` (`java.math.BigDecimal`)
- `priorIncidentCount` (`int`)
- `entityType` (`io.casehub.aml.domain.EntityType`)
- `jurisdiction` (`io.casehub.aml.domain.JurisdictionRisk`)
- `network` (`io.casehub.aml.domain.NetworkComplexity`)

## Methods

### `public static io.casehub.aml.domain.CaseProfile complete(io.casehub.aml.domain.FlagReason flagReason, java.math.BigDecimal transactionAmount, int priorIncidentCount, io.casehub.aml.domain.EntityType entityType, io.casehub.aml.domain.JurisdictionRisk jurisdiction, io.casehub.aml.domain.NetworkComplexity network)`

#### Parameters

- `flagReason` (`io.casehub.aml.domain.FlagReason`)
- `transactionAmount` (`java.math.BigDecimal`)
- `priorIncidentCount` (`int`)
- `entityType` (`io.casehub.aml.domain.EntityType`)
- `jurisdiction` (`io.casehub.aml.domain.JurisdictionRisk`)
- `network` (`io.casehub.aml.domain.NetworkComplexity`)

### `public io.casehub.aml.domain.EntityType entityType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.aml.domain.FlagReason flagReason()`

### `public final int hashCode()`

### `public static io.casehub.aml.domain.CaseProfile initial(io.casehub.aml.domain.FlagReason flagReason, java.math.BigDecimal transactionAmount, int priorIncidentCount)`

#### Parameters

- `flagReason` (`io.casehub.aml.domain.FlagReason`)
- `transactionAmount` (`java.math.BigDecimal`)
- `priorIncidentCount` (`int`)

### `public io.casehub.aml.domain.JurisdictionRisk jurisdiction()`

### `public io.casehub.aml.domain.NetworkComplexity network()`

### `public int priorIncidentCount()`

### `public java.util.Map<java.lang.String,FeatureValue> toFeatures()`

### `public final java.lang.String toString()`

### `public java.math.BigDecimal transactionAmount()`
