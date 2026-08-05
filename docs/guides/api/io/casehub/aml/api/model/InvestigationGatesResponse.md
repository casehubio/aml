# io.casehub.aml.api.model.InvestigationGatesResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Response containing all gate decisions for an AML investigation.
Gates are oversight checkpoints where consequential actions (SAR filing,
account restriction, etc.) require human approval before the engine proceeds.

## Fields

### `gates` (`java.util.List<io.casehub.aml.api.model.GateDecisionResponse>`)

## Record Components

### `gates` (`java.util.List<io.casehub.aml.api.model.GateDecisionResponse>`)

List of gate decisions, ordered by creation time (oldest first)

## Constructors

### `public InvestigationGatesResponse(java.util.List<io.casehub.aml.api.model.GateDecisionResponse> gates)`

#### Parameters

- `gates` (`java.util.List<io.casehub.aml.api.model.GateDecisionResponse>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.aml.api.model.GateDecisionResponse> gates()`

### `public final int hashCode()`

### `public final java.lang.String toString()`
