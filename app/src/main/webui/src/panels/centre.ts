import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { TableColumnConfig, ColumnRenderer } from '@casehubio/pages-table';
import type { TypedRow, CellValue, ColumnId } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TabDefinition } from '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-split-workbench';
import '@casehubio/blocks-ui-list-pane';
import '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-work-item-workbench';

const LEFT_DOCK_PANELS = new Set(['aml-investigation-nav', 'aml-worker-nav', 'aml-work-queue-nav']);
const PANEL_TO_MODE: Record<string, string> = {
  'aml-investigation-nav': 'investigations',
  'aml-worker-nav': 'worker-tasks',
  'aml-work-queue-nav': 'work-queue',
};

type CentreMode = 'investigations' | 'worker-tasks' | 'work-queue';

const CALLER_REF_PATTERNS: [RegExp, number][] = [
  [/^case:(.+)\/gate:.+$/, 1],
  [/^aml:investigation:(.+)$/, 1],
];

function extractCaseId(callerRef: string): string | null {
  for (const [pattern, group] of CALLER_REF_PATTERNS) {
    const match = callerRef.match(pattern);
    if (match?.[group]) return match[group]!;
  }
  return null;
}

const investigationTabs: TabDefinition[] = [
  { id: 'overview', label: 'Overview', tagName: 'aml-investigation-overview', order: 0 },
  { id: 'flow', label: 'Flow Diagram', tagName: 'aml-investigation-flow', order: 5 },
  { id: 'findings', label: 'Findings', tagName: 'aml-findings-panel', order: 10 },
  { id: 'routing', label: 'Routing & Trust', tagName: 'aml-routing-panel', order: 20 },
  { id: 'compliance', label: 'Compliance', tagName: 'aml-compliance-panel', order: 25 },
  { id: 'audit', label: 'Audit', tagName: 'aml-audit-trail', order: 30 },
];

const statusColors: Record<string, string> = {
  completed: 'background: #dcfce7; color: #16a34a;',
  in_progress: 'background: #dbeafe; color: #2563eb;',
  failed: 'background: #fee2e2; color: #dc2626;',
  suspended: 'background: #fef3c7; color: #d97706;',
  cancelled: 'background: #e5e5e5; color: #404040;',
};

const investigationColumns: TableColumnConfig[] = [
  { id: 'status' as ColumnId, label: 'Status', sortable: true, width: '110px' },
  { id: 'riskScore' as ColumnId, label: 'Risk', sortable: true, width: '70px' },
  { id: 'flagReason' as ColumnId, label: 'Flag Reason', sortable: true },
  { id: 'amount' as ColumnId, label: 'Amount', sortable: true, width: '140px', align: 'end' as const },
  { id: 'outcomeType' as ColumnId, label: 'Outcome', sortable: true, width: '110px' },
  { id: 'createdAt' as ColumnId, label: 'Created', sortable: true, width: '100px' },
  { id: 'caseId' as ColumnId, visible: false },
  { id: 'transactionId' as ColumnId, visible: false },
  { id: 'originAccount' as ColumnId, visible: false },
  { id: 'destinationAccount' as ColumnId, visible: false },
  { id: 'currency' as ColumnId, visible: false },
];

const columnRenderers: ReadonlyMap<ColumnId, ColumnRenderer> = new Map([
  ['status' as ColumnId, (cell: CellValue) => {
    const val = cell.type === 'NULL' ? '' : String((cell as { value: unknown }).value);
    const colors = statusColors[val] ?? '';
    return html`<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.3px;${colors}">${val}</span>`;
  }],
  ['riskScore' as ColumnId, (cell: CellValue) => {
    if (cell.type === 'NULL') return html`<span>—</span>`;
    const score = (cell as { value: number }).value;
    const pct = Math.round(score * 100);
    const color = score >= 0.8 ? '#dc2626' : score >= 0.5 ? '#d97706' : '#16a34a';
    return html`<span style="font-weight:600;font-size:12px;color:${color}">${pct}%</span>`;
  }],
  ['amount' as ColumnId, (cell: CellValue, row: TypedRow) => {
    if (cell.type === 'NULL') return html`<span>—</span>`;
    const amount = (cell as { value: number }).value;
    const currency = row.text('currency' as ColumnId);
    return html`<span style="font-variant-numeric:tabular-nums">${amount.toLocaleString()} ${currency}</span>`;
  }],
  ['outcomeType' as ColumnId, (cell: CellValue) => {
    const val = cell.type === 'NULL' ? '' : String((cell as { value: unknown }).value);
    if (!val) return html`<span style="color:#a3a3a3">—</span>`;
    return html`<span>${val}</span>`;
  }],
]);

function getRowClass(row: TypedRow): string {
  const status = row.text('status' as ColumnId);
  if (status === 'failed' || status === 'cancelled') return 'row-high-risk';
  if (status === 'suspended') return 'row-medium-risk';
  return '';
}

@customElement('aml-centre')
export class AmlCentre extends LitElement {
  @state() private _activeMode: CentreMode = 'investigations';

  private _onDockToggle = (e: Event) => {
    const { panelId, visible } = (e as CustomEvent<{ panelId: string; visible: boolean }>).detail;
    if (visible && LEFT_DOCK_PANELS.has(panelId)) {
      this._activeMode = (PANEL_TO_MODE[panelId] ?? 'investigations') as CentreMode;
    }
  };

  private _emitInvestigationContext(caseId: string) {
    document.dispatchEvent(new CustomEvent('pages-selection', {
      detail: { topic: 'investigation-context', caseId },
      bubbles: true, composed: true,
    }));
  }

  private _onCaseSelection = (e: Event) => {
    const row = (e as CustomEvent).detail?.row as TypedRow | undefined;
    if (row) {
      const caseId = row.text('caseId' as ColumnId);
      if (caseId) this._emitInvestigationContext(caseId);
    }
  };

  private _onWorkItemSelection = (e: Event) => {
    const item = (e as CustomEvent).detail?.item;
    if (item?.callerRef) {
      const caseId = extractCaseId(item.callerRef);
      if (caseId) this._emitInvestigationContext(caseId);
    }
  };

  override connectedCallback() {
    super.connectedCallback();
    document.addEventListener('pages-dock-toggle', this._onDockToggle);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('pages-dock-toggle', this._onDockToggle);
  }

  static override styles = css`
    :host { display: block; height: 100%; width: 100%; }
  `;

  override render() {
    switch (this._activeMode) {
      case 'investigations':
        return html`
          <blocks-split-workbench
            selection-topic="case"
            title="AML Investigations"
            @selection-changed=${this._onCaseSelection}>
            <blocks-list-pane slot="list"
              selection-topic="case"
              endpoint="/api/investigations"
              .columnConfig=${investigationColumns}
              .columnRenderers=${columnRenderers}
              .getRowKey=${(row: TypedRow) => row.text('caseId' as ColumnId)}
              .getRowClass=${getRowClass}>
            </blocks-list-pane>
            <blocks-detail-pane slot="detail"
              selection-topic="case"
              .tabs=${investigationTabs}
              empty-message="Select an investigation to view details">
            </blocks-detail-pane>
          </blocks-split-workbench>
        `;
      case 'worker-tasks':
        return html`
          <div style="padding: 24px; color: var(--pages-neutral-7, #525252); text-align: center;">
            Worker task queue — requires blocks-ui worker-task-pane (Batch 6)
          </div>
        `;
      case 'work-queue':
        return html`
          <blocks-work-item-workbench
            endpoint="/api/work-items"
            .identity=${{ userId: 'officer-001', groups: ['compliance-officers'] }}
            @selection-changed=${this._onWorkItemSelection}>
          </blocks-work-item-workbench>
        `;
    }
  }
}
