import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import '@casehubio/blocks-ui-compliance-summary';
import '@casehubio/blocks-ui-gdpr-erasure-action';

@customElement('aml-compliance-dock')
export class AmlComplianceDock extends LitElement {
  @state() private _caseId: string | null = null;

  private _onContext = (e: Event) => {
    const detail = (e as CustomEvent).detail;
    if (detail.topic === 'investigation-context') {
      this._caseId = detail.caseId;
    }
  };

  override connectedCallback() {
    super.connectedCallback();
    document.addEventListener('pages-selection', this._onContext);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('pages-selection', this._onContext);
  }

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; }
    .empty {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px); font-style: italic;
    }
    .section-label {
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--pages-neutral-8, #404040);
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px) var(--pages-space-2, 8px);
    }
  `;

  override render() {
    if (!this._caseId) return html`<div class="empty">Select an investigation</div>`;
    return html`
      <div class="section-label">Regulatory Compliance</div>
      <blocks-compliance-summary
        endpoint="/api/investigations/${this._caseId}/compliance-evidence">
      </blocks-compliance-summary>
      <div class="section-label">GDPR Erasure</div>
      <blocks-gdpr-erasure-action
        endpoint="/api/actors"
        subject-label="Actor">
      </blocks-gdpr-erasure-action>
    `;
  }
}
