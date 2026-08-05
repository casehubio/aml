# io.casehub.aml.domain.AmlActionType

**Package:** `io.casehub.aml.domain`

**Kind:** `enum`

Typed taxonomy of consequential AML actions that workers may declare as `PlannedAction`
before the engine advances the case. Each constant encodes its regulatory gate policy,
reversibility, candidate approver groups, reason string, and oversight scope.

<p>Classification logic lives in `AmlActionRiskClassifier`. This enum owns only the
data — keeping it pure Java with no framework dependencies so both api and app modules can use it.

<p>candidateGroups semantics (GE-20260607-326c7e): fewer entries = more restrictive in the
engine chain. SAR_FILING with ["aml-mlro"] (1 group) is the tightest gate in the system.

## Fields

### `OVERSIGHT_SCOPE` (`java.lang.String`)

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `gatePolicy` (`io.casehub.aml.domain.AmlActionType.GatePolicy`)

### `reason` (`java.lang.String`)

### `reversible` (`boolean`)

## Enum Constants

### `ACCOUNT_RESTRICTION` (`io.casehub.aml.domain.AmlActionType`)

### `ENTITY_LINK_CREATION` (`io.casehub.aml.domain.AmlActionType`)

### `INVESTIGATION_CLEARANCE` (`io.casehub.aml.domain.AmlActionType`)

### `LAW_ENFORCEMENT_REFERRAL` (`io.casehub.aml.domain.AmlActionType`)

### `SAR_FILING` (`io.casehub.aml.domain.AmlActionType`)

### `TRANSACTION_BLOCKING` (`io.casehub.aml.domain.AmlActionType`)

## Constructors

### `private AmlActionType(io.casehub.aml.domain.AmlActionType.GatePolicy gatePolicy, boolean reversible, java.util.List<java.lang.String> candidateGroups, java.lang.String reason)`

#### Parameters

- `gatePolicy` (`io.casehub.aml.domain.AmlActionType.GatePolicy`)
- `reversible` (`boolean`)
- `candidateGroups` (`java.util.List<java.lang.String>`)
- `reason` (`java.lang.String`)

## Methods

### `public java.lang.String actionType()`

Returns the PlannedAction actionType string: e.g. `SAR_FILING \u2192 "sar.filing"`.

### `public java.util.List<java.lang.String> candidateGroups()`

### `public java.time.Duration expiresIn()`

expiresIn is null — expiry policy is regulatory and configurable post-GA.

### `public static java.util.Optional<io.casehub.aml.domain.AmlActionType> fromActionType(java.lang.String actionType)`

Parses a `PlannedAction.actionType()` string back to the enum constant.
Uses stream filter — never throws on unrecognised or null input.

#### Parameters

- `actionType` (`java.lang.String`)

### `public io.casehub.aml.domain.AmlActionType.GatePolicy gatePolicy()`

### `public java.lang.String reason()`

### `public boolean reversible()`

### `public java.lang.String scope()`

### `public static io.casehub.aml.domain.AmlActionType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.aml.domain.AmlActionType[] values()`
