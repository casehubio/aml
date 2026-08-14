## D1: Art.22 scope — which automated decisions need supplements

**Choice:** Investigation triage only (AmlCaseProfileLedgerEntry)
**Alternatives:**
- All automated decisions (triage + agent routing + SAR narrative) — over-broad, routing is operational, narrative is a draft for human review
- Triage + routing — routing doesn't "significantly affect" the data subject
**Rationale:** Triage directly determines whether someone faces further scrutiny (SAR_WARRANTED vs INVESTIGATION_CLEARED). Agent routing and SAR narrative are operational/advisory.
**Trade-offs:** If routing decisions are later found to have regulatory significance, supplements would need to be added retroactively.
**Exploration:** quick
**Status:** captured

## D2: Foundation vs AML split

**Choice:** DecisionContextSanitiser SPI in casehub-ledger-api; everything else in AML
**Alternatives:**
- Art22DecisionRecordRequirement in casehub-blocks — premature; clinical doesn't aggregate supplements into compliance reports yet
- Everything in AML — DecisionContextSanitiser is domain-agnostic, any harness with PII in decisionContext needs it
**Rationale:** Clinical uses supplements as static regulatory metadata (3 of 9 fields). AML is the first consumer of dynamic Art.22 fields (confidenceScore, decisionContext, rationale). Foundation type premature until clinical also needs Art.22 reporting.
**Trade-offs:** Art22DecisionRecordRequirement stays AML-local; promotion to blocks deferred until a second consumer exists.
**Exploration:** deep-analysis
**Status:** captured

## D3: Supplement attachment pattern

**Choice:** AmlComplianceSupplement factory + sanitiser injection (Approach B)
**Alternatives:**
- Inline in AmlCaseProfileStoreObserver — mixes regulatory and event handling, harder to test supplement content independently
- Dedicated AmlTriageDecisionLedgerWriter — structural refactor of observer; clinical LedgerWriter pattern doesn't map to @ObservesAsync CaseOutcomeEvent
**Rationale:** Follows clinical ClinicalComplianceSupplement factory precedent. Dynamic field population with sanitiser call. Testable with plain Mockito (no QuarkusTest). Minimal disruption to existing observer.
**Trade-offs:** One additional class; factory must accept dynamic parameters unlike clinical's static-only methods.
**Exploration:** quick
**Status:** captured

## D4: ComplianceEvidence Art.22 section

**Choice:** New Art22DecisionRecordRequirement record — dedicated section in ComplianceEvidence
**Alternatives:**
- Extend AuditChainRequirement with inline supplement data — conflates FinCEN audit chain (evidence linking) with GDPR Art.22 (decision transparency); two different regulatory obligations in one section
**Rationale:** Each regulation should be independently assessable. CLOSED/PARTIAL/GAP status per Art.22 mirrors the existing pattern (audit chain, SLA, trust routing, GDPR erasure all have separate requirement records with independent status).
**Trade-offs:** Adds a new field to ComplianceEvidence record — wire format change for API consumers.
**Exploration:** quick
**Status:** captured

## D5: DecisionContextSanitiser SPI design

**Choice:** ~~Simple string sanitiser~~ WITHDRAWN — `ContentSanitiser` already exists in `io.casehub.ledger.runtime.privacy` with exactly `String sanitise(String)` and `PassThroughContentSanitiser` as `@DefaultBean` no-op. AML provides a CDI alternative implementation.
**Alternatives:**
- Create new `DecisionContextSanitiser` SPI — redundant; identical interface already shipped
**Rationale:** Decision review R1-01 identified the existing interface. Parameter named `decisionContextJson` confirms identical intent. No foundation changes needed.
**Trade-offs:** None — strictly simpler.
**Exploration:** quick
**Status:** revised

## D2 revision note

Original D2 proposed foundation changes (new SPI in ledger-api). With D5 withdrawn, **no foundation repo changes are needed.** AML-only scope: provide `ContentSanitiser` implementation + supplement factory + evidence section. The slot still needs only the AML repo.
