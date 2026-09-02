import { describe, it, expect } from 'vitest';

function buildEndpoint(template: string, caseId: string | null): string | null {
  if (!caseId) return null;
  return template.replace('{caseId}', caseId);
}

function extractCaseIdFromEvent(detail: { topic?: string; caseId?: string }): string | null {
  if (detail.topic !== 'investigation-context') return null;
  return detail.caseId ?? null;
}

describe('dock panel endpoint construction', () => {
  it('builds audit trail endpoint from caseId', () => {
    expect(buildEndpoint('/api/investigations/{caseId}/audit-trail', 'case-123'))
      .toBe('/api/investigations/case-123/audit-trail');
  });

  it('builds compliance evidence endpoint from caseId', () => {
    expect(buildEndpoint('/api/investigations/{caseId}/compliance-evidence', 'case-456'))
      .toBe('/api/investigations/case-456/compliance-evidence');
  });

  it('builds routing endpoint from caseId', () => {
    expect(buildEndpoint('/api/investigations/{caseId}/routing', 'case-789'))
      .toBe('/api/investigations/case-789/routing');
  });

  it('builds findings endpoint from caseId', () => {
    expect(buildEndpoint('/api/investigations/{caseId}/findings', 'case-abc'))
      .toBe('/api/investigations/case-abc/findings');
  });

  it('returns null when caseId is null', () => {
    expect(buildEndpoint('/api/investigations/{caseId}/audit-trail', null)).toBeNull();
  });
});

describe('investigation-context event filtering', () => {
  it('extracts caseId from investigation-context event', () => {
    expect(extractCaseIdFromEvent({ topic: 'investigation-context', caseId: 'test-id' }))
      .toBe('test-id');
  });

  it('ignores events for other topics', () => {
    expect(extractCaseIdFromEvent({ topic: 'case', caseId: 'test-id' })).toBeNull();
  });

  it('returns null when caseId is missing', () => {
    expect(extractCaseIdFromEvent({ topic: 'investigation-context' })).toBeNull();
  });

  it('ignores events with no topic', () => {
    expect(extractCaseIdFromEvent({ caseId: 'test-id' })).toBeNull();
  });
});
