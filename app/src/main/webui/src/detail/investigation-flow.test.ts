import { describe, it, expect } from 'vitest';
import type { InvestigationFlowResponse, FlowNode } from '../types.js';

interface PlanItemSnapshot {
  id: string;
  bindingName: string;
  status: string;
  createdAt: string;
}

interface TrustScoreSnapshot {
  bindingName: string;
  workerId: string;
  score: number;
}

interface CaseRuntimeState {
  planItems: PlanItemSnapshot[];
  milestones: [];
  timestamp: string;
  caseStatus?: string;
  trustScores?: TrustScoreSnapshot[];
  adaptiveDecisions?: unknown[];
  parallelGroups?: string[][];
}

const STATUS_MAP: Record<string, string> = {
  scheduled: 'RUNNING',
  completed: 'COMPLETED',
  failed: 'FAULTED',
  declined: 'REJECTED',
};

function flowToRuntimeState(flow: InvestigationFlowResponse, caseStatus?: string): CaseRuntimeState {
  const planItems: PlanItemSnapshot[] = flow.nodes.map(n => ({
    id: `binding:${n.capabilityTag}`,
    bindingName: n.capabilityTag,
    status: STATUS_MAP[n.status] ?? 'PENDING',
    createdAt: n.timestamp,
  }));

  const trustScores: TrustScoreSnapshot[] = flow.nodes
    .filter(n => n.trustScoreAtRouting != null)
    .map(n => ({
      bindingName: n.capabilityTag,
      workerId: n.workerId,
      score: n.trustScoreAtRouting!,
    }));

  const parallelGroups: string[][] = flow.parallelGroups.map(group =>
    group.map(idx => flow.nodes[idx]!.capabilityTag)
  );

  return {
    planItems,
    milestones: [],
    timestamp: new Date().toISOString(),
    caseStatus,
    trustScores: trustScores.length > 0 ? trustScores : undefined,
    parallelGroups: parallelGroups.length > 0 ? parallelGroups : undefined,
  };
}

const sampleFlow: InvestigationFlowResponse = {
  nodes: [
    { capabilityTag: 'entity-resolution', workerId: 'entity-resolution-agent', trustScoreAtRouting: 0.85, status: 'completed', timestamp: '2026-09-01T10:00:00Z' },
    { capabilityTag: 'pattern-analysis', workerId: 'pattern-analysis-agent', trustScoreAtRouting: 0.72, status: 'completed', timestamp: '2026-09-01T10:01:00Z' },
    { capabilityTag: 'osint-screening', workerId: 'osint-screening-agent-senior', trustScoreAtRouting: 0.91, status: 'completed', timestamp: '2026-09-01T10:01:00Z' },
    { capabilityTag: 'investigation-triage', workerId: 'investigation-triage-agent', trustScoreAtRouting: null, status: 'completed', timestamp: '2026-09-01T10:02:00Z' },
    { capabilityTag: 'sar-drafting', workerId: 'sar-drafting-agent-senior', trustScoreAtRouting: 0.88, status: 'scheduled', timestamp: '2026-09-01T10:03:00Z' },
  ],
  edges: [{ from: 0, to: 1 }, { from: 0, to: 2 }, { from: 1, to: 3 }, { from: 2, to: 3 }, { from: 3, to: 4 }],
  parallelGroups: [[1, 2]],
};

describe('flowToRuntimeState', () => {
  it('maps all nodes to planItems', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.planItems).toHaveLength(5);
  });

  it('maps completed status correctly', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.planItems[0]!.status).toBe('COMPLETED');
  });

  it('maps scheduled status to RUNNING', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.planItems[4]!.status).toBe('RUNNING');
  });

  it('uses bindingName from capabilityTag', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.planItems[0]!.bindingName).toBe('entity-resolution');
    expect(state.planItems[0]!.id).toBe('binding:entity-resolution');
  });

  it('extracts trust scores from nodes with non-null values', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.trustScores).toHaveLength(4);
    expect(state.trustScores![0]).toEqual({
      bindingName: 'entity-resolution',
      workerId: 'entity-resolution-agent',
      score: 0.85,
    });
  });

  it('excludes nodes with null trustScoreAtRouting', () => {
    const state = flowToRuntimeState(sampleFlow);
    const triageTrust = state.trustScores!.find(t => t.bindingName === 'investigation-triage');
    expect(triageTrust).toBeUndefined();
  });

  it('maps parallel groups from node indices to binding names', () => {
    const state = flowToRuntimeState(sampleFlow);
    expect(state.parallelGroups).toEqual([['pattern-analysis', 'osint-screening']]);
  });

  it('passes caseStatus through', () => {
    const state = flowToRuntimeState(sampleFlow, 'COMPLETED');
    expect(state.caseStatus).toBe('COMPLETED');
  });
});
