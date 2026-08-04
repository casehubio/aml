/**
 * Mock data for the AML workbench showcase.
 * Each object matches the JSON response shape of its corresponding REST endpoint.
 */

export const INVESTIGATIONS = {
  items: [
    { caseId: 'c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6', status: 'completed', outcomeType: 'SAR_FILED', transactionId: 'TXN-2024-001', originAccount: 'ACC-CORP-7721', destinationAccount: 'ACC-SHELL-0092', amount: 245000, currency: 'USD', flagReason: 'Structured layering — 12 sub-threshold deposits over 48h', riskScore: 0.89, createdAt: '2024-11-15T09:23:00Z' },
    { caseId: 'd2b3c4d5-e6f7-a8b9-c0d1-e2f3a4b5c6d7', status: 'completed', outcomeType: 'CLEARED', transactionId: 'TXN-2024-002', originAccount: 'ACC-RETAIL-1103', destinationAccount: 'ACC-SAVINGS-4456', amount: 89500, currency: 'EUR', flagReason: 'Large cash deposit — single transaction over threshold', riskScore: 0.42, createdAt: '2024-11-16T14:45:00Z' },
    { caseId: 'e3c4d5e6-f7a8-b9c0-d1e2-f3a4b5c6d7e8', status: 'in_progress', outcomeType: null, transactionId: 'TXN-2024-003', originAccount: 'ACC-PEP-8834', destinationAccount: 'ACC-TRUST-2201', amount: 1200000, currency: 'GBP', flagReason: 'PEP — politically exposed person, high-value transfer', riskScore: 0.95, createdAt: '2024-11-17T08:12:00Z' },
    { caseId: 'f4d5e6f7-a8b9-c0d1-e2f3-a4b5c6d7e8f9', status: 'failed', outcomeType: null, transactionId: 'TXN-2024-004', originAccount: 'ACC-CORP-3310', destinationAccount: 'ACC-OFFSHORE-7788', amount: 567000, currency: 'USD', flagReason: 'Rapid cross-border transfer pattern — 5 jurisdictions in 24h', riskScore: 0.78, createdAt: '2024-11-18T16:30:00Z' },
    { caseId: 'a5e6f7a8-b9c0-d1e2-f3a4-b5c6d7e8f9a0', status: 'completed', outcomeType: 'SAR_FILED', transactionId: 'TXN-2024-005', originAccount: 'ACC-SMURFER-2291', destinationAccount: 'ACC-CONSOLIDATION-5500', amount: 49900, currency: 'USD', flagReason: 'Smurfing — 10 deposits of $4,990 across branches', riskScore: 0.82, createdAt: '2024-11-19T11:00:00Z' },
    { caseId: 'b6f7a8b9-c0d1-e2f3-a4b5-c6d7e8f9a0b1', status: 'suspended', outcomeType: null, transactionId: 'TXN-2024-006', originAccount: 'ACC-ESTATE-9912', destinationAccount: 'ACC-NOMINEE-3344', amount: 3400000, currency: 'CHF', flagReason: 'Beneficial ownership obscured — nominee structure', riskScore: 0.91, createdAt: '2024-11-20T07:45:00Z' },
  ],
  total: 6,
  page: 0,
  pageSize: 25,
};

export const LAYER6_RESPONSE = {
  caseId: 'c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6',
  status: 'completed',
  routingDecisions: [
    { capabilityTag: 'entity-resolution', selectedWorker: 'entity-resolution-agent', trustScore: 0.92 },
    { capabilityTag: 'pattern-analysis', selectedWorker: 'pattern-analysis-agent', trustScore: 0.87 },
    { capabilityTag: 'osint-screening', selectedWorker: 'osint-screening-agent-senior', trustScore: 0.95 },
    { capabilityTag: 'sar-drafting', selectedWorker: 'sar-drafting-agent-senior', trustScore: 0.91 },
    { capabilityTag: 'senior-analyst-review', selectedWorker: 'senior-analyst-agent', trustScore: 0.88 },
  ],
  outcome: { type: 'SAR_FILED', reason: 'SAR filed with FinCEN — reference FIN-2024-00847' },
  failureContext: null,
};

export const PRIOR_CONTEXT = {
  hasHistory: true,
  knownHighRisk: true,
  entityRiskCount: 3,
  networkCount: 2,
  patternCount: 4,
  facts: [
    { domain: 'ENTITY_RISK', text: '2 previous SARs filed (2023-Q4, 2024-Q1)', createdAt: '2024-08-20T00:00:00Z', confidence: '0.92' },
    { domain: 'NETWORK', text: 'Shell company network — Cayman Islands, Cyprus jurisdictions', createdAt: '2024-07-15T00:00:00Z', confidence: '0.87' },
    { domain: 'PATTERN', text: 'Recurring structured deposits below CTR threshold', createdAt: '2024-09-10T00:00:00Z', confidence: '0.95' },
  ],
};

export const FINDINGS = {
  entityResolution: {
    status: 'COMPLETED',
    result: { entityId: 'ENT-CORP-7721', ownershipChain: 'Acme Holdings → Apex Ltd → Shell-092 (BVI)', entityType: 'CORPORATE', riskScore: 0.89 },
  },
  patternAnalysis: {
    status: 'COMPLETED',
    result: { structuringDetected: true, description: '12 deposits between $9,800-$9,999 over 48 hours across 4 branch locations. Classic structuring pattern to avoid CTR filing threshold.' },
  },
  osintScreening: {
    status: 'COMPLETED',
    result: { pepHit: false, sanctionsHit: false, screeningLevel: 'ENHANCED', adverseMedia: 'No adverse media hits' },
  },
  sarNarrative: {
    status: 'COMPLETED',
    result: { sarNarrative: 'Subject ACC-CORP-7721 (Acme Holdings) conducted 12 structured deposits totalling $245,000 across 4 branch locations over a 48-hour period. Each deposit was calibrated below the $10,000 CTR threshold. Beneficial ownership traces through a BVI shell company (Shell-092) with opaque nominee structures. Prior investigation history shows 2 previous SARs filed in the last 12 months. Pattern is consistent with layering phase of money laundering.' },
  },
};

export const GATES = {
  gates: [
    { workItemId: 'wi-gate-001', actionType: 'sar.filing', gatePolicy: 'ALWAYS', reversible: false, description: 'SAR filing requires MLRO approval before submission to FinCEN', candidateGroups: ['aml-mlro'], status: 'COMPLETED', approvedBy: 'mlro-officer-001', approvedAt: '2024-11-15T14:30:00Z', expiresAt: null },
    { workItemId: 'wi-gate-002', actionType: 'entity.link.creation', gatePolicy: 'RISK_SCORE_THRESHOLD', reversible: true, description: 'Entity link proposals above risk threshold require compliance review', candidateGroups: ['aml-compliance'], status: 'COMPLETED', approvedBy: 'compliance-analyst-003', approvedAt: '2024-11-15T10:15:00Z', expiresAt: null },
  ],
};

export const COMPLIANCE_EVIDENCE = {
  caseId: 'c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6',
  generatedAt: '2024-11-15T15:00:00Z',
  auditChain: { id: 'FINCEN-AUDIT-001', citation: 'FinCEN 31 CFR §1020.320', mechanism: 'Tamper-evident ledger with Merkle inclusion proofs', status: 'MET', treeRoot: 'sha256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', chainVerified: true, events: [{ entryId: 'ae-001', eventType: 'CASE_OPENED', actorId: 'aml-orchestrator', actorRole: 'SYSTEM', occurredAt: '2024-11-15T09:23:00Z', causedByEntryId: null, digest: 'sha256:7f3a8b2c...', inclusionProof: { entryIndex: 0, treeSize: 7, leafHash: 'sha256:7f3a8b2c9d4e5f6a1b2c3d4e5f6a7b8c', siblings: [{ hash: 'sha256:4e9d1f7a2b3c4d5e', position: 'RIGHT' }], treeRoot: 'sha256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6' } }, { entryId: 'ae-006', eventType: 'COMPLIANCE_REVIEW_OPENED', actorId: 'aml-orchestrator', actorRole: 'SYSTEM', occurredAt: '2024-11-15T14:00:00Z', causedByEntryId: 'ae-005', digest: 'sha256:9e8f7d6c...', inclusionProof: { entryIndex: 5, treeSize: 7, leafHash: 'sha256:9e8f7d6c5b4a3f2e', siblings: [{ hash: 'sha256:3c4d5e6f7a8b9c0d', position: 'LEFT' }], treeRoot: 'sha256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6' } }, { entryId: 'ae-007', eventType: 'SAR_OFFICER_REVIEWED', actorId: 'mlro-officer-001', actorRole: 'HUMAN', occurredAt: '2024-11-15T14:30:00Z', causedByEntryId: 'ae-006', digest: 'sha256:3c4d5e6f...', inclusionProof: { entryIndex: 6, treeSize: 7, leafHash: 'sha256:3c4d5e6f7a8b9c0d', siblings: [{ hash: 'sha256:9e8f7d6c5b4a3f2e', position: 'RIGHT' }], treeRoot: 'sha256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6' } }] },
  sla: { id: 'FINCEN-SLA-001', citation: 'FinCEN 30-day filing requirement', mechanism: 'WorkItem with claimDeadline + auto-escalation', status: 'MET', workItemId: 'wi-001', claimDeadline: '2024-12-15T09:23:00Z', completedAt: '2024-11-15T14:30:00Z', slaMet: true, candidateGroups: ['compliance-officers'], escalationPolicy: 'Escalate to head of compliance after 30 days' },
  trustRouting: { id: 'FATF-TRUST-001', citation: 'FATF Recommendation 15 — trust-weighted agent selection', mechanism: 'Bayesian Beta trust scoring with SAR outcome attestations', status: 'MET' },
  gdprErasure: { id: 'GDPR-ART17-001', citation: 'GDPR Article 17 — Right to Erasure', mechanism: 'LedgerErasureService + identity tokenisation', status: 'MET', tokenisationEnabled: true, erasureReceiptEnabled: true, erasureReceiptCount: 0, erasureEndpoint: '/api/investigations/{caseId}/erasure' },
  signature: 'sha256:a1b2c3d4e5f6...',
};

export const AUDIT_TRAIL = [
  { entryId: 'ae-001', entryType: 'AML_CASE_OPENED', actorId: 'aml-orchestrator', actorRole: 'SYSTEM', occurredAt: '2024-11-15T09:23:00Z', causedByEntryId: null, digest: 'sha256:7f3a8b2c...', sequenceNumber: 1 },
  { entryId: 'ae-002', entryType: 'WORKER_DECISION', actorId: 'entity-resolution-agent', actorRole: 'AGENT', occurredAt: '2024-11-15T09:23:05Z', causedByEntryId: 'ae-001', digest: 'sha256:4e9d1f7a...', sequenceNumber: 2 },
  { entryId: 'ae-003', entryType: 'WORKER_DECISION', actorId: 'pattern-analysis-agent', actorRole: 'AGENT', occurredAt: '2024-11-15T09:23:08Z', causedByEntryId: 'ae-001', digest: 'sha256:b2c8e4f1...', sequenceNumber: 3 },
  { entryId: 'ae-004', entryType: 'WORKER_DECISION', actorId: 'osint-screening-agent-senior', actorRole: 'AGENT', occurredAt: '2024-11-15T09:23:12Z', causedByEntryId: 'ae-001', digest: 'sha256:d5f2a8c3...', sequenceNumber: 4 },
  { entryId: 'ae-005', entryType: 'WORKER_DECISION', actorId: 'sar-drafting-agent-senior', actorRole: 'AGENT', occurredAt: '2024-11-15T09:24:00Z', causedByEntryId: 'ae-001', digest: 'sha256:1a3b5c7d...', sequenceNumber: 5 },
  { entryId: 'ae-006', entryType: 'COMPLIANCE_REVIEW_OPENED', actorId: 'aml-orchestrator', actorRole: 'SYSTEM', occurredAt: '2024-11-15T14:00:00Z', causedByEntryId: 'ae-005', digest: 'sha256:9e8f7d6c...', sequenceNumber: 6 },
  { entryId: 'ae-007', entryType: 'SAR_OFFICER_REVIEWED', actorId: 'mlro-officer-001', actorRole: 'HUMAN', occurredAt: '2024-11-15T14:30:00Z', causedByEntryId: 'ae-006', digest: 'sha256:3c4d5e6f...', sequenceNumber: 7 },
];

export const THROUGHPUT_METRICS = {
  totalInvestigations: 847,
  byStatus: { completed: 612, in_progress: 128, failed: 47, suspended: 32, cancelled: 28 },
  byFlagReason: { 'Structured deposits': 234, 'PEP transfers': 156, 'Cross-border patterns': 189, 'Large cash deposits': 142, 'Smurfing': 78, 'Nominee structures': 48 },
  byOutcomeType: { SAR_FILED: 287, CLEARED: 298, ESCALATED: 27 },
};

export const TRUST_SCORE_METRICS = {
  scores: [
    { agentId: 'sar-drafting-agent-senior', capabilityTag: 'sar-drafting', score: 0.909 },
    { agentId: 'sar-drafting-agent-junior', capabilityTag: 'sar-drafting', score: 0.200 },
    { agentId: 'entity-resolution-agent', capabilityTag: 'entity-resolution', score: 0.800 },
    { agentId: 'pattern-analysis-agent', capabilityTag: 'pattern-analysis', score: 0.800 },
    { agentId: 'osint-screening-agent-senior', capabilityTag: 'osint-screening', score: 0.818 },
    { agentId: 'osint-screening-agent', capabilityTag: 'osint-screening', score: 0.300 },
    { agentId: 'senior-analyst-agent', capabilityTag: 'senior-analyst-review', score: 0.909 },
  ],
};

export const GATE_METRICS = {
  totalGates: 342,
  byActionType: { 'sar.filing': 287, 'entity.link.creation': 34, 'law.enforcement.referral': 12, 'account.restriction': 9 },
  byStatus: { APPROVED: 318, REJECTED: 14, EXPIRED: 10 },
  averageApprovalTimeSeconds: 4320,
};

export const INTERVENTION_METRICS = {
  escalationCount: 27,
  manualOverrideCount: 14,
  declineRoutingCount: 43,
  gateRejectionCount: 14,
  averageResponseTimeSeconds: 2160,
  recentInterventions: [
    { type: 'ESCALATION', caseId: 'e3c4d5e6-f7a8-b9c0-d1e2-f3a4b5c6d7e8', reason: 'Compliance officer SLA breach — 30-day deadline exceeded', actor: 'system', occurredAt: '2024-11-18T14:00:00Z' },
    { type: 'DECLINE_REROUTE', caseId: 'f4d5e6f7-a8b9-c0d1-e2f3-a4b5c6d7e8f9', reason: 'OSINT agent declined — PEP clearance level insufficient', actor: 'osint-screening-agent', occurredAt: '2024-11-18T10:15:00Z' },
    { type: 'GATE_REJECTION', caseId: 'a5e6f7a8-b9c0-d1e2-f3a4-b5c6d7e8f9a0', reason: 'MLRO rejected SAR filing — insufficient evidence', actor: 'mlro-officer-001', occurredAt: '2024-11-17T16:45:00Z' },
    { type: 'MANUAL_OVERRIDE', caseId: 'c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6', reason: 'Senior analyst overrode pattern-analysis routing — requested senior agent', actor: 'senior-analyst-agent', occurredAt: '2024-11-16T11:30:00Z' },
    { type: 'ESCALATION', caseId: 'b6f7a8b9-c0d1-e2f3-a4b5-c6d7e8f9a0b1', reason: 'Investigation suspended — beneficial ownership chain unresolvable', actor: 'system', occurredAt: '2024-11-20T09:00:00Z' },
  ],
};

export const WORK_ITEMS: Record<string, unknown> = {
  'wi-001': {
    id: 'wi-001', title: 'Compliance review — SAR for TXN-2024-001', description: 'Review SAR narrative and approve or reject filing with FinCEN.',
    status: 'PENDING', priority: 'HIGH', category: 'SAR Review',
    candidateGroups: 'compliance-officers', assigneeId: null,
    callerRef: 'aml:investigation:c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6',
    createdAt: '2024-11-15T14:00:00Z', updatedAt: '2024-11-15T14:00:00Z',
    claimDeadline: '2024-12-15T14:00:00Z', expiresAt: null,
    completedAt: null, outcome: null,
    permittedOutcomes: [{ value: 'APPROVED', label: 'Approve SAR Filing' }, { value: 'REJECTED', label: 'Reject SAR Filing' }],
    inputDataSchema: null, payload: null, outputDataSchema: null,
  },
  'wi-002': {
    id: 'wi-002', title: 'Compliance review — SAR for TXN-2024-005', description: 'Review SAR narrative for smurfing pattern investigation.',
    status: 'ASSIGNED', priority: 'HIGH', category: 'SAR Review',
    candidateGroups: 'compliance-officers', assigneeId: 'officer-001',
    callerRef: 'aml:investigation:a5e6f7a8-b9c0-d1e2-f3a4-b5c6d7e8f9a0',
    createdAt: '2024-11-19T11:30:00Z', updatedAt: '2024-11-19T12:00:00Z',
    claimDeadline: '2024-12-19T11:30:00Z', expiresAt: null,
    completedAt: null, outcome: null,
    permittedOutcomes: [{ value: 'APPROVED', label: 'Approve SAR Filing' }, { value: 'REJECTED', label: 'Reject SAR Filing' }],
    inputDataSchema: null, payload: null, outputDataSchema: null,
  },
  'wi-003': {
    id: 'wi-003', title: 'Compliance review — SAR for TXN-2024-003', description: 'Review SAR narrative for PEP high-value transfer.',
    status: 'PENDING', priority: 'CRITICAL', category: 'SAR Review',
    candidateGroups: 'compliance-officers', assigneeId: null,
    callerRef: 'aml:investigation:e3c4d5e6-f7a8-b9c0-d1e2-f3a4b5c6d7e8',
    createdAt: '2024-11-17T09:00:00Z', updatedAt: '2024-11-17T09:00:00Z',
    claimDeadline: '2024-12-17T09:00:00Z', expiresAt: null,
    completedAt: null, outcome: null,
    permittedOutcomes: [{ value: 'APPROVED', label: 'Approve SAR Filing' }, { value: 'REJECTED', label: 'Reject SAR Filing' }],
    inputDataSchema: null, payload: null, outputDataSchema: null,
  },
};

export const INCLUSION_PROOF = {
  entryId: 'ae-001',
  entryIndex: 0,
  treeSize: 7,
  leafHash: 'sha256:7f3a8b2c9d4e5f6a1b2c3d4e5f6a7b8c',
  siblings: ['sha256:4e9d1f7a2b3c4d5e', 'sha256:f1a2b3c4d5e6f7a8', 'sha256:8c7d6e5f4a3b2c1d'],
  treeRoot: 'sha256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6',
};
