import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import '@casehubio/blocks-ui-work-item-inbox';

@customElement('aml-work-queue-nav')
export class AmlWorkQueueNav extends LitElement {
  static override styles = css`
    :host { display: block; height: 100%; }
  `;

  override render() {
    return html`
      <blocks-work-item-inbox
        endpoint="/api/work-items?scope=casehubio/aml/oversight"
        selection-topic="work-item"
        .identity=${{ userId: 'current-user', groups: ['compliance-officers'] }}>
      </blocks-work-item-inbox>
    `;
  }
}
