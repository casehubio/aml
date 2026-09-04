import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('aml-pattern-analysis-workspace')
export class AmlPatternAnalysisWorkspace extends LitElement {
  @property({ attribute: false }) taskContext: any = null;

  @state() private _findings: any = null;
  @state() private _loading = false;

  static override styles = css`
    :host { display: block; padding: var(--pages-space-4, 16px); }
    .section { margin-bottom: var(--pages-space-4, 16px); }
    .section-title { font-size: var(--pages-font-size-sm, 13px); font-weight: 600; color: var(--pages-neutral-8, #404040); margin-bottom: var(--pages-space-2, 8px); text-transform: uppercase; letter-spacing: 0.5px; }
    .card { border: 1px solid var(--pages-neutral-4, #d4d4d4); border-radius: 8px; padding: var(--pages-space-3, 12px); }
    .row { display: flex; justify-content: space-between; padding: var(--pages-space-1, 4px) 0; font-size: var(--pages-font-size-sm, 13px); }
    .label { color: var(--pages-neutral-7, #525252); }
    .value { color: var(--pages-neutral-11, #0a0a0a); font-weight: 500; }
    .description { font-size: var(--pages-font-size-sm, 13px); line-height: 1.5; color: var(--pages-neutral-11, #0a0a0a); }
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

  override render() {
    if (this._loading) return html`<div>Loading pattern data...</div>`;
    const pa = this._findings?.patternAnalysis;
    if (!pa?.result) return html`<div>No pattern analysis data yet</div>`;
    const r = pa.result;
    return html`
      <div class="section">
        <div class="section-title">Pattern Detection</div>
        <div class="card">
          <div class="description">${r.patternDescription ?? 'No pattern identified'}</div>
        </div>
      </div>
      <div class="section">
        <div class="section-title">Structuring Detection</div>
        <div class="card">
          <div class="row"><span class="label">Structuring Detected</span><span class="value">${r.structuringDetected ? 'Yes' : 'No'}</span></div>
          <div class="row"><span class="label">Related Transactions</span><span class="value">${r.relatedTransactionCount ?? 0}</span></div>
        </div>
      </div>
    `;
  }
}
