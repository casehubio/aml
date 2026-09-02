import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import '@casehubio/blocks-ui-list-pane';

@customElement('aml-worker-nav')
export class AmlWorkerNav extends LitElement {
  static override styles = css`
    :host { display: block; height: 100%; }
    .placeholder {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px); font-style: italic;
      padding: var(--pages-space-4, 16px); text-align: center;
    }
  `;

  override render() {
    return html`
      <div class="placeholder">
        Worker task queue — requires blocks-ui worker-task-pane (Batch 6)
      </div>
    `;
  }
}
