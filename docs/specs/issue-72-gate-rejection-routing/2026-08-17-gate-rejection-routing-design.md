# Gate Rejection Routing — Design Spec

**Issue:** casehubio/aml#72
**Date:** 2026-08-17
**Status:** Draft

## Summary

Add rejection routing logic for both AML oversight gates: SAR_FILING (MLRO rejects SAR) and INVESTIGATION_CLEARANCE (compliance officer rejects case clearance). When a gate is rejected, the case routes through a senior analyst re-investigation, deterministic re-triage with extended input, and — if the re-triage confirms the rejected decision — escalation to the head of compliance for final determination.

The LLM supervisor (`AmlInvestigationSupervisor`) participates in rejection routing via rejection-aware binding selection, with conditional deeper reasoning for complex cases (PEP entities, second rejection cycles, high-risk scores).

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Re-investigation approach (D1) | Hybrid — senior analyst + head-of-compliance escalation | Avoids stub-worker loop problem; demonstrates both re-investigation and escalation paths |
| Terminal outcomes (D2) | New `investigation-closed-no-sar` goal | Makes MLRO override visible at case level; distinct trust-routing signal |
| Post-rejection evaluation (D3) | Re-triage via `InvestigationTriageEvaluator` with extended input | Preserves deterministic evaluator as single quality gate for regulatory judgment |
| Supervisor involvement (D4) | Layered B+C — always rejection-aware, conditionally deeper | Same mechanism (binding selection), different prompt depth by case complexity |
| Capability separation (D5) | New `rejection-review` capability, separate from `senior-analyst-review` | Output projection is capability-level; avoids field conflict with existing senior analyst flow |
| Escalation pattern (D6) | `RejectionEscalationLifecycle` observer, mirrors `ComplianceReviewLifecycle` | Three-way decision (FILE_SAR/NO_SAR/CLEAR) doesn't fit binary gate approve/reject |
| Loop protection (D7) | Max 1 rejection-review cycle per gate type | Sufficient for showcase; configurable for production |

## Architecture

### Engine Mechanism (existing — no engine changes required)

`ActionGateRejectedHandler` already provides the integration point:
1. Clears `PendingActionGate` from case instance
2. Writes `actionGateRejected: {actionType, workerId, rejectedBy, resolution}` to case context
3. Marks the worker as faulted — output NOT committed to context
4. Fires `CaseContextChangedEvent` — YAML bindings re-evaluate
5. Records `RoutingOutcome.GATE_REJECTED` and writes `ACTION_GATE_REJECTED` to event log

No engine changes needed. All rejection routing logic lives in AML YAML bindings, workers, and observers.

### Rejection Flow — SAR_FILING

```
sar-drafting → PlannedAction(SAR_FILING) → MLRO rejects
  → engine writes actionGateRejected to context
  → rejection-review binding fires (supervisor selects)
    → senior analyst reviews with rejection rationale
    → writes rejectionReview to context
  → post-rejection-triage binding fires
    → InvestigationTriageEvaluator with extended input
    → writes postRejectionTriage to context
  → outcome routing:
    a. Score drops below SAR threshold
       → investigation-closed-no-sar (goal met)
    b. Score still above threshold
       → rejection-escalation binding fires
       → head of compliance WorkItem created
       → decision:
         - "FILE_SAR" → sar-drafting-post-escalation fires
           → sarNarrative written → compliance-review-opening fires
           → investigation-complete
         - "NO_SAR" → investigation-closed-no-sar (goal met)
```

### Rejection Flow — INVESTIGATION_CLEARANCE

```
investigation-triage → PlannedAction(INVESTIGATION_CLEARANCE) → officer rejects
  → engine writes actionGateRejected to context
  → rejection-review binding fires (supervisor selects)
    → senior analyst reviews with rejection rationale
    → writes rejectionReview to context
  → post-rejection-triage binding fires
    → InvestigationTriageEvaluator with extended input
    → writes postRejectionTriage to context
  → outcome routing:
    a. FALSE_POSITIVE → investigation-cleared (goal met)
    b. SAR_WARRANTED → sar-drafting-after-clearance-rejection fires
       → normal SAR path (sar-drafting → SAR_FILING gate → MLRO)
       → investigation-complete
    c. Still INCONCLUSIVE → rejection-escalation binding fires
       → head of compliance WorkItem
       → decision:
         - "CLEAR" → investigation-cleared (goal met)
         - "FILE_SAR" → sar-drafting-post-escalation fires
           → investigation-complete
```

### Loop Protection

Max 1 rejection-review cycle per gate type. The flow is linear:

```
gate rejected → senior analyst → re-triage → (resolved OR escalated)
```

If the head of compliance's decision creates another gate (e.g., "FILE_SAR" → SAR_FILING gate → MLRO rejects AGAIN), `actionGateRejected` is overwritten (single field, not accumulated). The `rejectionReview != null` guard prevents the rejection-review binding from re-firing. The case transitions to `investigation-stalled` if no completion path is reachable after escalation.

### Rejection-Review Worker Failure

If the `rejection-review` worker (senior analyst) fails (worker error, not DECLINE), the existing failure-handling pattern applies: the engine writes a failure marker to context. A new `rejection-review-failed` binding should escalate directly to head of compliance, bypassing re-triage — the case still needs resolution even without additional evidence. This mirrors the existing `osint-screening-failed-escalation` pattern.

## YAML Changes

### New Capabilities

```yaml
- name: rejection-review
  description: "Senior analyst reviews case after gate rejection"
  inputProjection: >-
    { transaction: .transaction,
      entityResolution: .entityResolution,
      rejectionContext: .actionGateRejected }
  outputProjection: "{ rejectionReview: . }"

- name: post-rejection-triage
  description: "Re-evaluate specialist findings with senior analyst review and rejection context"
  inputProjection: >-
    { entityResolution: .entityResolution,
      patternAnalysis: .patternAnalysis,
      osintScreening: .osintScreening,
      cbrPathAdvice: .cbrPathAdvice,
      seniorAnalystReview: .rejectionReview,
      rejectionContext: .actionGateRejected }
  outputProjection: "{ postRejectionTriage: . }"

- name: sar-drafting-escalated
  description: "Re-draft SAR narrative after head-of-compliance FILE_SAR decision — no PlannedAction gate"
  inputProjection: >-
    { transaction: .transaction,
      entityResolution: .entityResolution,
      patternAnalysis: .patternAnalysis,
      osintScreening: .osintScreening,
      similarSarNarratives: .cbrPathAdvice.similarSarNarratives }
  outputProjection: "{ sarNarrative: .sarNarrative, narrativeSeeded: .narrativeSeeded, seedCount: .seedCount }"

- name: rejection-escalation
  description: "Escalate to head of compliance when re-triage confirms rejected decision"
  inputProjection: >-
    { transaction: .transaction,
      entityResolution: .entityResolution,
      patternAnalysis: .patternAnalysis,
      osintScreening: .osintScreening,
      rejectionReview: .rejectionReview,
      postRejectionTriage: .postRejectionTriage,
      rejectionContext: .actionGateRejected }
  outputProjection: "{ rejectionEscalation: . }"
```

### New Bindings

### Modified Existing Binding — sar-drafting

Add `.actionGateRejected == null` guard to prevent re-fire after gate rejection. Without this, the original binding's condition (`.investigationTriage.decision == "SAR_WARRANTED" and .sarNarrative == null`) remains satisfied after SAR_FILING rejection — the engine's plan-item tracking prevents re-fire but this should be explicit in the YAML, not an implicit invariant.

```yaml
- name: sar-drafting
  when: >-
    ... existing conditions ... and
    .actionGateRejected == null
```

```yaml
## ─── Rejection Routing ──────────────────────────────────────────

## Senior analyst reviews case after any gate rejection.
- name: rejection-review
  on: { contextChange: {} }
  when: >-
    .actionGateRejected != null and
    .rejectionReview == null
  capability: rejection-review

## Re-triage with extended input after senior analyst review.
- name: post-rejection-triage
  on: { contextChange: {} }
  when: >-
    .actionGateRejected != null and
    .rejectionReview != null and
    .postRejectionTriage == null
  capability: post-rejection-triage

## Escalation — fires when re-triage confirms the rejected decision,
## OR when re-triage produces INCONCLUSIVE (for either gate type —
## INCONCLUSIVE always needs human sign-off per regulatory principle).
- name: rejection-escalation
  on: { contextChange: {} }
  when: >-
    .postRejectionTriage != null and
    .rejectionEscalation == null and
    ((.postRejectionTriage.decision == "SAR_WARRANTED"
      and .actionGateRejected.actionType == "sar.filing") or
     .postRejectionTriage.decision == "INCONCLUSIVE")
  capability: rejection-escalation

## SAR path re-entry after escalation approves filing.
## Head of compliance has already approved — NO PlannedAction gate.
## This bypasses the MLRO gate that was rejected, avoiding the authority
## paradox where the escalation authority's decision is re-gated by the
## same actor who triggered the escalation.
- name: sar-drafting-post-escalation
  on: { contextChange: {} }
  when: >-
    .rejectionEscalation.decision == "FILE_SAR" and
    .sarNarrative == null
  capability: sar-drafting-escalated

## Stall detection — fires when a second rejection occurs after the first
## rejection cycle has completed (rejectionReview already written).
## actionGateRejected is overwritten by the engine on each rejection,
## but rejectionReview != null prevents the rejection-review binding
## from re-firing. This binding catches the case and marks it stalled.
- name: rejection-stall-detection
  on: { contextChange: {} }
  when: >-
    .actionGateRejected != null and
    .rejectionReview != null and
    .postRejectionTriage != null and
    .rejectionEscalation == null and
    .investigationStalled != true
  capability: rejection-stall

## Officer rejected clearance, re-investigation shows SAR warranted.
## Normal SAR path — MLRO gate, not escalated.
- name: sar-drafting-after-clearance-rejection
  on: { contextChange: {} }
  when: >-
    .postRejectionTriage.decision == "SAR_WARRANTED" and
    .actionGateRejected.actionType == "investigation.clearance" and
    .sarNarrative == null
  capability: sar-drafting
```

### New Goal

```yaml
- name: investigation-closed-no-sar
  kind: success
  condition: >-
    (.postRejectionTriage.decision == "FALSE_POSITIVE" and
     .actionGateRejected.actionType == "sar.filing") or
    .rejectionEscalation.decision == "NO_SAR"
```

### Modified Goal — investigation-cleared

```yaml
- name: investigation-cleared
  kind: success
  condition: >-
    (.investigationTriage.decision == "FALSE_POSITIVE" or
     .investigationTriage.decision == "INCONCLUSIVE") or
    (.postRejectionTriage.decision == "FALSE_POSITIVE" and
     .actionGateRejected.actionType == "investigation.clearance") or
    .rejectionEscalation.decision == "CLEAR"
```

### Updated Completion

```yaml
completion:
  success:
    anyOf:
      - investigation-complete
      - investigation-cleared
      - investigation-closed-no-sar
  failure:
    anyOf:
      - investigation-stalled
```

### Supervised-Investigation Compound — New Members

The rejection-routing bindings are added to the `supervised-investigation` compound so the LLM supervisor can select among them:

```yaml
compounds:
  - name: supervised-investigation
    planningStrategy: aml-supervisor
    members:
      - pattern-analysis
      - osint-screening
      - investigation-triage
      - sar-drafting
      - senior-analyst-required-resolution

# Rejection-routing bindings are NOT in this compound — they are sequential
# and deterministic (one eligible binding at a time), so LLM selection adds
# no value. They use ChoreographyStrategy (fire when eligible).
# See: rejection-review, post-rejection-triage, rejection-escalation,
#      sar-drafting-post-escalation, sar-drafting-after-clearance-rejection
```

## Domain Model (api/)

### New Types

**`RejectionContext`** — typed record for the engine's `actionGateRejected` context field:

```java
public record RejectionContext(
    String actionType,
    String workerId,
    String rejectedBy,
    String resolution
) {}
```

**`SeniorAnalystReview`** — typed record for rejection-review worker output:

```java
public record SeniorAnalystReview(
    double riskAdjustment,
    String finding,
    String recommendedAction
) {
    public SeniorAnalystReview {
        if (riskAdjustment < -1.0 || riskAdjustment > 1.0) {
            throw new IllegalArgumentException(
                "riskAdjustment must be in [-1.0, 1.0], got: " + riskAdjustment);
        }
        Objects.requireNonNull(finding);
        Objects.requireNonNull(recommendedAction);
    }
}
```

### Modified Types

**`TriageInput`** — extended with nullable senior analyst review and rejection context:

```java
public record TriageInput(
    EntityResolutionResult entityResolution,
    PatternAnalysisResult patternAnalysis,
    OsintResult osintScreening,
    CbrPathAdvice cbrPathAdvice,
    SeniorAnalystReview seniorAnalystReview,
    RejectionContext rejectionContext
) {
    public TriageInput(
            EntityResolutionResult entityResolution,
            PatternAnalysisResult patternAnalysis,
            OsintResult osintScreening,
            CbrPathAdvice cbrPathAdvice) {
        this(entityResolution, patternAnalysis, osintScreening, cbrPathAdvice, null, null);
    }
}
```

Backward-compatible: the existing 4-arg constructor delegates to the full constructor with nulls. All existing call sites (including `InvestigationTriageWorker`) are unaffected.

**`RiskScorer`** — two additional factors:

| Factor | Weight | Value |
|--------|--------|-------|
| Senior analyst risk adjustment | 0.15 | `clamp(seniorAnalystReview.riskAdjustment(), -1.0, 1.0)` — negative values reduce risk |
| Rejection uncertainty | 0.10 | 0.3 when rejection context present (uncertainty signal — the process has been challenged) |

New factors only contribute when `seniorAnalystReview != null` or `rejectionContext != null` respectively. When null, their contribution is 0.0 — existing score formula unchanged. Total weight exceeds 1.0 (1.15 with senior analyst, 1.25 with both), so the final score is normalized: `clamp(rawScore, 0.0, 1.0)`.

**Hard gate interaction:** `HardGateEvaluator` checks absolute gates (SANCTIONS_HIT, CONFIRMED_PEP, SHELL_COMPANY) BEFORE scoring. Hard gates bypass the `RiskScorer` entirely. If a hard gate fired in the original triage, the senior analyst's `riskAdjustment` factor has no effect — re-triage still produces SAR_WARRANTED via the hard gate. This is correct: hard gates encode regulatory absolutes that no analyst override should bypass. For hard-gate cases, rejection routing always reaches escalation (re-triage confirms SAR_WARRANTED → head of compliance decides).

## Workers (app/)

### RejectionReviewWorker

Registered for capability `rejection-review`. Stub implementation for the showcase:

```java
public static Worker create(ObjectMapper objectMapper) {
    return Worker.builder()
        .name("rejection-review-agent")
        .capabilityName("rejection-review")
        .function(new FlowWorkerFunction(
            workflow("rejection-review")
                .tasks(function(s -> {
                    Map<String, Object> input = (Map<String, Object>) s;
                    Map<String, Object> rejection = (Map<String, Object>) input.get("rejectionContext");
                    String actionType = rejection != null
                        ? (String) rejection.getOrDefault("actionType", "") : "";
                    boolean isSarRejection = "sar.filing".equals(actionType);
                    return Map.of(
                        "riskAdjustment", isSarRejection ? -0.15 : 0.1,
                        "finding", isSarRejection
                            ? "Entity structure reassessed — legitimate corporate activity"
                            : "Additional OSINT screening confirms initial risk indicators",
                        "recommendedAction", isSarRejection ? "LOWER_RISK" : "MAINTAIN_RISK");
                }, Map.class))
                .build()))
        .build();
}
```

Context-aware: SAR_FILING rejection produces findings that lower risk (supporting the MLRO's judgment), INVESTIGATION_CLEARANCE rejection produces findings that maintain or raise risk (supporting the officer's concern).

### PostRejectionTriageWorker

Registered for capability `post-rejection-triage`. Calls `InvestigationTriageEvaluator` with extended `TriageInput`:

```java
public static Worker create(ObjectMapper objectMapper, PreferenceProvider preferenceProvider) {
    return Worker.builder()
        .name("post-rejection-triage-agent")
        .capabilityName("post-rejection-triage")
        .function((Map<String, Object> input) -> {
            var entity = objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
            var pattern = objectMapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
            var osint = objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
            CbrPathAdvice cbr = input.get("cbrPathAdvice") != null
                ? objectMapper.convertValue(input.get("cbrPathAdvice"), CbrPathAdvice.class) : null;
            SeniorAnalystReview review = input.get("seniorAnalystReview") != null
                ? objectMapper.convertValue(input.get("seniorAnalystReview"), SeniorAnalystReview.class) : null;
            RejectionContext rejection = input.get("rejectionContext") != null
                ? objectMapper.convertValue(input.get("rejectionContext"), RejectionContext.class) : null;

            var triageInput = new TriageInput(entity, pattern, osint, cbr, review, rejection);
            var evaluator = buildEvaluator(preferenceProvider);
            var result = evaluator.evaluate(triageInput);
            return toResultMap(result);
        })
        .build();
}
```

Uses `WorkerFunction.Sync` (not `FlowWorkerFunction`) — same pattern as `InvestigationTriageWorker`. No `PlannedAction` on the post-rejection-triage result — the routing is handled by YAML binding conditions on `.postRejectionTriage.decision`.

### RejectionEscalationWorker

Registered for capability `rejection-escalation`. Creates a head-of-compliance WorkItem. Uses `WorkerFunction.Sync` (not `FlowWorkerFunction`) because it needs `WorkerScope.caseId()` for the callerRef — same pattern as `complianceReviewOpeningWorker`:

```java
public static Worker create(ObjectMapper objectMapper,
                             RejectionEscalationLifecycle lifecycle) {
    return Worker.builder()
        .name("rejection-escalation-agent")
        .capabilityName("rejection-escalation")
        .fn(null).apply((input, scope) -> {
            UUID taskId = lifecycle.openEscalation(
                scope.caseId(),
                objectMapper.writeValueAsString(input));
            return WorkerResult.of(Map.of(
                "escalationTaskId", taskId.toString(),
                "status", "PENDING"));
        })
        .build();
}
```

The worker writes the escalation task ID to context. The `RejectionEscalationLifecycle` observer handles the WorkItem completion event and writes `rejectionEscalation: {decision, reason}` to context.

### RejectionEscalationLifecycle

Mirrors `ComplianceReviewLifecycle`:

```java
@ApplicationScoped
public class RejectionEscalationLifecycle {

    @Inject WorkItemCreator workItemCreator;
    @Inject CaseHubRuntime runtime;

    public UUID openEscalation(String evidencePayload) {
        return workItemCreator.create(WorkItemCreateRequest.builder()
            .title("Gate rejection escalation — head of compliance review required")
            .candidateGroups(AmlGroups.AML_SENIOR_COMPLIANCE)
            .createdBy("casehub-engine")
            .payload(evidencePayload)
            .callerRef("aml:escalation:" + /* caseId from context */)
            .scope("casehubio/aml/escalation")
            .tenancyId(TenancyConstants.DEFAULT_TENANT_ID)
            .build());
    }

    void onWorkItemCompleted(@ObservesAsync WorkItemLifecycleEvent event) {
        if (!event.workItem().callerRef.startsWith("aml:escalation:")) return;
        UUID caseId = extractCaseId(event.workItem().callerRef);
        String resolution = event.workItem().resolution;
        String decision = mapResolution(resolution); // FILE_SAR, NO_SAR, or CLEAR
        runtime.updateContext(caseId, Map.of(
            "rejectionEscalation", Map.of("decision", decision, "reason", resolution)));
    }
}
```

## Supervisor Changes (app/)

### Context Projection Extension

`AmlSupervisorLlmAdapter.buildPrompt()` — add rejection context to the structured projection when `actionGateRejected` is present in case context:

```
## Rejection Context (when present)
- Gate type: {actionType}
- Rejected by: {rejectedBy}
- Rationale: {resolution}
- Senior analyst review: {rejectionReview} (when present)
- Post-rejection triage: {postRejectionTriage} (when present)
```

### Conditional Depth (Layer C)

When ANY of these conditions hold, the prompt includes additional reasoning instructions:
- Case has `rejectionReview != null` (second cycle — senior analyst already reviewed)
- Entity type is PEP
- Risk score > 0.8
- Rejection rationale is substantive (resolution string length > 20 chars)

Layer C prompt addition:
```
This case has been through rejection routing. The rejection rationale and
senior analyst review are included in the context below. Use this to inform
selection of non-rejection bindings that may also be eligible in this cycle.
```

**Note:** Rejection-routing bindings are kept OUTSIDE the `supervised-investigation` compound — they are sequential with no branching points, so LLM selection adds latency with no decisional value. The supervisor sees rejection context in its projection for situational awareness only.

## Trust Routing Integration

When a case reaches `investigation-closed-no-sar`, the triage evaluator originally said SAR_WARRANTED but a human authority overruled. This has trust-scoring implications:

**Which workers' scores are affected:** The specialist workers whose findings drove the SAR_WARRANTED triage are not penalized — their findings were factually correct. The trust impact falls on:
- `investigation-triage-agent` — the evaluator produced a SAR_WARRANTED decision that was overruled. This is feedback on the evaluator's threshold calibration, not worker accuracy. Recorded as a `TRIAGE_OVERRULED` attestation on the `investigation-accuracy` trust dimension.
- `rejection-review-agent` — the senior analyst's risk adjustment contributed to the re-triage result. If re-triage dropped below SAR threshold, the senior analyst's judgment was vindicated. If escalation was needed, neutral.

**Attestation mechanism:** `AmlTrustRoutingObserver` (existing) fires on `WorkerDecisionEvent`. Extend to also fire on case completion with `investigation-closed-no-sar` outcome:
- Check case outcome via `CaseOutcomeEvent`
- If outcome is `investigation-closed-no-sar`, write attestation with dimension `investigation-accuracy`, verdict `OVERRULED`, for the triage worker

**Existing dimension:** Uses `investigation-accuracy` (existing trust dimension). No new dimension needed — the `OVERRULED` verdict type is the distinguishing signal, not the dimension.

## Testing Strategy

### Unit Tests (api/) — pure domain, no Quarkus

**`RejectionContextTest`:**
- Construction, null handling

**`SeniorAnalystReviewTest`:**
- Valid construction
- riskAdjustment bounds [-1.0, 1.0]
- Null fields throw

**`ExtendedTriageInputTest`:**
- 6-arg constructor with senior analyst review and rejection context
- 4-arg backward-compatible constructor — nulls for new fields
- Null seniorAnalystReview and rejectionContext are valid

**`ExtendedRiskScorerTest`:**
- Senior analyst risk adjustment factor (positive and negative adjustments)
- Rejection uncertainty factor
- Null senior analyst review — factor contribution is 0.0
- Score normalization when total weight exceeds 1.0
- Backward compatibility — existing factor weights unchanged when new fields are null

### @QuarkusTest Integration Tests (app/)

All tests follow established conventions: drain to completion (PP-20260604-820c35), Awaitility polling, gate approval before attestation wait (GE-20260628-dbc656), ledger subject isolation, CBR store isolation (GE-20260716-986cd1).

**`SarFilingRejectionRoutingTest`:**

| # | Scenario | Terminal |
|---|---|---|
| 1 | SAR_FILING rejected → review → re-triage score drops | `investigation-closed-no-sar` |
| 2 | SAR_FILING rejected → review → still SAR_WARRANTED → escalation "NO_SAR" | `investigation-closed-no-sar` |
| 3 | SAR_FILING rejected → review → still SAR_WARRANTED → escalation "FILE_SAR" → re-draft → compliance review | `investigation-complete` |

**`ClearanceRejectionRoutingTest`:**

| # | Scenario | Terminal |
|---|---|---|
| 4 | CLEARANCE rejected → review → FALSE_POSITIVE | `investigation-cleared` |
| 5 | CLEARANCE rejected → review → SAR_WARRANTED → SAR path | `investigation-complete` |
| 6 | CLEARANCE rejected → review → still INCONCLUSIVE → escalation "CLEAR" | `investigation-cleared` |

Each test:
1. Starts investigation with appropriate flag reason
2. Drains to initial gate
3. Rejects the gate with a rationale
4. Awaits rejection routing (senior analyst review, re-triage)
5. For escalation scenarios: finds and resolves head-of-compliance WorkItem
6. Drains to terminal state
7. Verifies outcome type and audit trail entries

## File Inventory

### New Files

| File | Module | Description |
|------|--------|-------------|
| `RejectionContext.java` | api | Typed record for gate rejection context |
| `SeniorAnalystReview.java` | api | Typed record for rejection-review output |
| `RejectionContextTest.java` | api (test) | Unit tests |
| `SeniorAnalystReviewTest.java` | api (test) | Unit tests |
| `ExtendedTriageInputTest.java` | api (test) | Extended TriageInput unit tests |
| `ExtendedRiskScorerTest.java` | api (test) | New risk factors unit tests |
| `RejectionReviewWorker.java` | app | Stub worker for `rejection-review` capability |
| `PostRejectionTriageWorker.java` | app | Re-triage with extended input |
| `RejectionEscalationWorker.java` | app | Creates head-of-compliance WorkItem |
| `RejectionEscalationLifecycle.java` | app | Observer for escalation WorkItem completion |
| `SarDraftingEscalatedWorker.java` | app | SAR drafting without PlannedAction gate (post-escalation) |
| `SarFilingRejectionRoutingTest.java` | app (test) | Integration tests — SAR rejection scenarios |
| `ClearanceRejectionRoutingTest.java` | app (test) | Integration tests — clearance rejection scenarios |

### Modified Files

| File | Change |
|------|--------|
| `aml-investigation.yaml` | Add capabilities, bindings, goal, update completion block |
| `TriageInput.java` | Add nullable seniorAnalystReview and rejectionContext fields; backward-compatible constructor |
| `RiskScorer.java` | Add senior-analyst-risk-adjustment and rejection-uncertainty factors |
| `InvestigationTriageEvaluator.java` | Pass new fields through to RiskScorer (no logic change) |
| `AmlInvestigationCaseDescriptor.java` | Register new workers |
| `AmlInvestigationCaseHub.java` | Wire RejectionEscalationLifecycle |
| `AmlSupervisorLlmAdapter.java` | Extend context projection with rejection fields; conditional Layer C prompt |
| `AmlInvestigationCaseDescriptorTest.java` | Update worker count assertion, add new capability names |

### Dependencies

No new dependencies. All changes use existing casehub-work, casehub-engine, and casehub-ledger APIs.

## References

- [FFIEC BSA/AML Manual — SAR Requirements](https://bsaaml.ffiec.gov/manual/AssessingComplianceWithBSARegulatoryRequirements/04) — escalation procedures, SAR decision authority
- [FinCEN SAR FAQs (Oct 2025)](https://www.fincen.gov/system/files/2025-10/SAR-FAQs-October-2025.pdf) — documentation of no-file decisions, risk-based judgment
- [Co-Investigator AI (arXiv:2509.08380)](https://arxiv.org/abs/2509.08380) — agentic AML framework with structured feedback loops
- [ACM Design Patterns for Approval Processes](https://dl.acm.org/doi/fullHtml/10.1145/3628034.3628035) — Correct & Restart pattern for rejection routing
- `docs/specs/issue-8-llm-supervisor-mode/2026-08-14-llm-supervisor-mode-design.md` — supervisor scope boundary (D1), compound scoping
- `docs/specs/issue-112-investigation-triage-logic/2026-07-21-investigation-triage-design.md` — evaluator decomposition, PlannedAction gate behavior
- `ActionGateRejectedHandler.java` (casehub-engine) — engine gate rejection mechanism
- GE-20260628-dbc656 — WorkerDecisionEvent timing: gate approval before attestation wait
- GE-20260808-47dc40 — CasePlanModel adaptation: replaceCompound for safe re-registration
- GE-20260612-9ff1c6 — Programmatic binding timing: dedicated case hub per worker
- GE-20260808-56e574 — getPlanItemByBindingName vs findPlanItemByBindingName semantics
- GE-20260604-38e09e — Engine WAITING state on humanTask binding
