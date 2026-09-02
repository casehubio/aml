import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';

@customElement('aml-scenario-dock')
export class AmlScenarioDock extends LitElement {
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
        Scenario library — requires Pages ScenarioOrchestrator (Batch 8)
      </div>
    `;
  }
}
