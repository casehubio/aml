import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('aml-entity-resolution-workspace')
export class AmlEntityResolutionWorkspace extends LitElement {
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
    if (this._loading) return html`<div>Loading entity data...</div>`;
    const er = this._findings?.entityResolution;
    if (!er?.result) return html`<div>No entity resolution data yet</div>`;
    const r = er.result;
    return html`
      <div class="section">
        <div class="section-title">Entity Graph</div>
        <div class="card">
          <div class="row"><span class="label">Beneficial Owner</span><span class="value">${r.beneficialOwner ?? '—'}</span></div>
          <div class="row"><span class="label">Entity Type</span><span class="value">${r.entityType ?? '—'}</span></div>
          <div class="row"><span class="label">Jurisdiction</span><span class="value">${r.jurisdiction ?? '—'}</span></div>
          <div class="row"><span class="label">Ownership Depth</span><span class="value">${r.ownershipDepth ?? '—'}</span></div>
        </div>
      </div>
      <div class="section">
        <div class="section-title">Risk Indicators</div>
        <div class="card">
          <div class="row"><span class="label">Shell Company</span><span class="value">${r.shellCompanyIndicator ? 'Yes' : 'No'}</span></div>
          <div class="row"><span class="label">Layered Ownership</span><span class="value">${r.layeredOwnership ? 'Yes' : 'No'}</span></div>
        </div>
      </div>
    `;
  }
}
