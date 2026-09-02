import { describe, it, expect } from 'vitest';
import type { ThroughputMetrics, GateMetrics } from '../types.js';

interface MetricDefinition {
  key: string;
  value: number | string;
  label: string;
  unit?: string;
  status?: 'normal' | 'warning' | 'critical';
}

function throughputToMetrics(m: ThroughputMetrics): MetricDefinition[] {
  const completed = m.byStatus['COMPLETED'] ?? 0;
  const inProgress = m.byStatus['IN_PROGRESS'] ?? 0;
  const failed = m.byStatus['FAILED'] ?? 0;
  return [
    { key: 'total', value: m.totalInvestigations, label: 'Total Investigations' },
    { key: 'completed', value: completed, label: 'Completed' },
    { key: 'in-progress', value: inProgress, label: 'In Progress' },
    { key: 'failed', value: failed, label: 'Failed', status: failed > 0 ? 'warning' : 'normal' },
  ];
}

function gateToMetrics(m: GateMetrics): MetricDefinition[] {
  const pending = m.byStatus['PENDING'] ?? 0;
  const approved = m.byStatus['COMPLETED'] ?? 0;
  const rejected = m.byStatus['REJECTED'] ?? 0;
  return [
    { key: 'total-gates', value: m.totalGates, label: 'Total Gates' },
    { key: 'pending', value: pending, label: 'Pending', status: pending > 3 ? 'warning' : 'normal' },
    { key: 'approved', value: approved, label: 'Approved' },
    { key: 'rejected', value: rejected, label: 'Rejected', status: rejected > 0 ? 'warning' : 'normal' },
    { key: 'avg-approval', value: m.averageApprovalTimeSeconds != null ? `${Math.round(m.averageApprovalTimeSeconds / 3600)}h` : '—', label: 'Avg Approval Time' },
  ];
}

describe('throughput metrics transformation', () => {
  const sample: ThroughputMetrics = {
    totalInvestigations: 42,
    byStatus: { COMPLETED: 30, IN_PROGRESS: 10, FAILED: 2 },
    byFlagReason: { HIGH_RISK_JURISDICTION: 25, STRUCTURING: 17 },
    byOutcomeType: {},
  };

  it('extracts total investigations', () => {
    const metrics = throughputToMetrics(sample);
    expect(metrics[0]).toEqual({ key: 'total', value: 42, label: 'Total Investigations' });
  });

  it('extracts status counts', () => {
    const metrics = throughputToMetrics(sample);
    expect(metrics[1]).toEqual({ key: 'completed', value: 30, label: 'Completed' });
    expect(metrics[2]).toEqual({ key: 'in-progress', value: 10, label: 'In Progress' });
  });

  it('marks failed as warning when nonzero', () => {
    const metrics = throughputToMetrics(sample);
    expect(metrics[3].status).toBe('warning');
  });

  it('marks failed as normal when zero', () => {
    const zero = { ...sample, byStatus: { COMPLETED: 42, IN_PROGRESS: 0, FAILED: 0 } };
    const metrics = throughputToMetrics(zero);
    expect(metrics[3].status).toBe('normal');
  });
});

describe('gate metrics transformation', () => {
  const sample: GateMetrics = {
    totalGates: 15,
    byActionType: { SAR_FILING: 8, ACCOUNT_RESTRICTION: 7 },
    byStatus: { COMPLETED: 12, PENDING: 2, REJECTED: 1 },
    averageApprovalTimeSeconds: 7200,
  };

  it('extracts total gates', () => {
    const metrics = gateToMetrics(sample);
    expect(metrics[0]).toEqual({ key: 'total-gates', value: 15, label: 'Total Gates' });
  });

  it('formats avg approval time in hours', () => {
    const metrics = gateToMetrics(sample);
    expect(metrics[4].value).toBe('2h');
  });

  it('shows dash when avg approval is null', () => {
    const noAvg = { ...sample, averageApprovalTimeSeconds: null };
    const metrics = gateToMetrics(noAvg);
    expect(metrics[4].value).toBe('—');
  });

  it('marks pending as warning above threshold', () => {
    const manyPending = { ...sample, byStatus: { ...sample.byStatus, PENDING: 5 } };
    const metrics = gateToMetrics(manyPending);
    expect(metrics[1].status).toBe('warning');
  });
});
