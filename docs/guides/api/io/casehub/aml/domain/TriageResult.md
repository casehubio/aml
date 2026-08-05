# io.casehub.aml.domain.TriageResult

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `cbrThresholdAdjustment` (`java.lang.Double`)

### `decision` (`io.casehub.aml.domain.TriageDecision`)

### `factors` (`java.util.List<io.casehub.aml.domain.RiskFactor>`)

### `hardGate` (`io.casehub.aml.domain.HardGate`)

### `reason` (`java.lang.String`)

### `riskScore` (`double`)

## Record Components

### `cbrThresholdAdjustment` (`java.lang.Double`)

### `decision` (`io.casehub.aml.domain.TriageDecision`)

### `factors` (`java.util.List<io.casehub.aml.domain.RiskFactor>`)

### `hardGate` (`io.casehub.aml.domain.HardGate`)

### `reason` (`java.lang.String`)

### `riskScore` (`double`)

## Constructors

### `public TriageResult(io.casehub.aml.domain.TriageDecision decision, java.lang.String reason, double riskScore, io.casehub.aml.domain.HardGate hardGate, java.lang.Double cbrThresholdAdjustment, java.util.List<io.casehub.aml.domain.RiskFactor> factors)`

#### Parameters

- `decision` (`io.casehub.aml.domain.TriageDecision`)
- `reason` (`java.lang.String`)
- `riskScore` (`double`)
- `hardGate` (`io.casehub.aml.domain.HardGate`)
- `cbrThresholdAdjustment` (`java.lang.Double`)
- `factors` (`java.util.List<io.casehub.aml.domain.RiskFactor>`)

## Methods

### `public java.lang.Double cbrThresholdAdjustment()`

### `public io.casehub.aml.domain.TriageDecision decision()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.aml.domain.RiskFactor> factors()`

### `public io.casehub.aml.domain.HardGate hardGate()`

### `public final int hashCode()`

### `public java.lang.String reason()`

### `public double riskScore()`

### `public final java.lang.String toString()`
