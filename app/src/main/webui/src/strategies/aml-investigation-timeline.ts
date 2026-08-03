import type { TimelineNode, TimelineStrategy } from '@casehubio/blocks-ui-blocks-timeline';
import type { AuditTrailEntry, QhorusMessageFields } from '../types.js';

const CAPABILITY_LABELS: Record<string, string> = {
  'entity-resolution': 'Entity Resolution',
  'pattern-analysis': 'Pattern Analysis',
  'osint-screening': 'OSINT Screening',
  'sar-drafting': 'SAR Drafting',
};

function capabilityLabel(slug: string | null | undefined): string {
  if (!slug) return 'Unknown';
  if (CAPABILITY_LABELS[slug]) return CAPABILITY_LABELS[slug]!;
  return slug.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

interface MilestoneGroup {
  command: AuditTrailEntry;
  terminal?: AuditTrailEntry;
}

function sortEntries(entries: AuditTrailEntry[]): AuditTrailEntry[] {
  return [...entries].sort((a, b) => {
    if (a.sequenceNumber !== b.sequenceNumber) return a.sequenceNumber - b.sequenceNumber;
    if (a.occurredAt !== b.occurredAt) return a.occurredAt < b.occurredAt ? -1 : 1;
    return a.entryId < b.entryId ? -1 : 1;
  });
}

function groupQhorusMessages(entries: AuditTrailEntry[]): { milestones: MilestoneGroup[]; rest: AuditTrailEntry[] } {
  const qhorus = entries.filter(e => e.discriminator === 'QHORUS_MESSAGE');
  const rest = entries.filter(e => e.discriminator !== 'QHORUS_MESSAGE');

  const byCorrelation = new Map<string, AuditTrailEntry[]>();
  for (const e of qhorus) {
    const corrId = (e.domainFields as QhorusMessageFields).correlationId;
    if (!corrId) { rest.push(e); continue; }
    const group = byCorrelation.get(corrId) ?? [];
    group.push(e);
    byCorrelation.set(corrId, group);
  }

  const milestones: MilestoneGroup[] = [];
  for (const [, group] of byCorrelation) {
    const command = group.find(e => (e.domainFields as QhorusMessageFields).messageType === 'COMMAND');
    if (!command) { rest.push(...group); continue; }
    const terminal = group.find(e => {
      const mt = (e.domainFields as QhorusMessageFields).messageType;
      return mt === 'DONE' || mt === 'DECLINE';
    });
    milestones.push({ command, terminal });
  }

  return { milestones, rest };
}

function qhorusMilestoneToNode(m: MilestoneGroup): TimelineNode {
  const topic = (m.command.domainFields as QhorusMessageFields).topic;
  const label = capabilityLabel(topic);
  const corrId = (m.command.domainFields as QhorusMessageFields).correlationId;

  if (!m.terminal) {
    return {
      key: corrId ?? m.command.entryId,
      label: `${label} in progress`,
      status: 'active',
      timestamp: m.command.occurredAt,
      actor: m.command.actorId,
      detail: [m.command],
      category: 'agent',
    };
  }

  const terminalType = (m.terminal.domainFields as QhorusMessageFields).messageType;
  const isDecline = terminalType === 'DECLINE';

  return {
    key: corrId ?? m.terminal.entryId,
    label: `${label} ${isDecline ? 'declined' : 'completed'}`,
    status: isDecline ? 'skipped' : 'completed',
    timestamp: m.terminal.occurredAt,
    actor: m.terminal.actorId,
    detail: [m.command, m.terminal],
    category: 'agent',
  };
}

function standaloneToNode(e: AuditTrailEntry): TimelineNode {
  switch (e.discriminator) {
    case 'AML_CASE_OPENED':
      return { key: e.entryId, label: 'Investigation Opened', status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'lifecycle' };

    case 'AML_COMPLIANCE_REVIEW':
      return { key: e.entryId, label: 'Compliance Review Opened', status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'milestone' };

    case 'AML_SAR_OFFICER_REVIEWED': {
      const actorRole = (e.domainFields as { actorRole?: string }).actorRole ?? e.actorRole;
      if (actorRole?.includes('observer-failed')) {
        return { key: e.entryId, label: 'SAR Review — observer failure', status: 'failed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'milestone' };
      }
      const decision = (e.domainFields as { reviewDecision?: string }).reviewDecision ?? 'unknown';
      return { key: e.entryId, label: `SAR Officer: ${decision}`, status: decision === 'rejected' ? 'failed' : 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'milestone' };
    }

    case 'CASE': {
      const caseStatus = (e.domainFields as { caseStatus?: string }).caseStatus ?? 'unknown';
      return { key: e.entryId, label: `Case ${caseStatus}`, status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'lifecycle' };
    }

    case 'WORKER_DECISION': {
      const cap = (e.domainFields as { capabilityTag?: string }).capabilityTag;
      const wid = (e.domainFields as { workerId?: string }).workerId;
      return { key: e.entryId, label: `${capabilityLabel(cap)} routed`, status: 'completed', timestamp: e.occurredAt, actor: wid ?? e.actorId, detail: e, category: 'agent' };
    }

    case 'AML_CASE_PROFILE':
      return { key: e.entryId, label: 'Case Profile Retained', status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'orchestration' };

    case 'AML_CBR_ADVISORY':
      return { key: e.entryId, label: 'CBR Path Advisory', status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'orchestration' };

    default:
      return { key: e.entryId, label: e.discriminator, status: 'completed', timestamp: e.occurredAt, actor: e.actorId, detail: e, category: 'orchestration' };
  }
}

export function amlInvestigationTimelineStrategy(): TimelineStrategy<AuditTrailEntry[]> {
  return {
    toNodes(data: AuditTrailEntry[]): TimelineNode[] {
      const sorted = sortEntries(data);
      const { milestones, rest } = groupQhorusMessages(sorted);

      const qhorusNodes = milestones.map(qhorusMilestoneToNode);
      const standaloneNodes = rest.map(standaloneToNode);

      const allNodes = [...qhorusNodes, ...standaloneNodes];
      allNodes.sort((a, b) => {
        const ta = a.timestamp ?? '';
        const tb = b.timestamp ?? '';
        if (ta !== tb) return ta < tb ? -1 : 1;
        return a.key < b.key ? -1 : 1;
      });

      return allNodes;
    },
    defaultLayout: 'vertical',
    filterCategories: ['lifecycle', 'agent', 'milestone', 'orchestration'],
  };
}
