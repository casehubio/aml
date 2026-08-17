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

## D6: Entity data in ledger content — exemption vs redaction

**Choice:** Document Art.17(3)(b) exemption — AML investigation ledger content (account IDs in `domainContentBytes()`) is exempt from erasure under regulatory retention obligation
**Alternatives:**
- Build a redaction layer despite the exemption — defence-in-depth but significant foundation-level design effort for content that is legally exempt
- Document exemption now, stub redaction API for future — intermediate approach; adds API surface with no current consumer
**Rationale:** AML investigation records are retained under Art.17(3)(b) ("compliance with a legal obligation which requires processing by Union or Member State law to which the controller is subject"). FinCEN BSA 31 CFR 1020.320(d) mandates 5-year SAR retention; 4AMLD Art.40 mandates 5-year record retention. Art.17(3)(b) is a positive legal obligation to retain — stronger than Art.17(3)(e) ("legal claims"), which is discretionary and subject to proportionality challenges. Jurisdictional variability is acknowledged — EU member states have different implementations of exemption provisions. A follow-up issue can track redaction if legal review determines the exemption doesn't fully apply in a specific jurisdiction.
**Trade-offs:** If the exemption is later found insufficient for a specific jurisdiction, no redaction capability exists. Mitigated by creating a tracking issue. Jurisdictional variability not modelled — exemption is treated as universal.
**Exploration:** quick
**Status:** revised (R1-06: corrected Art.17(3)(e) → Art.17(3)(b))

## D7: Exemption placement in compliance evidence

**Choice:** Extend existing `GdprErasureRequirement` with exemption fields. Status semantics: `CLOSED` means "erasure requirement fully addressed" — both erasure capability (for erasable data) and documented exemption (for exempt data) are in place. An `exemptionBasis` field makes the distinction explicit for examiners.
**Alternatives:**
- Separate `ContentRetentionRequirement` record — over-structures a sub-concern of GDPR erasure; the exemption explains why entity data is NOT erased, which belongs with the erasure requirement
- `PARTIAL` status with exemption documented — semantically honest about incomplete erasure but conflates "partially implemented" with "partially applicable"
- New `EXEMPT` enum value on `RequirementStatus` — shared enum across all requirement types; GDPR-specific semantics don't belong there
**Rationale:** The exemption is directly about why ledger content entity data is retained rather than erased. Adding fields to the existing record keeps all Art.17 concerns in one place. `CLOSED` + `exemptionBasis` reads as "erasure requirement closed — some data exempt under [basis], rest erasable" — the clearest signal to an examiner.
**Trade-offs:** `GdprErasureRequirement` grows slightly. If content retention becomes a complex concern with multiple exemption types, a separate record may be warranted later.
**Depends on:** D6 (exemption vs redaction)
**Exploration:** quick
**Status:** revised (R1-07: added status semantics definition)

## D8: Case context entity data — out of scope

**Choice:** Document as out of scope — entity data from CaseContext does not persist as identifying data; no GDPR erasure action needed
**Alternatives:**
- Add explicit cache-eviction erasure for CaseContext — unnecessary; the in-memory context is evicted on case completion, and observers that persist data from it already exclude identifying entity data
**Rationale:** Entity data in CaseContext is out of scope for GDPR erasure for three reasons: (a) the context itself is transient — `InMemoryCaseContextStore` uses a `LinkedHashMap` evicted at case completion; (b) observers that persist data from the context (`AmlCaseProfileStoreObserver`) deliberately exclude identifying entity data (account IDs) from persisted fields — the PII exclusion is in the observer's field selection, not in CaseContext's transience; (c) no persistent `CaseContextStore` implementation exists in the current architecture. If a persistent context store is introduced, this decision must be revisited.
**Trade-offs:** If a persistent CaseContextStore is introduced, this decision must be revisited — the transience argument would no longer hold, and the observer field-exclusion argument would need to be re-verified against the new persistence layer.
**Exploration:** quick
**Status:** revised (R1-08: corrected reasoning — safety attributed to observer field exclusion, not container transience)

## D9: Cross-tenant erasure — parameterise now vs design-only

**Choice:** Parameterise the erasure API now — `AmlErasureService.eraseEntity(entityId, tenantId, reason)` and add `eraseEntityAcrossTenants()`. REST endpoint accepts optional tenantId.
**Alternatives:**
- Design the tenant-aware contract only (spec + ADR, no code change) — documents intent but doesn't prevent continued DEFAULT_TENANT_ID hardcoding in new call sites
- Close as not-yet-needed (YAGNI) — platform capability exists; wire when multi-tenancy arrives. Risk: 36+ DEFAULT_TENANT_ID call sites compound, making retrofit painful.
**Rationale:** 36 DEFAULT_TENANT_ID call sites already exist. Each new caller hardens the single-tenant assumption. Making the erasure API tenant-aware now has low cost and prevents the erasure path from being the hardest part of a future multi-tenancy migration.
**Trade-offs:** Adds a parameter that's always DEFAULT_TENANT_ID today — slight API complexity for a capability not yet used. Mitigated by the default value making it invisible to current callers.
**Exploration:** quick
**Status:** captured

## D10: Use principal.tenancyId() vs keep DEFAULT_TENANT_ID default

**Choice:** Default overload delegates to `principal.tenancyId()` — aligns with the platform's tenant-scoped security model
**Alternatives:**
- Default to `DEFAULT_TENANT_ID` — preserves current behaviour but hardcodes single-tenant assumption into the API contract
**Rationale:** `CurrentPrincipal.tenancyId()` already returns DEFAULT_TENANT_ID in single-tenant mode. Using it as the default makes the API tenant-aware without changing behaviour. When multi-tenancy activates, the correct tenant flows through automatically.
**Trade-offs:** None — `principal.tenancyId()` == `DEFAULT_TENANT_ID` in current config.
**Depends on:** D9 (parameterise now)
**Exploration:** quick
**Status:** captured
