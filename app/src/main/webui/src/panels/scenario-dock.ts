import { LitElement, html, css, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';

interface ScriptDescriptor {
  name: string;
  description: string;
  labels: string[];
  complexity?: string;
  duration?: string;
}

@customElement('aml-scenario-dock')
export class AmlScenarioDock extends LitElement {
  @state() private _scripts: ScriptDescriptor[] = [];
  @state() private _loading = true;
  @state() private _running: string | null = null;

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; }
    .header {
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px);
      font-size: var(--pages-font-size-xs, 11px); font-weight: 600;
      text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--pages-neutral-8, #404040);
    }
    .script-list { display: flex; flex-direction: column; gap: var(--pages-space-2, 8px); padding: 0 var(--pages-space-4, 16px); }
    .script-card {
      border: 1px solid var(--pages-neutral-4, #d4d4d4); border-radius: 8px;
      padding: var(--pages-space-3, 12px); cursor: pointer; transition: border-color 0.15s;
    }
    .script-card:hover { border-color: var(--pages-accent-7, #60a5fa); }
    .script-card.running { border-color: var(--pages-success-7, #4ade80); background: var(--pages-success-1, #f0fdf4); }
    .script-name { font-size: var(--pages-font-size-sm, 13px); font-weight: 600; color: var(--pages-neutral-11, #0a0a0a); }
    .script-desc { font-size: 12px; color: var(--pages-neutral-7, #525252); margin-top: 2px; }
    .script-meta { display: flex; gap: var(--pages-space-2, 8px); margin-top: var(--pages-space-2, 8px); }
    .label-chip {
      font-size: 10px; padding: 1px 6px; border-radius: 8px;
      background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-9, #262626);
    }
    .loading, .empty {
      padding: var(--pages-space-6, 24px); text-align: center;
      color: var(--pages-neutral-7, #525252); font-size: var(--pages-font-size-sm, 13px); font-style: italic;
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this._fetchScripts();
  }

  private async _fetchScripts() {
    this._loading = true;
    try {
      const res = await fetch('/api/scenario/library');
      if (res.ok) this._scripts = await res.json();
    } finally { this._loading = false; }
  }

  private async _startScenario(name: string) {
    this._running = name;
    try {
      await fetch('/api/scenario/start', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scenario: name }),
      });
    } catch { /* scenario start is fire-and-forget */ }
  }

  override render() {
    if (this._loading) return html`<div class="loading">Loading scenarios...</div>`;
    if (this._scripts.length === 0) return html`<div class="empty">No scenarios available</div>`;
    return html`
      <div class="header">Scenario Library</div>
      <div class="script-list">
        ${this._scripts.map(s => html`
          <div class="script-card ${this._running === s.name ? 'running' : ''}"
               @click=${() => this._startScenario(s.name)}>
            <div class="script-name">${s.name}</div>
            <div class="script-desc">${s.description}</div>
            <div class="script-meta">
              ${s.labels?.map(l => html`<span class="label-chip">${l}</span>`) ?? nothing}
              ${s.complexity ? html`<span class="label-chip">${s.complexity}</span>` : nothing}
              ${s.duration ? html`<span class="label-chip">${s.duration}</span>` : nothing}
            </div>
          </div>
        `)}
      </div>
    `;
  }
}
