# io.casehub.aml.domain.InvestigationSummary

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `entityResolution` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.EntityResolutionResult>`)

### `osintScreening` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.OsintResult>`)

### `patternAnalysis` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.PatternAnalysisResult>`)

### `sarNarrative` (`java.lang.String`)

### `transaction` (`io.casehub.aml.domain.SuspiciousTransaction`)

## Record Components

### `entityResolution` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.EntityResolutionResult>`)

### `osintScreening` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.OsintResult>`)

### `patternAnalysis` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.PatternAnalysisResult>`)

### `sarNarrative` (`java.lang.String`)

### `transaction` (`io.casehub.aml.domain.SuspiciousTransaction`)

## Constructors

### `public InvestigationSummary(io.casehub.aml.domain.SuspiciousTransaction transaction, io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.EntityResolutionResult> entityResolution, io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.PatternAnalysisResult> patternAnalysis, io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.OsintResult> osintScreening, java.lang.String sarNarrative)`

#### Parameters

- `transaction` (`io.casehub.aml.domain.SuspiciousTransaction`)
- `entityResolution` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.EntityResolutionResult>`)
- `patternAnalysis` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.PatternAnalysisResult>`)
- `osintScreening` (`io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.OsintResult>`)
- `sarNarrative` (`java.lang.String`)

## Methods

### `public io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.EntityResolutionResult> entityResolution()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.OsintResult> osintScreening()`

### `public io.casehub.aml.domain.SpecialistOutcome<io.casehub.aml.domain.PatternAnalysisResult> patternAnalysis()`

### `public java.lang.String sarNarrative()`

### `public final java.lang.String toString()`

### `public io.casehub.aml.domain.SuspiciousTransaction transaction()`
