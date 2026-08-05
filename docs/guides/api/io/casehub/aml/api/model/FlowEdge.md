# io.casehub.aml.api.model.FlowEdge

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Directed edge in the investigation flow graph. `from \u2192 to` means
"worker at index `from` completed before worker at index `to` was scheduled."

## Fields

### `from` (`int`)

### `to` (`int`)

## Record Components

### `from` (`int`)

source node index (into `InvestigationFlowResponse.nodes()`)

### `to` (`int`)

target node index

## Constructors

### `public FlowEdge(int from, int to)`

#### Parameters

- `from` (`int`)
- `to` (`int`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int from()`

### `public final int hashCode()`

### `public int to()`

### `public final java.lang.String toString()`
