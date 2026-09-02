import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import '@casehubio/blocks-ui-audit-trail-viewer';

@customElement('aml-audit-dock')
export class AmlAuditDock extends LitElement {
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
    :host { display: block; height: 100%; }
    .empty {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px); font-style: italic;
    }
  `;

  override render() {
    if (!this._caseId) return html`<div class="empty">Select an investigation</div>`;
    return html`
      <blocks-audit-trail-viewer
        endpoint="/api/investigations/${this._caseId}/audit-trail">
      </blocks-audit-trail-viewer>
    `;
  }
}
