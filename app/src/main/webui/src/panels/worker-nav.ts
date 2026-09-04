import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import '@casehubio/blocks-ui-worker-task-pane';
import type { WorkspaceDefinition } from '@casehubio/blocks-ui-worker-task-pane';

const SPECIALIST_WORKSPACES: WorkspaceDefinition[] = [
  { capabilityTag: 'entity-resolution', tagName: 'aml-entity-resolution-workspace', label: 'Entity Resolution', icon: 'account_tree' },
  { capabilityTag: 'pattern-analysis', tagName: 'aml-pattern-analysis-workspace', label: 'Pattern Analysis', icon: 'analytics' },
  { capabilityTag: 'osint-screening', tagName: 'aml-osint-workspace', label: 'OSINT Screening', icon: 'policy' },
  { capabilityTag: 'sar-drafting', tagName: 'aml-sar-drafting-workspace', label: 'SAR Drafting', icon: 'description' },
  { capabilityTag: 'senior-analyst-review', tagName: 'aml-senior-analyst-workspace', label: 'Senior Analyst', icon: 'supervisor_account' },
];

@customElement('aml-worker-nav')
export class AmlWorkerNav extends LitElement {
  static override styles = css`
    :host { display: block; height: 100%; }
  `;

  override render() {
    return html`
      <blocks-worker-task-pane
        endpoint="/api/worker-tasks"
        respond-endpoint="/api/worker-tasks"
        selection-topic="worker-task"
        .identity=${{ userId: 'current-user', groups: ['aml-compliance'] }}
        .workspaces=${SPECIALIST_WORKSPACES}>
      </blocks-worker-task-pane>
    `;
  }
}
