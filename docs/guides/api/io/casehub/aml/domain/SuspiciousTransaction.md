# io.casehub.aml.domain.SuspiciousTransaction

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `amount` (`java.math.BigDecimal`)

### `currency` (`java.lang.String`)

### `destinationAccountId` (`java.lang.String`)

### `flagReason` (`io.casehub.aml.domain.FlagReason`)

### `id` (`java.lang.String`)

### `originAccountId` (`java.lang.String`)

### `timestamp` (`java.time.Instant`)

## Record Components

### `amount` (`java.math.BigDecimal`)

### `currency` (`java.lang.String`)

### `destinationAccountId` (`java.lang.String`)

### `flagReason` (`io.casehub.aml.domain.FlagReason`)

### `id` (`java.lang.String`)

### `originAccountId` (`java.lang.String`)

### `timestamp` (`java.time.Instant`)

## Constructors

### `public SuspiciousTransaction(java.lang.String id, java.lang.String originAccountId, java.lang.String destinationAccountId, java.math.BigDecimal amount, java.lang.String currency, java.time.Instant timestamp, io.casehub.aml.domain.FlagReason flagReason)`

#### Parameters

- `id` (`java.lang.String`)
- `originAccountId` (`java.lang.String`)
- `destinationAccountId` (`java.lang.String`)
- `amount` (`java.math.BigDecimal`)
- `currency` (`java.lang.String`)
- `timestamp` (`java.time.Instant`)
- `flagReason` (`io.casehub.aml.domain.FlagReason`)

## Methods

### `public java.math.BigDecimal amount()`

### `public java.lang.String currency()`

### `public java.lang.String destinationAccountId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.aml.domain.FlagReason flagReason()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public java.lang.String originAccountId()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`
