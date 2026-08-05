# io.casehub.aml.triage.CbrAdjuster

**Package:** `io.casehub.aml.triage`

**Kind:** `class`

## Fields

### `LOG` (`java.util.logging.Logger`)

### `maxAdjustment` (`double`)

### `minConfidence` (`double`)

## Constructors

### `public CbrAdjuster(double maxAdjustment, double minConfidence)`

#### Parameters

- `maxAdjustment` (`double`)
- `minConfidence` (`double`)

## Methods

### `public io.casehub.aml.triage.CbrAdjuster.AdjustedThresholds adjust(double sarThreshold, double fpThreshold, io.casehub.aml.domain.CbrPathAdvice cbr)`

#### Parameters

- `sarThreshold` (`double`)
- `fpThreshold` (`double`)
- `cbr` (`io.casehub.aml.domain.CbrPathAdvice`)
