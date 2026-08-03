import { describe, it, expect } from 'vitest';
import { amlInvestigationTimelineStrategy } from './aml-investigation-timeline.js';
import type { AuditTrailEntry } from '../types.js';

function entry(overrides: Partial<AuditTrailEntry> & { discriminator: string }): AuditTrailEntry {
  return {
    entryId: `entry-${Math.random().toString(36).slice(2, 10)}`,
    entryType: 'EVENT',
    discriminator: overrides.discriminator,
    actorId: overrides.actorId ?? 'aml-orchestrator',
    actorRole: overrides.actorRole ?? 'AmlInvestigationOrchestrator',
    occurredAt: overrides.occurredAt ?? '2026-08-01T10:00:00Z',
    causedByEntryId: overrides.causedByEntryId ?? null,
    digest: overrides.digest ?? 'abc123',
    sequenceNumber: overrides.sequenceNumber ?? 1,
    domainFields: overrides.domainFields ?? {},
  };
}

describe('amlInvestigationTimelineStrategy', () => {
  const strategy = amlInvestigationTimelineStrategy();

  describe('toNodes', () => {
    it('returns empty array for empty input', () => {
      expect(strategy.toNodes([])).toEqual([]);
    });

    it('maps AML_CASE_OPENED to lifecycle node', () => {
      const entries = [entry({
        discriminator: 'AML_CASE_OPENED',
        domainFields: { transactionId: 'TXN-001', originAccountId: 'A', destinationAccountId: 'B' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('Investigation Opened');
      expect(nodes[0]!.category).toBe('lifecycle');
      expect(nodes[0]!.status).toBe('completed');
      expect(nodes[0]!.actor).toBe('aml-orchestrator');
      expect(nodes[0]!.timestamp).toBe('2026-08-01T10:00:00Z');
    });

    it('pairs QHORUS_MESSAGE COMMAND+DONE into one completed agent node', () => {
      const entries = [
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 1,
          occurredAt: '2026-08-01T10:01:00Z',
          domainFields: { messageType: 'COMMAND', topic: 'entity-resolution', correlationId: 'corr-1' },
        }),
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 3,
          occurredAt: '2026-08-01T10:02:00Z',
          actorId: 'entity-resolution-agent',
          domainFields: { messageType: 'DONE', topic: 'entity-resolution', correlationId: 'corr-1' },
        }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('Entity Resolution completed');
      expect(nodes[0]!.category).toBe('agent');
      expect(nodes[0]!.status).toBe('completed');
      expect(nodes[0]!.actor).toBe('entity-resolution-agent');
    });

    it('pairs QHORUS_MESSAGE COMMAND+DECLINE into skipped agent node', () => {
      const entries = [
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 1,
          domainFields: { messageType: 'COMMAND', topic: 'osint-screening', correlationId: 'corr-2' },
        }),
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 2,
          actorId: 'osint-screening-agent',
          domainFields: { messageType: 'DECLINE', topic: 'osint-screening', correlationId: 'corr-2' },
        }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('OSINT Screening declined');
      expect(nodes[0]!.status).toBe('skipped');
    });

    it('unpaired COMMAND becomes active node', () => {
      const entries = [
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 1,
          domainFields: { messageType: 'COMMAND', topic: 'pattern-analysis', correlationId: 'corr-3' },
        }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('Pattern Analysis in progress');
      expect(nodes[0]!.status).toBe('active');
    });

    it('does not pair COMMANDs for different correlationIds', () => {
      const entries = [
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 1,
          domainFields: { messageType: 'COMMAND', topic: 'entity-resolution', correlationId: 'corr-A' },
        }),
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 2,
          domainFields: { messageType: 'DONE', topic: 'pattern-analysis', correlationId: 'corr-B' },
        }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(2);
    });

    it('maps CASE engine entry with COMPLETED status', () => {
      const entries = [entry({
        discriminator: 'CASE',
        domainFields: { caseStatus: 'COMPLETED', eventType: 'CASE_COMPLETED' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('Case COMPLETED');
      expect(nodes[0]!.category).toBe('lifecycle');
    });

    it('maps WORKER_DECISION to agent routed node', () => {
      const entries = [entry({
        discriminator: 'WORKER_DECISION',
        domainFields: { workerId: 'entity-resolution-agent', capabilityTag: 'entity-resolution' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes).toHaveLength(1);
      expect(nodes[0]!.label).toBe('Entity Resolution routed');
      expect(nodes[0]!.category).toBe('agent');
      expect(nodes[0]!.actor).toBe('entity-resolution-agent');
    });

    it('maps AML_SAR_OFFICER_REVIEWED with observer-failed actorRole', () => {
      const entries = [entry({
        discriminator: 'AML_SAR_OFFICER_REVIEWED',
        actorRole: 'ComplianceOfficer-observer-failed',
        domainFields: { reviewDecision: 'approved', actorRole: 'ComplianceOfficer-observer-failed' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toContain('observer failure');
      expect(nodes[0]!.status).toBe('failed');
    });

    it('maps AML_SAR_OFFICER_REVIEWED approved', () => {
      const entries = [entry({
        discriminator: 'AML_SAR_OFFICER_REVIEWED',
        actorId: 'officer-001',
        domainFields: { reviewDecision: 'approved', actorRole: 'ComplianceOfficer' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('SAR Officer: approved');
      expect(nodes[0]!.status).toBe('completed');
      expect(nodes[0]!.category).toBe('milestone');
    });

    it('maps AML_SAR_OFFICER_REVIEWED rejected as failed', () => {
      const entries = [entry({
        discriminator: 'AML_SAR_OFFICER_REVIEWED',
        domainFields: { reviewDecision: 'rejected', actorRole: 'ComplianceOfficer' },
      })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.status).toBe('failed');
    });

    it('maps AML_COMPLIANCE_REVIEW', () => {
      const entries = [entry({ discriminator: 'AML_COMPLIANCE_REVIEW', domainFields: { taskId: 't-1' } })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('Compliance Review Opened');
      expect(nodes[0]!.category).toBe('milestone');
    });

    it('maps AML_CASE_PROFILE to orchestration', () => {
      const entries = [entry({ discriminator: 'AML_CASE_PROFILE' })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('Case Profile Retained');
      expect(nodes[0]!.category).toBe('orchestration');
    });

    it('maps AML_CBR_ADVISORY to orchestration', () => {
      const entries = [entry({ discriminator: 'AML_CBR_ADVISORY' })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('CBR Path Advisory');
      expect(nodes[0]!.category).toBe('orchestration');
    });

    it('orders nodes by sequenceNumber with occurredAt tiebreaker', () => {
      const entries = [
        entry({ discriminator: 'AML_COMPLIANCE_REVIEW', sequenceNumber: 3, occurredAt: '2026-08-01T10:02:00Z', domainFields: { taskId: 't-1' } }),
        entry({ discriminator: 'AML_CASE_OPENED', sequenceNumber: 1, occurredAt: '2026-08-01T10:00:00Z', domainFields: { transactionId: 'T', originAccountId: 'A', destinationAccountId: 'B' } }),
        entry({ discriminator: 'CASE', sequenceNumber: 2, occurredAt: '2026-08-01T10:01:00Z', domainFields: { caseStatus: 'STARTED' } }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('Investigation Opened');
      expect(nodes[1]!.label).toBe('Case STARTED');
      expect(nodes[2]!.label).toBe('Compliance Review Opened');
    });

    it('unknown discriminator uses raw string as label', () => {
      const entries = [entry({ discriminator: 'SOME_FUTURE_TYPE' })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('SOME_FUTURE_TYPE');
      expect(nodes[0]!.category).toBe('orchestration');
    });

    it('unknown capability falls back to title-cased slug', () => {
      const entries = [
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 1,
          domainFields: { messageType: 'COMMAND', topic: 'beneficial-ownership', correlationId: 'corr-X' },
        }),
        entry({
          discriminator: 'QHORUS_MESSAGE',
          sequenceNumber: 2,
          domainFields: { messageType: 'DONE', topic: 'beneficial-ownership', correlationId: 'corr-X' },
        }),
      ];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.label).toBe('Beneficial Ownership completed');
    });

    it('grouped node detail contains both entries', () => {
      const cmd = entry({
        discriminator: 'QHORUS_MESSAGE',
        sequenceNumber: 1,
        domainFields: { messageType: 'COMMAND', topic: 'sar-drafting', correlationId: 'corr-D' },
      });
      const done = entry({
        discriminator: 'QHORUS_MESSAGE',
        sequenceNumber: 2,
        domainFields: { messageType: 'DONE', topic: 'sar-drafting', correlationId: 'corr-D' },
      });
      const nodes = strategy.toNodes([cmd, done]);
      const detail = nodes[0]!.detail as AuditTrailEntry[];
      expect(detail).toHaveLength(2);
    });

    it('standalone node detail is the single entry', () => {
      const entries = [entry({ discriminator: 'AML_CASE_OPENED', domainFields: { transactionId: 'T', originAccountId: 'A', destinationAccountId: 'B' } })];
      const nodes = strategy.toNodes(entries);
      expect(nodes[0]!.detail).toHaveProperty('entryId');
    });
  });

  describe('metadata', () => {
    it('defaultLayout is vertical', () => {
      expect(strategy.defaultLayout).toBe('vertical');
    });

    it('filterCategories includes all four categories', () => {
      expect(strategy.filterCategories).toEqual(['lifecycle', 'agent', 'milestone', 'orchestration']);
    });
  });
});
