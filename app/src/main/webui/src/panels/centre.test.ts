import { describe, it, expect } from 'vitest';

const LEFT_DOCK_PANELS = new Set(['aml-investigation-nav', 'aml-worker-nav', 'aml-work-queue-nav']);
const PANEL_TO_MODE: Record<string, string> = {
  'aml-investigation-nav': 'investigations',
  'aml-worker-nav': 'worker-tasks',
  'aml-work-queue-nav': 'work-queue',
};

function resolveMode(panelId: string, visible: boolean, currentMode: string): string {
  if (!visible || !LEFT_DOCK_PANELS.has(panelId)) return currentMode;
  return PANEL_TO_MODE[panelId] ?? 'investigations';
}

const CALLER_REF_PATTERNS: [RegExp, number][] = [
  [/^case:(.+)\/gate:.+$/, 1],
  [/^aml:investigation:(.+)$/, 1],
];

function extractCaseId(callerRef: string): string | null {
  for (const [pattern, group] of CALLER_REF_PATTERNS) {
    const match = callerRef.match(pattern);
    if (match?.[group]) return match[group];
  }
  return null;
}

describe('centre panel mode switching', () => {
  it('defaults to investigations', () => {
    expect(resolveMode('aml-investigation-nav', true, 'investigations')).toBe('investigations');
  });

  it('switches to worker-tasks on worker nav activation', () => {
    expect(resolveMode('aml-worker-nav', true, 'investigations')).toBe('worker-tasks');
  });

  it('switches to work-queue on work queue nav activation', () => {
    expect(resolveMode('aml-work-queue-nav', true, 'investigations')).toBe('work-queue');
  });

  it('ignores non-left-dock panels', () => {
    expect(resolveMode('aml-audit-dock', true, 'investigations')).toBe('investigations');
  });

  it('ignores hidden events', () => {
    expect(resolveMode('aml-worker-nav', false, 'investigations')).toBe('investigations');
  });
});

describe('callerRef caseId extraction', () => {
  it('extracts caseId from gate callerRef', () => {
    expect(extractCaseId('case:abc-123/gate:gate-456')).toBe('abc-123');
  });

  it('extracts caseId from compliance review callerRef', () => {
    expect(extractCaseId('aml:investigation:def-789')).toBe('def-789');
  });

  it('returns null for unknown format', () => {
    expect(extractCaseId('unknown-format')).toBeNull();
  });

  it('prefers gate format over compliance format', () => {
    expect(extractCaseId('case:id-1/gate:g-1')).toBe('id-1');
  });
});
