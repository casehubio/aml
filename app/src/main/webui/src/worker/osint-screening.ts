import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('aml-osint-workspace')
export class AmlOsintWorkspace extends LitElement {
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
    .value { font-weight: 500; }
    .hit { color: var(--pages-error-9, #dc2626); }
    .clear { color: var(--pages-success-9, #16a34a); }
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
    if (this._loading) return html`<div>Loading OSINT data...</div>`;
    const osint = this._findings?.osintScreening;
    if (!osint?.result) return html`<div>No OSINT screening data yet</div>`;
    const r = osint.result;
    return html`
      <div class="section">
        <div class="section-title">Sanctions & PEP Screening</div>
        <div class="card">
          <div class="row"><span class="label">Sanctions Hit</span><span class="value ${r.sanctionsHit ? 'hit' : 'clear'}">${r.sanctionsHit ? 'HIT' : 'Clear'}</span></div>
          <div class="row"><span class="label">PEP Match</span><span class="value ${r.pepMatch ? 'hit' : 'clear'}">${r.pepMatch ? 'MATCH' : 'Clear'}</span></div>
          <div class="row"><span class="label">Adverse Media</span><span class="value ${r.adverseMedia ? 'hit' : 'clear'}">${r.adverseMedia ? 'Found' : 'None'}</span></div>
        </div>
      </div>
    `;
  }
}
