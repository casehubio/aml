# io.casehub.aml.api.model.InvestigationFlowResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Investigation flow graph for visualization. Reconstructs the directed acyclic graph
of specialist workers dispatched during an AML investigation, showing:
<ul>
  <li>Execution order (nodes in temporal sequence)</li>
  <li>Parallel groups (workers scheduled simultaneously)</li>
  <li>Trust scores at routing time</li>
  <li>Worker status (scheduled/completed/failed)</li>
</ul>

<p>Edge direction: `from \u2192 to` means "from completed before to was scheduled."
Parallel groups identify sets of node indices scheduled together with no dependency
between them.

## Fields

### `edges` (`java.util.List<io.casehub.aml.api.model.FlowEdge>`)

### `nodes` (`java.util.List<io.casehub.aml.api.model.FlowNode>`)

### `parallelGroups` (`java.util.List<java.util.List<java.lang.Integer>>`)

## Record Components

### `edges` (`java.util.List<io.casehub.aml.api.model.FlowEdge>`)

directed edges showing completion-to-schedule dependencies

### `nodes` (`java.util.List<io.casehub.aml.api.model.FlowNode>`)

sequential list of workers in temporal dispatch order

### `parallelGroups` (`java.util.List<java.util.List<java.lang.Integer>>`)

groups of node indices scheduled in parallel (each group is a list of indices)

## Constructors

### `public InvestigationFlowResponse(java.util.List<io.casehub.aml.api.model.FlowNode> nodes, java.util.List<io.casehub.aml.api.model.FlowEdge> edges, java.util.List<java.util.List<java.lang.Integer>> parallelGroups)`

#### Parameters

- `nodes` (`java.util.List<io.casehub.aml.api.model.FlowNode>`)
- `edges` (`java.util.List<io.casehub.aml.api.model.FlowEdge>`)
- `parallelGroups` (`java.util.List<java.util.List<java.lang.Integer>>`)

## Methods

### `public java.util.List<io.casehub.aml.api.model.FlowEdge> edges()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.aml.api.model.FlowNode> nodes()`

### `public java.util.List<java.util.List<java.lang.Integer>> parallelGroups()`

### `public final java.lang.String toString()`
