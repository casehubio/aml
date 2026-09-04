import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('aml-senior-analyst-workspace')
export class AmlSeniorAnalystWorkspace extends LitElement {
  @property({ attribute: false }) taskContext: any = null;

  @state() private _findings: any = null;
  @state() private _loading = false;

  static override styles = css`
    :host { display: block; padding: var(--pages-space-4, 16px); }
    .section { margin-bottom: var(--pages-space-4, 16px); }
    .section-title { font-size: var(--pages-font-size-sm, 13px); font-weight: 600; color: var(--pages-neutral-8, #404040); margin-bottom: var(--pages-space-2, 8px); text-transform: uppercase; letter-spacing: 0.5px; }
    .card { border: 1px solid var(--pages-neutral-4, #d4d4d4); border-radius: 8px; padding: var(--pages-space-3, 12px); margin-bottom: var(--pages-space-2, 8px); }
    .finding-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--pages-space-2, 8px); }
    .finding-label { font-size: var(--pages-font-size-sm, 13px); font-weight: 600; }
    .badge { padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
    .badge.completed { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #16a34a); }
    .badge.pending { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #d97706); }
    .badge.declined { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-8, #404040); }
    .summary { font-size: var(--pages-font-size-sm, 13px); color: var(--pages-neutral-11, #0a0a0a); line-height: 1.5; }
  `;

  override updated(changed: Map<string, unknown>) {
    if (changed.has('taskContext') && this.taskContext?.caseId) this._fetchFindings();
  }

  private async _fetchFindings() {
    this._loading = true;
    try {
      const res = await fetch(`/api/investigations/${this.taskContext.caseId}/findings`);
      if (res.ok) this._findings = await res.json();
    } finally { this._loading = false; }
  }

  private _renderFinding(label: string, finding: any) {
    if (!finding) return nothing;
    const status = finding.status ?? 'PENDING';
    return html`
      <div class="card">
        <div class="finding-header">
          <span class="finding-label">${label}</span>
          <span class="badge ${status.toLowerCase()}">${status}</span>
        </div>
        ${finding.result ? html`<div class="summary">${JSON.stringify(finding.result, null, 2).substring(0, 200)}...</div>` : nothing}
      </div>
    `;
  }

  override render() {
    if (this._loading) return html`<div>Loading consolidated findings...</div>`;
    if (!this._findings) return html`<div>No findings available</div>`;
    return html`
      <div class="section">
        <div class="section-title">Consolidated Findings</div>
        ${this._renderFinding('Entity Resolution', this._findings.entityResolution)}
        ${this._renderFinding('Pattern Analysis', this._findings.patternAnalysis)}
        ${this._renderFinding('OSINT Screening', this._findings.osintScreening)}
        ${this._renderFinding('SAR Narrative', this._findings.sarNarrative)}
      </div>
    `;
  }
}
