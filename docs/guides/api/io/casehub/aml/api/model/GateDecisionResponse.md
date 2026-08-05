# io.casehub.aml.api.model.GateDecisionResponse

**Package:** `io.casehub.aml.api.model`

**Kind:** `record`

Details of a single oversight gate decision.
Each gate represents a `PlannedAction` that requires approval before
the engine can execute it.

## Fields

### `actionType` (`java.lang.String`)

### `approvedAt` (`java.time.Instant`)

### `approvedBy` (`java.lang.String`)

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `description` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `gatePolicy` (`java.lang.String`)

### `reversible` (`boolean`)

### `status` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `actionType` (`java.lang.String`)

Action type string from `PlannedAction.actionType()`
                  (e.g., "sar.filing", "account.restriction")

### `approvedAt` (`java.time.Instant`)

Timestamp of approval/rejection (null if still pending)

### `approvedBy` (`java.lang.String`)

Actor who approved/rejected the gate (null if still pending)

### `candidateGroups` (`java.util.List<java.lang.String>`)

Approver groups (e.g., ["aml-mlro"], ["aml-compliance"])

### `description` (`java.lang.String`)

Human-readable description of the action

### `expiresAt` (`java.time.Instant`)

Gate expiry deadline (null if no expiry)

### `gatePolicy` (`java.lang.String`)

Gate policy name (ALWAYS, RISK_SCORE_THRESHOLD, CONFIDENCE_THRESHOLD)

### `reversible` (`boolean`)

Whether the action can be reversed after execution

### `status` (`java.lang.String`)

WorkItem status (PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, REJECTED, etc.)

### `workItemId` (`java.util.UUID`)

WorkItem ID for this gate (used for approval/rejection)

## Constructors

### `public GateDecisionResponse(java.util.UUID workItemId, java.lang.String actionType, java.lang.String gatePolicy, boolean reversible, java.lang.String description, java.util.List<java.lang.String> candidateGroups, java.lang.String status, java.lang.String approvedBy, java.time.Instant approvedAt, java.time.Instant expiresAt)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actionType` (`java.lang.String`)
- `gatePolicy` (`java.lang.String`)
- `reversible` (`boolean`)
- `description` (`java.lang.String`)
- `candidateGroups` (`java.util.List<java.lang.String>`)
- `status` (`java.lang.String`)
- `approvedBy` (`java.lang.String`)
- `approvedAt` (`java.time.Instant`)
- `expiresAt` (`java.time.Instant`)

## Methods

### `public java.lang.String actionType()`

### `public java.time.Instant approvedAt()`

### `public java.lang.String approvedBy()`

### `public java.util.List<java.lang.String> candidateGroups()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant expiresAt()`

### `public java.lang.String gatePolicy()`

### `public final int hashCode()`

### `public boolean reversible()`

### `public java.lang.String status()`

### `public final java.lang.String toString()`

### `public java.util.UUID workItemId()`
