# io.casehub.aml.domain.InvestigationSummaryResponse

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `amount` (`java.math.BigDecimal`)

### `caseId` (`java.util.UUID`)

### `createdAt` (`java.time.Instant`)

### `currency` (`java.lang.String`)

### `destinationAccount` (`java.lang.String`)

### `flagReason` (`java.lang.String`)

### `originAccount` (`java.lang.String`)

### `outcomeType` (`java.lang.String`)

### `status` (`java.lang.String`)

### `transactionId` (`java.lang.String`)

## Record Components

### `amount` (`java.math.BigDecimal`)

### `caseId` (`java.util.UUID`)

### `createdAt` (`java.time.Instant`)

### `currency` (`java.lang.String`)

### `destinationAccount` (`java.lang.String`)

### `flagReason` (`java.lang.String`)

### `originAccount` (`java.lang.String`)

### `outcomeType` (`java.lang.String`)

### `status` (`java.lang.String`)

### `transactionId` (`java.lang.String`)

## Constructors

### `public InvestigationSummaryResponse(java.util.UUID caseId, java.lang.String status, java.lang.String outcomeType, java.lang.String transactionId, java.lang.String originAccount, java.lang.String destinationAccount, java.math.BigDecimal amount, java.lang.String currency, java.lang.String flagReason, java.time.Instant createdAt)`

#### Parameters

- `caseId` (`java.util.UUID`)
- `status` (`java.lang.String`)
- `outcomeType` (`java.lang.String`)
- `transactionId` (`java.lang.String`)
- `originAccount` (`java.lang.String`)
- `destinationAccount` (`java.lang.String`)
- `amount` (`java.math.BigDecimal`)
- `currency` (`java.lang.String`)
- `flagReason` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.math.BigDecimal amount()`

### `public java.util.UUID caseId()`

### `public java.time.Instant createdAt()`

### `public java.lang.String currency()`

### `public java.lang.String destinationAccount()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String flagReason()`

### `public final int hashCode()`

### `public java.lang.String originAccount()`

### `public java.lang.String outcomeType()`

### `public java.lang.String status()`

### `public final java.lang.String toString()`

### `public java.lang.String transactionId()`
