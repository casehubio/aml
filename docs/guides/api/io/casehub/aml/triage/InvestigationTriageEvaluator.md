# io.casehub.aml.triage.InvestigationTriageEvaluator

**Package:** `io.casehub.aml.triage`

**Kind:** `class`

## Fields

### `cbrAdjuster` (`io.casehub.aml.triage.CbrAdjuster`)

### `fpThreshold` (`double`)

### `hardGateEvaluator` (`io.casehub.aml.triage.HardGateEvaluator`)

### `riskScorer` (`io.casehub.aml.triage.RiskScorer`)

### `sarThreshold` (`double`)

## Constructors

### `public InvestigationTriageEvaluator(double sarThreshold, double fpThreshold, double maxCbrAdjustment, double cbrMinConfidence)`

#### Parameters

- `sarThreshold` (`double`)
- `fpThreshold` (`double`)
- `maxCbrAdjustment` (`double`)
- `cbrMinConfidence` (`double`)

## Methods

### `public io.casehub.aml.domain.TriageResult evaluate(io.casehub.aml.domain.TriageInput input)`

#### Parameters

- `input` (`io.casehub.aml.domain.TriageInput`)
