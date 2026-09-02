import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import './aml-findings-panel.js';

@customElement('aml-findings-dock')
export class AmlFindingsDock extends LitElement {
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
  `;

  override render() {
    if (!this._caseId) return html`<div class="empty">Select an investigation</div>`;
    return html`
      <aml-findings-panel
        .item=${{ caseId: this._caseId }}>
      </aml-findings-panel>
    `;
  }
}
