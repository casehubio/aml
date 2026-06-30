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
  createdAt: string;
}

export interface ThroughputMetrics {
  totalInvestigations: number;
  byStatus: Record<string, number>;
  byFlagReason: Record<string, number>;
  byOutcomeType: Record<string, number>;
}

export interface TrustScoreEntry {
  agentId: string;
  capabilityTag: string;
  score: number | null;
}

export interface GateMetrics {
  totalGates: number;
  byActionType: Record<string, number>;
  byStatus: Record<string, number>;
  averageApprovalTimeSeconds: number | null;
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
