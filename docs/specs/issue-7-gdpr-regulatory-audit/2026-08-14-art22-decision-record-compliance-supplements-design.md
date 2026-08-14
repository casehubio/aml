# GDPR Art.22 Decision Record Compliance Supplements

**Issue:** #82 — feat: GDPR Art.22 decision record compliance supplements
**Branch:** `issue-7-gdpr-regulatory-audit`
**Date:** 2026-08-14
**Decisions:** D1–D5 in `decisions.md`

---

## Problem Statement

GDPR Art.22 requires that when automated decision-making significantly affects data subjects, the controller must provide:
- Meaningful information about the logic involved (Art.13(2)(f), Art.14(2)(g))
- The significance and envisaged consequences
- The right to obtain human intervention, express a point of view, and contest the decision (Art.22(3))

AML investigation triage is an automated decision that determines whether a person faces further scrutiny (SAR_WARRANTED vs INVESTIGATION_CLEARED). This decision is recorded in `AmlCaseProfileLedgerEntry` but carries no Art.22 decision transparency metadata — no algorithm reference, no rationale, no confidence score, no contestation mechanism.

The platform's `ComplianceSupplement` infrastructure already supports Art.22 fields (`algorithmRef`, `confidenceScore`, `decisionContext`, `rationale`, `contestationUri`, `humanOverrideAvailable`). The clinical-trial harness attaches supplements via a factory pattern (`ClinicalComplianceSupplement`) for EU AI Act Art.12 compliance. AML doesn't use supplements at all.

## Scope

**In scope:**
- Art.22 supplement attachment to triage decision entries
- PII sanitisation of decision context via existing `ContentSanitiser` SPI
- Art.22 compliance evidence section in `ComplianceEvidence` report

**Not in scope:**
- Agent routing decision supplements (D1: routing is operational, not Art.22-triggering)
- SAR narrative generation supplements (D1: advisory draft for human review)
- Foundation repo changes (D2/D5: `ContentSanitiser` already exists in ledger-runtime)
- Contestation workflow or endpoint (Art.22(3) contestation is a separate concern from Art.22(1) transparency; the supplement records the contestation URI but the workflow behind it is future work)

**Temporal trade-off (R1-02):** `AmlCaseProfileLedgerEntry` is written post-hoc on `CaseOutcomeEvent`, not at triage decision time. In AML, data subjects are informed after investigation completes (SAR filing). If mid-investigation Art.22 access becomes required, a dedicated triage-time ledger entry can be added. This is acknowledged, not blocked.

---

## Design

### Part 1: AmlComplianceSupplement Factory (app/ module)

New `AmlComplianceSupplement` in `io.casehub.aml.compliance`, following the clinical `ClinicalComplianceSupplement` factory pattern. Unlike clinical (static-only fields), AML populates dynamic fields from the triage context.

```java
package io.casehub.aml.compliance;

public final class AmlComplianceSupplement {

    private AmlComplianceSupplement() {}

    public static ComplianceSupplement triageDecision(
            String outcome, Double confidence, String investigationPath,
            String sanitisedDecisionContext) {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "GDPR Art.22(1) — automated decision-making transparency";
        s.algorithmRef = "AmlInvestigationTriageService (CBR-weighted rule-based triage)";
        s.confidenceScore = confidence;
        s.rationale = "Triage outcome: " + outcome + ". Path: " + investigationPath;
        s.decisionContext = sanitisedDecisionContext;
        s.humanOverrideAvailable = true;
        s.contestationUri = "/api/investigations/{caseId}/contestation";
        return s;
    }
}
```

**Field mapping:**

| ComplianceSupplement field | AML source | Value |
|---|---|---|
| `planRef` | static | `"GDPR Art.22(1) — automated decision-making transparency"` |
| `algorithmRef` | static | `"AmlInvestigationTriageService (CBR-weighted rule-based triage)"` |
| `confidenceScore` | `AmlCaseProfileLedgerEntry.confidence` | currently always null — placeholder for future triage confidence scoring |
| `rationale` | constructed from outcome + path | human-readable summary |
| `decisionContext` | snapshot fields, sanitised | JSON of triage inputs (flag reason, amount, entity type, jurisdiction, network complexity, prior incidents) |
| `humanOverrideAvailable` | static | `true` — compliance officer review gate is the human override |
| `contestationUri` | static | placeholder URI pattern — actual endpoint is future work |
| `evidence` | not used | null |
| `detail` | not used | null |

`humanOverrideAvailable` is unconditionally `true` because every AML investigation passes through a compliance officer review WorkItem (Layer 2) before any SAR is filed. The human-in-the-loop is structural, not optional.

### Part 2: PII Sanitisation (app/ module)

New `AmlContentSanitiser` in `io.casehub.aml.compliance`, implementing the existing `io.casehub.ledger.runtime.privacy.ContentSanitiser` SPI. Verify `PassThroughContentSanitiser` CDI registration before choosing displacement mechanism — the decompiled bytecode shows no CDI annotations; it may be registered via extension or `beans.xml`.

```java
@ApplicationScoped
public class AmlContentSanitiser implements ContentSanitiser {

    private static final Pattern IBAN_PATTERN =
        Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4,30}\\b");

    @Override
    public String sanitise(String decisionContextJson) {
        if (decisionContextJson == null) return null;
        return IBAN_PATTERN.matcher(decisionContextJson)
                .replaceAll("[REDACTED:account]");
    }
}
```

The regex targets IBAN patterns only. The original design also matched bare 8–17 digit numbers (`\\b\\d{8,17}\\b`), but this produces false positives on timestamps, reference numbers, and large amounts. Since the decision context JSON uses structured fields (not free text), and account IDs (`originAccountId`, `destinationAccountId`) are deliberately excluded from the decision context, the IBAN-only pattern provides sufficient coverage with no false positives. The sanitiser is defence-in-depth — the primary PII exclusion is in `buildDecisionContext()` itself.

The sanitiser runs at the call site in the observer, not automatically in the save pipeline. The observer builds the decision context JSON, sanitises it, then passes the sanitised string to `AmlComplianceSupplement.triageDecision()`.

### Part 3: Separate Art.22 CaseOutcomeObserver (app/ module)

New `AmlArt22SupplementObserver` in `io.casehub.aml.compliance` — a dedicated `CaseOutcomeObserver` with its own error boundary, independent of the CBR observer. Art.22 compliance and CBR case profiling are different concerns; coupling them means CBR-specific early exits or failures would silently skip Art.22 supplement attachment.

```java
@ApplicationScoped
public class AmlArt22SupplementObserver implements CaseOutcomeObserver {

    @Inject ContentSanitiser contentSanitiser;
    @Inject LedgerEntryRepository ledgerRepository;
    @Inject ObjectMapper objectMapper;

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        try {
            writeArt22Supplement(event);
        } catch (Exception e) {
            LOG.warnf(e, "Art.22 supplement write failed for caseId=%s", event.caseId());
        }
    }
}
```

The observer reads the triage decision from the snapshot (same data source as the CBR observer) and writes the Art.22 supplement on the `AmlCaseProfileLedgerEntry`. Since the CBR observer writes the profile entry first, this observer queries it by `subjectId = caseId` and attaches the supplement.

**Ordering:** Both observers fire on `CaseOutcomeEvent`. The CBR observer must write the `AmlCaseProfileLedgerEntry` before the Art.22 observer can attach a supplement to it. Use `@Priority` on both observers to enforce ordering: CBR observer at default priority, Art.22 observer at lower priority (higher number).

**Decision context construction** uses `ObjectMapper` (not `String.format`) to produce valid JSON:

```java
private String buildDecisionContext(CaseOutcomeEvent event) {
    var snapshot = event.caseFileSnapshot();
    var context = new LinkedHashMap<String, Object>();
    // Extract triage inputs from snapshot
    var triageMap = (Map<String, Object>) snapshot.get("investigationTriage");
    if (triageMap != null) {
        context.put("decision", triageMap.get("decision"));
    }
    var tx = snapshot.get("transaction");
    if (tx instanceof Map<?,?> txMap) {
        context.put("flagReason", txMap.get("flagReason"));
        context.put("amount", txMap.get("amount"));
        context.put("currency", txMap.get("currency"));
        // Deliberately exclude originAccountId, destinationAccountId — PII
    }
    var entityResolution = snapshot.get("entityResolution");
    if (entityResolution instanceof Map<?,?> erMap) {
        context.put("entityType", erMap.get("entityType"));
        context.put("jurisdictionRisk", erMap.get("jurisdictionRisk"));
        context.put("networkComplexity", erMap.get("networkComplexity"));
    }
    return objectMapper.writeValueAsString(context);
}
```

Account IDs (`originAccountId`, `destinationAccountId`) are deliberately excluded from the decision context (identifying data, not decision factors). The sanitiser provides defence-in-depth for any PII that leaks in via future field additions.

### Part 4: Art22DecisionRecordRequirement (api/ module)

New record in `io.casehub.aml.compliance`:

```java
public record Art22DecisionRecordRequirement(
        String id,
        String citation,
        String mechanism,
        RequirementStatus status,
        List<Art22DecisionRecord> decisions) {

    public static final String REQUIREMENT_ID = "GDPR-ART22-DECISION-RECORD";
    public static final String CITATION =
            "GDPR Art.22 — automated decision-making transparency and contestation rights";
    public static final String MECHANISM =
            "ComplianceSupplement attached to AmlCaseProfileLedgerEntry at case completion. " +
            "Records algorithm reference, confidence score, sanitised decision context, " +
            "and contestation URI. Human override available via compliance officer review gate.";
}
```

```java
public record Art22DecisionRecord(
        UUID entryId,
        String algorithmRef,
        Double confidenceScore,
        String rationale,
        boolean humanOverrideAvailable,
        String contestationUri,
        boolean decisionContextPresent) {}
```

`decisionContextPresent` is a boolean flag — the actual `decisionContext` string is not surfaced in the compliance evidence report (it may contain sanitised-but-still-sensitive triage data). The flag indicates whether the supplement has a non-null decision context attached.

**Status logic:**
- `CLOSED` — all `AmlCaseProfileLedgerEntry` records for the case have a `ComplianceSupplement` with `algorithmRef` and `humanOverrideAvailable` populated
- `PARTIAL` — some entries have supplements, or required fields are missing
- `GAP` — no triage entries have supplements

### Part 5: ComplianceEvidence Extension (api/ + app/ modules)

**api/ module:**

```java
public record ComplianceEvidence(
    UUID caseId,
    Instant generatedAt,
    AuditChainRequirement auditChain,
    SlaRequirement sla,
    TrustRoutingRequirement trustRouting,
    GdprErasureRequirement gdprErasure,
    Art22DecisionRecordRequirement art22DecisionRecord,
    String signature
) {}
```

New field added before `signature`. Wire format change — documented.

**app/ module — `AmlComplianceEvidenceService`:**

New method `buildArt22DecisionRecord(List<LedgerEntry> all)` — receives the pre-fetched entry list from `build()`, consistent with `buildAuditChain` and `buildSla` (avoids redundant `findBySubjectId` query):

```java
private Art22DecisionRecordRequirement buildArt22DecisionRecord(List<LedgerEntry> all) {
    List<AmlCaseProfileLedgerEntry> profileEntries = all.stream()
            .filter(AmlCaseProfileLedgerEntry.class::isInstance)
            .map(AmlCaseProfileLedgerEntry.class::cast)
            .toList();

    if (profileEntries.isEmpty()) {
        return new Art22DecisionRecordRequirement(
                Art22DecisionRecordRequirement.REQUIREMENT_ID,
                Art22DecisionRecordRequirement.CITATION,
                Art22DecisionRecordRequirement.MECHANISM,
                RequirementStatus.GAP, List.of());
    }

    List<Art22DecisionRecord> records = new ArrayList<>();
    boolean allComplete = true;

    for (AmlCaseProfileLedgerEntry entry : profileEntries) {
        Optional<ComplianceSupplement> supplement = entry.compliance();
        if (supplement.isPresent()) {
            ComplianceSupplement s = supplement.get();
            records.add(new Art22DecisionRecord(
                    entry.id, s.algorithmRef, s.confidenceScore,
                    s.rationale, Boolean.TRUE.equals(s.humanOverrideAvailable),
                    s.contestationUri, s.decisionContext != null));
            if (s.algorithmRef == null || s.humanOverrideAvailable == null) {
                allComplete = false;
            }
        } else {
            allComplete = false;
        }
    }

    RequirementStatus status;
    if (!records.isEmpty() && allComplete) {
        status = RequirementStatus.CLOSED;
    } else if (!records.isEmpty()) {
        status = RequirementStatus.PARTIAL;
    } else {
        status = RequirementStatus.GAP;
    }

    return new Art22DecisionRecordRequirement(
            Art22DecisionRecordRequirement.REQUIREMENT_ID,
            Art22DecisionRecordRequirement.CITATION,
            Art22DecisionRecordRequirement.MECHANISM,
            status, records);
}
```

The `build()` method changes to pass the pre-fetched `all` list:

```java
private ComplianceEvidence build(UUID caseId,
        List<AmlCaseOpenedLedgerEntry> caseEntries,
        List<AmlComplianceReviewLedgerEntry> reviewEntries,
        List<AmlSarOfficerReviewedLedgerEntry> officerReviewEntries,
        List<LedgerEntry> all) {
    return new ComplianceEvidence(
            caseId, Instant.now(),
            buildAuditChain(caseId, caseEntries, reviewEntries, officerReviewEntries),
            buildSla(reviewEntries),
            buildTrustRouting(caseId),
            buildGdprErasure(),
            buildArt22DecisionRecord(all),
            null);
}
```

The `all` parameter is the same `List<LedgerEntry>` already fetched in `findEvidence()` / `assembleEvidence()`. Thread it through `build()` to avoid a redundant `findBySubjectId` query.

**Tamper-evidence note:** `ComplianceSupplement` data is covered by the Merkle chain. `LedgerEntry.attach()` serializes supplements to `supplementJson`, and `LedgerEntry.canonicalBytes()` includes `supplementJson` in its hash input. No `domainContentBytes()` change is needed — the supplement is tamper-evident via the canonical bytes path, not the subclass domain content path.

---

## Test Specification

### Unit tests (api/ module)

- `Art22DecisionRecordRequirementTest` — construction, constants
- `Art22DecisionRecordTest` — construction with all fields

### Unit tests (app/ module, plain Mockito)

- `AmlComplianceSupplementTest`:
  - `triageDecision()` returns supplement with all Art.22 fields populated
  - `triageDecision()` with null confidence — `confidenceScore` is null, other fields present
  - Verify `planRef`, `algorithmRef`, `humanOverrideAvailable` static values

- `AmlContentSanitiserTest`:
  - IBAN pattern redacted: `"GB29NWBK60161331926819"` → `"[REDACTED:account]"`
  - Numeric strings NOT redacted (IBAN-only pattern): `"12345678901234"` unchanged
  - Short numbers preserved: `"count: 3"` unchanged
  - Null input returns null
  - String with no PII unchanged

### @QuarkusTest (app/ module)

- `AmlComplianceEvidenceServiceTest` additions:
  - Case with `AmlCaseProfileLedgerEntry` WITH supplement → Art.22 status CLOSED, decision record populated
  - Case with `AmlCaseProfileLedgerEntry` WITHOUT supplement → Art.22 status GAP
  - Case with supplement missing `algorithmRef` → Art.22 status PARTIAL
  - Verify `decisionContextPresent` flag reflects actual supplement state

- `AmlArt22SupplementObserverTest`:
  - Verify `AmlCaseProfileLedgerEntry` receives `ComplianceSupplement` after observer fires
  - Verify `decisionContext` is sanitised (IBANs redacted)
  - Verify supplement `algorithmRef`, `planRef`, `humanOverrideAvailable` values
  - Verify decision context excludes account IDs (originAccountId, destinationAccountId)
  - Verify observer continues independently of CBR observer failures

### Integration tests

- Layer 7 GET compliance evidence → JSON includes `art22DecisionRecord` section with status
- Verify `art22DecisionRecord.decisions[0].humanOverrideAvailable == true`
- Verify `art22DecisionRecord.decisions[0].decisionContextPresent == true`

### Test conventions

- `casehub.ledger.hash-chain.enabled=false` (H2 limitation)
- Drain investigations to terminal status before asserting
- `LedgerEntry.compliance()` works in plain Mockito tests (GE-20260616-ba2c72) — use for supplement content assertions
- `LedgerEntry.attach()` auto-sets bidirectional back-reference (GE-20260526-a5bbd2)

---

## Protocol Compliance

| Protocol | Status |
|----------|--------|
| `domainContentBytes()` enforcement | N/A — supplement is tamper-evident via `canonicalBytes()` which includes `supplementJson`; no subclass content bytes change needed |
| Ledger subject isolation | N/A — supplement attaches to existing `AmlCaseProfileLedgerEntry` |
| api/ module purity | Compliant — new records have no framework dependencies |
| Clinical supplement pattern | Followed — factory class, `JpaComplianceSupplement` instantiation, `entry.attach()` |

## Platform Coherence

- No new foundation types created — uses existing `ComplianceSupplement`, `JpaComplianceSupplement`, `ContentSanitiser`
- No foundation repo changes required — `ContentSanitiser` already exists in ledger-runtime with `PassThroughContentSanitiser` as `@DefaultBean`
- `Art22DecisionRecordRequirement` stays AML-local — promotion to `casehub-blocks` deferred until a second consumer (clinical or life) needs Art.22 evidence reporting
- Follow-up: clinical could adopt the same pattern for EU AI Act Art.12 evidence aggregation (currently attaches supplements but doesn't aggregate them into a compliance report)

## Garden Entries Applied

- **GE-20260616-ba2c72:** `LedgerEntry.compliance()` works in Mockito tests without JPA — used for supplement content assertions
- **GE-20260526-a5bbd2:** `LedgerEntry.attach()` auto-sets bidirectional back-reference — no manual FK needed
- **GE-20260628-6599e6:** Post-GDPR-erasure, actor-scoped queries return empty — Art.22 decision records reference entry IDs, not actor IDs
- **GE-20260805-06bcb8:** `AmlCaseProfileLedgerEntry.outcome` is the triage decision, not the SAR verdict — Art.22 supplement attaches to the triage decision entry
