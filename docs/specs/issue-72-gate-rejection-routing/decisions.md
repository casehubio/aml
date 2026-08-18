# Decisions — Gate Rejection Routing (#72)

## D1: Re-investigation approach

**Choice:** Hybrid — senior analyst as re-investigation step, escalation to head of compliance as backstop
**Alternatives:**
- Full re-investigation — inject rejection rationale, loop back to all specialists. Problem: stub workers produce identical output; loop detection complex.
- Escalation-only — rejection always escalates without re-investigation. Misses the "gather more evidence" path that the industry consensus calls for.
**Rationale:** Senior analyst is already a capability in the model and represents a real human specialist who CAN produce different findings. Head of compliance is the natural backstop per FinCEN escalation expectations. This avoids the stub-worker loop problem while demonstrating both re-investigation and escalation paths.
**Trade-offs:** Senior analyst becomes load-bearing for rejection routing — if the senior analyst capability is unavailable, we need a fallback. Current stub worker will need context-aware output for testing.
**Sources:** FFIEC BSA/AML Manual (escalation procedures), arXiv:2509.08380 (Co-Investigator AI feedback loops), ACM Design Patterns for Approval Processes (Correct & Restart pattern), investigation-triage-design.md, aml-investigation.yaml
**Exploration:** deep-analysis
**Status:** captured

## D2: Terminal outcomes for rejected gates

**Choice:** Add new outcome `investigation-closed-no-sar` — distinct from `investigation-cleared`, meaning "triage said SAR_WARRANTED but MLRO/authority overruled"
**Alternatives:**
- Reuse existing outcomes — funnel back to investigation-complete or investigation-cleared. Simpler but loses visibility into the override decision.
**Rationale:** The accountability layer's value proposition is making consequential decisions visible at the case level. An MLRO override of SAR_WARRANTED is exactly such a decision. Gives trust-routing a distinct signal — override accuracy is a separate dimension from clearance accuracy. FinCEN doesn't require it, but it demonstrates the formal advantage.
**Trade-offs:** New goal adds complexity to completion conditions and test matrix. Must ensure the existing trust-scoring observers handle the new outcome correctly.
**Sources:** FinCEN Oct 2025 SAR FAQs (decision quality over documentation volume), AmlActionType.java, aml-investigation.yaml goals section
**Exploration:** quick
**Depends on:** D1 (hybrid approach creates the path that leads to this outcome)
**Status:** captured

## D3: Post-rejection evaluation mechanism

**Choice:** Re-triage — add a `post-rejection-triage` binding that re-runs the deterministic `InvestigationTriageEvaluator` with extended input (original specialist findings + senior analyst findings + rejection context)
**Alternatives:**
- Direct escalation to head of compliance — skip re-triage, let human authority make final call. Simpler but bypasses the deterministic evaluator, breaking the accountability boundary (supervisor spec D1: "LLM controls investigation flow, deterministic evaluator controls regulatory judgment").
**Rationale:** The evaluator is the single quality gate for regulatory judgment. Humans debate its recommendation, but the evaluation itself must be reproducible and auditable. Senior analyst findings may structurally change the risk score (e.g., clearing a PEP concern, explaining apparent structuring). Re-triage preserves the formally correct decision chain.
**Trade-offs:** Requires extending `TriageInput` to incorporate senior analyst findings and rejection context. `RiskScorer` needs additional factors. Must handle the case where re-triage produces the same result — escalation to head of compliance becomes the backstop.
**Sources:** investigation-triage-design.md (evaluator component decomposition), InvestigationTriageEvaluator.java, TriageInput.java
**Exploration:** quick
**Depends on:** D1 (senior analyst provides the new evidence that makes re-triage meaningful)
**Status:** captured

## D4: Supervisor involvement in rejection routing

**Choice:** Layered B+C — rejection-aware binding selection (B) is always active; substantive routing judgment (C) activates conditionally for complex cases
**Alternatives:**
- Binding selection only (A) — supervisor selects rejection-handling bindings without rejection context. Misses the opportunity to route based on WHY the gate was rejected.
- Rejection-aware prompting only (B) — always includes rejection rationale but never goes beyond binding selection. Leaves the most consequential routing decisions to pure JQ conditions.
- Full supervisor triage (C) always on — every rejection gets deep LLM reasoning. Wasteful for obvious cases; tension with "evaluator controls judgment" boundary.
**Rationale:** B and C operate through the same mechanism (binding selection via `SupervisorDecision`). The difference is prompt depth, not SPI contract. C activates under specific conditions: second rejection cycle, high-risk case (PEP, risk score > 0.8), or substantive rejection rationale. The supervisor's existing `suppressedBindings` and `rationale` fields express C-level judgment without breaking the procedural boundary.
**Trade-offs:** Conditional prompt depth adds complexity to `AmlSupervisorLlmAdapter.buildPrompt()`. Must define activation conditions clearly to avoid every case getting C-level reasoning (token cost, latency).
**Sources:** llm-supervisor-mode-design.md (D1: procedural boundary, D4: selective invocation), AmlInvestigationSupervisor.java, SupervisorDecision.java
**Exploration:** quick
**Depends on:** D1 (hybrid flow creates the rejection-routing bindings the supervisor selects from), D3 (re-triage is the binding the supervisor may suppress in favor of escalation)
**Status:** captured

## D5: Rejection-review capability separation

**Choice:** New `rejection-review` capability (separate from `senior-analyst-review`) with its own output projection writing to `.rejectionReview`
**Alternatives:**
- Reuse `senior-analyst-review` capability — simpler but output projection is capability-level, so both bindings write to `.seniorAnalystReview`, conflicting with the existing senior analyst flow.
**Rationale:** Capabilities own their output projection in the YAML DSL. Two bindings sharing a capability share an output field. Rejection review findings must be distinct from pre-rejection senior analyst review (which may have already fired via `senior-analyst-required-resolution`). Separate capability = separate output field = no conflict.
**Trade-offs:** One more capability and worker registration. Minor — the worker is small.
**Sources:** aml-investigation.yaml (capability/outputProjection relationship), AmlInvestigationCaseDescriptor.java
**Exploration:** quick
**Depends on:** D1 (senior analyst is the re-investigation step)
**Status:** captured

## D6: Escalation WorkItem pattern

**Choice:** `RejectionEscalationLifecycle` observer — mirrors `ComplianceReviewLifecycle` pattern. Worker creates WorkItem, observer handles completion, writes `rejectionEscalation.decision` to case context.
**Alternatives:**
- PlannedAction gate on the escalation worker — overloads the gate mechanism which is designed for "approve/reject an action", not "choose between FILE_SAR/NO_SAR/CLEAR" (three-way decision).
**Rationale:** The head of compliance decision is a three-way choice, not a binary approve/reject. WorkItem with resolution string is the natural model. The `ComplianceReviewLifecycle` pattern is proven in this codebase.
**Trade-offs:** Requires a new lifecycle observer. Must define the callerRef convention for escalation WorkItems.
**Sources:** ComplianceReviewLifecycle.java, compliance-review-opening worker in AmlInvestigationCaseDescriptor.java
**Exploration:** quick
**Depends on:** D1 (escalation is the backstop), D2 (terminal outcomes are driven by the escalation decision)
**Status:** captured

## D7: Loop protection

**Choice:** Max 1 rejection-review cycle per gate type. If re-triage confirms the rejected decision, escalation fires. If head of compliance also rejects, case transitions to `investigation-stalled`. No infinite loops.
**Alternatives:**
- Multiple re-investigation cycles (max 2-3) — allows iterative evidence gathering but risks infinite loops and is unrealistic for stub workers.
- No cap — pure supervisor judgment on when to stop. Risky — LLM hallucination could create endless loops.
**Rationale:** One cycle is sufficient to demonstrate the pattern. In production with real workers, the cap could be increased. The single-cycle design means: gate rejected → senior analyst review → re-triage → (resolved OR escalated). Clean, testable, no loop detection needed.
**Trade-offs:** If the senior analyst provides insufficient evidence, the case goes straight to escalation without a second chance. Acceptable for showcase; configurable in production.
**Sources:** arXiv:2509.08380 (iterative refinement with structured feedback), BPM Correct & Restart pattern
**Exploration:** quick
**Depends on:** D1 (defines the cycle), D3 (re-triage is the decision point)
**Status:** captured
