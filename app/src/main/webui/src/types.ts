// Core API Response Types

/**
 * Paginated response wrapper for list endpoints
 */
export interface PagedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * Summary of an AML investigation for list view
 */
export interface InvestigationSummaryResponse {
  caseId: string;
  status: string; // in-progress | completed | failed | cancelled | suspended
  outcomeType: string;
  transactionId: string;
  originAccount: string;
  destinationAccount: string;
  amount: number;
  currency: string;
  flagReason: string;
  riskScore: number; // 0.0 to 1.0
  createdAt: string; // ISO 8601 timestamp
}

/**
 * Detailed investigation response from Layer 6 endpoint
 */
export interface Layer6InvestigationResponse {
  caseId: string;
  status: string; // in-progress | completed | failed | cancelled | suspended
  routingDecisions: WorkerRoutingDecision[];
  outcome: InvestigationOutcome;
  failureContext: FailureContext | null;
}

/**
 * Worker routing decision within an investigation
 */
export interface WorkerRoutingDecision {
  capabilityTag: string;
  selectedWorker: string;
  trustScore: number | null; // null when Phase 0 (no trust history)
}

/**
 * Investigation outcome details
 */
export interface InvestigationOutcome {
  type: string; // sar-filed | gate-rejected | decision-not-recorded
  reason: string | null;
}

/**
 * Failure context when an investigation fails
 */
export interface FailureContext {
  triggerGoalName: string;
  triggerGoalKind: string;
  failureEvents: FailureEvent[];
  occurredAt: string; // ISO 8601 timestamp
}

/**
 * Individual failure event in an investigation
 */
export interface FailureEvent {
  eventType: string;
  workerId: string;
  timestamp: string; // ISO 8601 timestamp
  detail: string;
}

/**
 * Aggregates all specialist findings for an investigation
 */
export interface InvestigationFindingsResponse {
  entityResolution: SpecialistFindingResponse;
  patternAnalysis: SpecialistFindingResponse;
  osintScreening: SpecialistFindingResponse;
  sarNarrative: SpecialistFindingResponse;
}

/**
 * Specialist finding with execution status
 */
export interface SpecialistFindingResponse {
  status: string; // COMPLETED | PENDING
  result: Record<string, any> | null; // null when status is PENDING
}

/**
 * Gate decisions for an investigation
 */
export interface InvestigationGatesResponse {
  gates: GateDecisionResponse[];
}

/**
 * Individual gate decision
 */
export interface GateDecisionResponse {
  workItemId: string;
  actionType: string; // e.g. "sar.filing", "account.restriction"
  gatePolicy: string; // ALWAYS | RISK_SCORE_THRESHOLD | CONFIDENCE_THRESHOLD
  reversible: boolean;
  description: string;
  candidateGroups: string[]; // e.g. ["aml-mlro"], ["aml-compliance"]
  status: string; // PENDING | ASSIGNED | IN_PROGRESS | COMPLETED | REJECTED
  approvedBy: string | null;
  approvedAt: string | null; // ISO 8601 timestamp
  expiresAt: string | null; // ISO 8601 timestamp
}

/**
 * Throughput metrics for operations dashboard
 */
export interface ThroughputMetrics {
  totalInvestigations: number;
  byStatus: Record<string, number>; // e.g. {"IN_PROGRESS": 10, "COMPLETED": 5}
  byFlagReason: Record<string, number>;
  byOutcomeType: Record<string, number>;
}

/**
 * Trust score metrics for all agents
 */
export interface TrustScoreMetrics {
  scores: AgentTrustScore[];
}

/**
 * Trust score for a single agent-capability pair
 */
export interface AgentTrustScore {
  agentId: string;
  capabilityTag: string;
  score: number | null; // 0.0 to 1.0, or null if no observations yet
}

/**
 * Gate metrics for operations dashboard
 */
export interface GateMetrics {
  totalGates: number;
  byActionType: Record<string, number>;
  byStatus: Record<string, number>;
  averageApprovalTimeSeconds: number | null;
}

export interface InterventionMetrics {
  escalationCount: number;
  manualOverrideCount: number;
  declineRoutingCount: number;
  gateRejectionCount: number;
  averageResponseTimeSeconds: number;
  recentInterventions: RecentIntervention[];
}

export interface RecentIntervention {
  type: string;
  caseId: string;
  reason: string;
  actor: string;
  occurredAt: string;
}

/**
 * Compliance evidence for an investigation
 */
export interface ComplianceEvidence {
  caseId: string;
  generatedAt: string; // ISO 8601 timestamp
  auditChain: AuditChainRequirement;
  sla: SlaRequirement;
  trustRouting: TrustRoutingRequirement;
  gdprErasure: GdprErasureRequirement;
  signature: string;
}

/**
 * Audit chain compliance requirement
 */
export interface AuditChainRequirement {
  id: string;
  citation: string;
  mechanism: string;
  status: string; // MET | NOT_MET | PARTIAL
  treeRoot: string;
  chainVerified: boolean;
  events: LedgerEventRecord[];
}

/**
 * SLA compliance requirement
 */
export interface SlaRequirement {
  id: string;
  citation: string;
  mechanism: string;
  status: string; // MET | NOT_MET | PARTIAL
  workItemId: string;
  claimDeadline: string; // ISO 8601 timestamp
  completedAt: string | null; // ISO 8601 timestamp
  slaMet: boolean;
  candidateGroups: string[];
  escalationPolicy: string;
}

/**
 * Trust routing compliance requirement
 */
export interface TrustRoutingRequirement {
  id: string;
  citation: string;
  mechanism: string;
  status: string; // MET | NOT_MET | PARTIAL
}

/**
 * GDPR erasure compliance requirement
 */
export interface GdprErasureRequirement {
  id: string;
  citation: string;
  mechanism: string;
  status: string; // MET | NOT_MET | PARTIAL
  tokenisationEnabled: boolean;
  erasureReceiptEnabled: boolean;
  erasureReceiptCount: number;
  erasureEndpoint: string;
}

/**
 * Audit trail entry from ledger
 */
export interface AuditTrailEntry {
  entryId: string;
  entryType: string;
  discriminator: string;
  actorId: string;
  actorRole: string;
  occurredAt: string; // ISO 8601 timestamp
  causedByEntryId: string | null;
  digest: string;
  sequenceNumber: number;
  domainFields: Record<string, unknown>;
}

export interface AmlCaseOpenedFields {
  transactionId: string;
  originAccountId: string;
  destinationAccountId: string;
}

export interface AmlSarReviewedFields {
  reviewDecision: string;
  rejectionReason: string | null;
  actorRole: string;
}

export interface QhorusMessageFields {
  messageType: string;
  target: string | null;
  correlationId: string | null;
  topic: string | null;
  durationMs: number | null;
}

export interface CaseLedgerFields {
  caseStatus: string | null;
  eventType: string | null;
  commandType: string | null;
}

export interface WorkerDecisionFields {
  workerId: string;
  capabilityTag: string | null;
  trustScoreAtRouting: number | null;
}

/**
 * Ledger event record with inclusion proof
 */
export interface LedgerEventRecord {
  entryId: string;
  eventType: string;
  actorId: string;
  actorRole: string;
  occurredAt: string; // ISO 8601 timestamp
  causedByEntryId: string | null;
  digest: string;
  inclusionProof: AmlInclusionProof;
}

/**
 * Inclusion proof for Merkle chain verification
 */
export interface InclusionProof {
  verified: boolean;
  leafHash: string;
  siblingPath: string[];
  treeRoot: string;
}

/**
 * AML-specific inclusion proof with entry index
 */
export interface AmlInclusionProof {
  entryIndex: number;
  treeSize: number;
  leafHash: string;
  siblings: AmlProofStep[];
  treeRoot: string;
}

/**
 * Single step in Merkle proof path
 */
export interface AmlProofStep {
  hash: string;
  position: string; // LEFT | RIGHT
}

// Legacy types for backward compatibility with existing UI
export interface InvestigationSummary {
  caseId: string;
  status: string;
  outcomeType: string;
  transactionId: string;
  originAccount: string;
  destinationAccount: string;
  amount: number;
  currency: string;
  flagReason: string;
  riskScore: number;
  createdAt: string;
}

export interface TrustScoreEntry {
  agentId: string;
  capabilityTag: string;
  score: number | null;
}

export interface FlowNode {
  capabilityTag: string;
  workerId: string;
  trustScoreAtRouting: number | null;
  status: string;
  timestamp: string;
}

export interface FlowEdge {
  from: number;
  to: number;
}

export interface InvestigationFlowResponse {
  nodes: FlowNode[];
  edges: FlowEdge[];
  parallelGroups: number[][];
}

export interface GateEntry {
  workItemId: string;
  actionType: string;
  gatePolicy: string;
  reversible: boolean;
  description: string;
  candidateGroups: string[];
  status: string;
  approvedBy: string | null;
  approvedAt: string | null;
  expiresAt: string | null;
}

/**
 * Prior context response from Layer 8 memory endpoint
 */
export interface PriorContextResponse {
  hasHistory: boolean;
  knownHighRisk: boolean;
  entityRiskCount: number;
  networkCount: number;
  patternCount: number;
  facts: PriorContextFact[];
}

/**
 * Individual prior context fact
 */
export interface PriorContextFact {
  domain: string; // ENTITY_RISK | NETWORK | PATTERN
  text: string;
  createdAt: string; // ISO 8601 timestamp
  confidence: string | null;
}

export interface SarQualityReport {
  seeded: OutcomeSegment;
  unseeded: OutcomeSegment;
  bySeedCount: SeedCountBucket[];
  totalCases: number;
}

export interface OutcomeSegment {
  total: number;
  upheld: number;
  notUpheld: number;
  upheldRate: number;
}

export interface SeedCountBucket {
  range: string;
  total: number;
  upheldRate: number;
}
