import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('aml-sar-drafting-workspace')
export class AmlSarDraftingWorkspace extends LitElement {
  @property({ attribute: false }) taskContext: any = null;

  @state() private _findings: any = null;
  @state() private _loading = false;
  @state() private _narrative = '';

  static override styles = css`
    :host { display: block; padding: var(--pages-space-4, 16px); }
    .section { margin-bottom: var(--pages-space-4, 16px); }
    .section-title { font-size: var(--pages-font-size-sm, 13px); font-weight: 600; color: var(--pages-neutral-8, #404040); margin-bottom: var(--pages-space-2, 8px); text-transform: uppercase; letter-spacing: 0.5px; }
    textarea {
      width: 100%; min-height: 200px; padding: var(--pages-space-3, 12px);
      border: 1px solid var(--pages-neutral-4, #d4d4d4); border-radius: 8px;
      font-family: ui-serif, Georgia, serif; font-size: var(--pages-font-size-sm, 13px);
      line-height: 1.6; resize: vertical; box-sizing: border-box;
    }
    textarea:focus { outline: 2px solid var(--pages-accent-7, #60a5fa); border-color: transparent; }
    .prefill-note { font-size: 11px; color: var(--pages-neutral-7, #525252); font-style: italic; margin-top: var(--pages-space-1, 4px); }
  `;

  override updated(changed: Map<string, unknown>) {
    if (changed.has('taskContext') && this.taskContext?.caseId) this._fetchFindings();
  }

  private async _fetchFindings() {
    this._loading = true;
    try {
      const res = await fetch(`/api/investigations/${this.taskContext.caseId}/findings`);
      if (res.ok) {
        this._findings = await res.json();
        this._prefillNarrative();
      }
    } finally { this._loading = false; }
  }

  private _prefillNarrative() {
    const parts: string[] = [];
    const er = this._findings?.entityResolution?.result;
    if (er) parts.push(`Entity type: ${er.entityType ?? 'Unknown'}. Jurisdiction: ${er.jurisdiction ?? 'Unknown'}.`);
    const pa = this._findings?.patternAnalysis?.result;
    if (pa?.patternDescription) parts.push(`Pattern: ${pa.patternDescription}`);
    const osint = this._findings?.osintScreening?.result;
    if (osint) {
      const flags = [];
      if (osint.sanctionsHit) flags.push('sanctions hit');
      if (osint.pepMatch) flags.push('PEP match');
      if (osint.adverseMedia) flags.push('adverse media');
      if (flags.length > 0) parts.push(`OSINT flags: ${flags.join(', ')}.`);
    }
    this._narrative = parts.join('\n\n');
  }

  private _onInput(e: Event) {
    this._narrative = (e.target as HTMLTextAreaElement).value;
    this.dispatchEvent(new CustomEvent('workspace-result', {
      detail: { fields: { narrative: this._narrative }, confidence: 0.8 },
      bubbles: true, composed: true,
    }));
  }

  override render() {
    if (this._loading) return html`<div>Loading findings for narrative...</div>`;
    return html`
      <div class="section">
        <div class="section-title">SAR Narrative</div>
        <textarea .value=${this._narrative} @input=${this._onInput}
          placeholder="Draft the SAR narrative based on investigation findings..."></textarea>
        <div class="prefill-note">Pre-filled from specialist findings. Edit as needed.</div>
      </div>
    `;
  }
}
