# io.casehub.aml.api.model.InvestigationFindingsResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Aggregates all specialist findings for an AML investigation.

<p>Each field wraps a specialist's output with execution status. A `PENDING`
status means the specialist hasn't executed yet. A `COMPLETED` status means
the specialist returned a result — which may still contain `declined=true`
if the agent declined due to insufficient clearance.

## Fields

### `entityResolution` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

### `osintScreening` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

### `patternAnalysis` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

### `sarNarrative` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

## Record Components

### `entityResolution` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

entity-resolution specialist finding

### `osintScreening` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

osint-screening specialist finding

### `patternAnalysis` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

pattern-analysis specialist finding

### `sarNarrative` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

sar-drafting specialist finding

## Constructors

### `public InvestigationFindingsResponse(io.casehub.aml.api.model.SpecialistFindingResponse entityResolution, io.casehub.aml.api.model.SpecialistFindingResponse patternAnalysis, io.casehub.aml.api.model.SpecialistFindingResponse osintScreening, io.casehub.aml.api.model.SpecialistFindingResponse sarNarrative)`

#### Parameters

- `entityResolution` (`io.casehub.aml.api.model.SpecialistFindingResponse`)
- `patternAnalysis` (`io.casehub.aml.api.model.SpecialistFindingResponse`)
- `osintScreening` (`io.casehub.aml.api.model.SpecialistFindingResponse`)
- `sarNarrative` (`io.casehub.aml.api.model.SpecialistFindingResponse`)

## Methods

### `public io.casehub.aml.api.model.SpecialistFindingResponse entityResolution()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.aml.api.model.SpecialistFindingResponse osintScreening()`

### `public io.casehub.aml.api.model.SpecialistFindingResponse patternAnalysis()`

### `public io.casehub.aml.api.model.SpecialistFindingResponse sarNarrative()`

### `public final java.lang.String toString()`
