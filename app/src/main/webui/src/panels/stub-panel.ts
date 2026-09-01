import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';

@customElement('aml-stub-panel')
export class AmlStubPanel extends LitElement {
  @property() label = 'Coming soon';

  static override styles = css`
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px);
      font-style: italic;
    }
  `;

  override render() {
    return html`<div>${this.label}</div>`;
  }
}
