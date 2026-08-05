# io.casehub.aml.domain.AmlInvestigationResult

**Package:** `io.casehub.aml.domain`

**Kind:** `record`

## Fields

### `caseId` (`java.util.UUID`)

### `complianceReviewTaskId` (`java.lang.String`)

### `ledgerCaseEntryId` (`java.util.UUID`)

### `summary` (`io.casehub.aml.domain.InvestigationSummary`)

## Record Components

### `caseId` (`java.util.UUID`)

### `complianceReviewTaskId` (`java.lang.String`)

### `ledgerCaseEntryId` (`java.util.UUID`)

### `summary` (`io.casehub.aml.domain.InvestigationSummary`)

## Constructors

### `public AmlInvestigationResult(io.casehub.aml.domain.InvestigationSummary summary, java.lang.String complianceReviewTaskId)`

Layer 1/2 compat constructor — no ledger fields.

#### Parameters

- `summary` (`io.casehub.aml.domain.InvestigationSummary`)
- `complianceReviewTaskId` (`java.lang.String`)

### `public AmlInvestigationResult(io.casehub.aml.domain.InvestigationSummary summary, java.lang.String complianceReviewTaskId, java.util.UUID caseId, java.util.UUID ledgerCaseEntryId)`

#### Parameters

- `summary` (`io.casehub.aml.domain.InvestigationSummary`)
- `complianceReviewTaskId` (`java.lang.String`)
- `caseId` (`java.util.UUID`)
- `ledgerCaseEntryId` (`java.util.UUID`)

## Methods

### `public java.util.UUID caseId()`

### `public java.lang.String complianceReviewTaskId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID ledgerCaseEntryId()`

### `public io.casehub.aml.domain.InvestigationSummary summary()`

### `public final java.lang.String toString()`
