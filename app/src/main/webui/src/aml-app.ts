import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { TableColumnConfig, ColumnRenderer } from '@casehubio/pages-table';
import type { TypedRow, CellValue, ColumnId } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TabDefinition } from '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-split-workbench';
import '@casehubio/blocks-ui-list-pane';
import '@casehubio/blocks-ui-detail-pane';
import '@casehubio/blocks-ui-work-item-inbox';
import '@casehubio/blocks-ui-work-item-workbench';
import './views/operations.js';
import './panels/index.js';

type ViewId = 'investigations' | 'compliance' | 'operations';

const investigationTabs: TabDefinition[] = [
  { id: 'overview', label: 'Overview', tagName: 'aml-investigation-overview', order: 0 },
  { id: 'findings', label: 'Findings', tagName: 'aml-findings-panel', order: 10 },
  { id: 'routing', label: 'Routing & Trust', tagName: 'aml-routing-panel', order: 20 },
  { id: 'compliance', label: 'Compliance', tagName: 'aml-compliance-panel', order: 25 },
  { id: 'audit', label: 'Audit', tagName: 'aml-audit-trail', order: 30 },
];

@customElement('aml-app')
export class AmlApp extends LitElement {
  @state() private _activeView: ViewId = 'investigations';

  private _investigationColumns: TableColumnConfig[] = [
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

  private static _statusColors: Record<string, string> = {
    completed: 'background: #dcfce7; color: #16a34a;',
    in_progress: 'background: #dbeafe; color: #2563eb;',
    failed: 'background: #fee2e2; color: #dc2626;',
    suspended: 'background: #fef3c7; color: #d97706;',
    cancelled: 'background: #e5e5e5; color: #404040;',
  };

  private _columnRenderers: ReadonlyMap<ColumnId, ColumnRenderer> = new Map([
    ['status' as ColumnId, (cell: CellValue) => {
      const val = cell.type === 'NULL' ? '' : String((cell as { value: unknown }).value);
      const colors = AmlApp._statusColors[val] ?? '';
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

  static override styles = css`
    :host {
      display: flex;
      height: 100vh;
      width: 100%;
      font-family: var(--pages-font-family, system-ui);
      background: var(--pages-neutral-1, #ffffff);
    }

    .sidebar {
      width: 160px;
      background: var(--pages-neutral-2, #f5f5f5);
      border-right: 1px solid var(--pages-neutral-4, #d4d4d4);
      display: flex;
      flex-direction: column;
    }

    .sidebar-header {
      padding: var(--pages-space-4, 16px);
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 700;
      color: var(--pages-neutral-11, #0a0a0a);
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }

    .nav-item {
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px);
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
      color: var(--pages-neutral-8, #404040);
      border-left: 3px solid transparent;
      user-select: none;
    }

    .nav-item:hover {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .nav-item.active {
      background: var(--pages-accent-1, #eff6ff);
      color: var(--pages-accent-9, #3b82f6);
      border-left-color: var(--pages-accent-9, #3b82f6);
    }

    .main-content {
      flex: 1;
      overflow: hidden;
    }
  `;

  private _switchView(view: ViewId): void {
    if (window.location.hash !== `#${view}`) {
      window.location.hash = view;
    }
    this._activeView = view;
  }

  override connectedCallback(): void {
    super.connectedCallback();
    const hash = window.location.hash.slice(1);
    if (hash === 'investigations' || hash === 'compliance' || hash === 'operations') {
      this._activeView = hash;
    }
    window.addEventListener('hashchange', this._handleHashChange);
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    window.removeEventListener('hashchange', this._handleHashChange);
  }

  private _handleHashChange = (): void => {
    const hash = window.location.hash.slice(1);
    if (hash === 'investigations' || hash === 'compliance' || hash === 'operations') {
      this._activeView = hash;
    }
  };

  private _getRowClass = (row: TypedRow): string => {
    const status = row.text('status' as ColumnId);
    if (status === 'failed' || status === 'cancelled') return 'row-high-risk';
    if (status === 'suspended') return 'row-medium-risk';
    return '';
  };

  override render() {
    return html`
      <div class="sidebar">
        <div class="sidebar-header">AML Workbench</div>
        ${this._renderNavItem('investigations', 'Investigations')}
        ${this._renderNavItem('compliance', 'Compliance')}
        ${this._renderNavItem('operations', 'Operations')}
      </div>
      <div class="main-content">
        ${this._renderActiveView()}
      </div>
    `;
  }

  private _renderNavItem(id: ViewId, label: string) {
    return html`
      <div
        class="nav-item ${this._activeView === id ? 'active' : ''}"
        @click=${() => this._switchView(id)}
      >
        ${label}
      </div>
    `;
  }

  private _renderActiveView() {
    switch (this._activeView) {
      case 'investigations':
        return html`
          <blocks-split-workbench selection-topic="case" title="AML Investigations">
            <blocks-list-pane slot="list"
              selection-topic="case"
              endpoint="/api/investigations"
              .columnConfig=${this._investigationColumns}
              .columnRenderers=${this._columnRenderers}
              .getRowKey=${(row: TypedRow) => row.text('caseId' as ColumnId)}
              .getRowClass=${this._getRowClass}>
            </blocks-list-pane>
            <blocks-detail-pane slot="detail"
              selection-topic="case"
              .tabs=${investigationTabs}
              empty-message="Select an investigation to view details">
            </blocks-detail-pane>
          </blocks-split-workbench>
        `;
      case 'compliance':
        return html`
          <blocks-work-item-workbench
            endpoint=""
            .identity=${{ userId: 'officer-001', groups: ['compliance-officers'] }}
          ></blocks-work-item-workbench>
        `;
      case 'operations':
        return html`<aml-operations-view></aml-operations-view>`;
    }
  }
}
