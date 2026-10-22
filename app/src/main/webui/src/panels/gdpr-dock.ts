import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import '@casehubio/mosaic-gdpr-erasure-action';

@customElement('aml-gdpr-dock')
export class AmlGdprDock extends LitElement {
  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; }
    .section-label {
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--pages-neutral-8, #404040);
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px) var(--pages-space-2, 8px);
    }
  `;

  override render() {
    return html`
      <div class="section-label">GDPR Erasure</div>
      <agency-gdpr-erasure-action
        endpoint="/api/actors"
        subject-label="Actor">
      </agency-gdpr-erasure-action>
    `;
  }
}
