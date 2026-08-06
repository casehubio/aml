import { LitElement, html, css, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import type { TypedRow } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import { ColumnType, columnId } from '@casehubio/pages-data/dist/dataset/types.js';
import '@casehubio/pages-table';
import type {
  ThroughputMetrics,
  TrustScoreMetrics,
  AgentTrustScore,
  GateMetrics,
  InterventionMetrics,
} from '../types.js';

import './aml-sar-quality-tab.js';

type TabId = 'throughput' | 'trust-scores' | 'gates' | 'intervention' | 'sar-quality';

interface StatusRow {
  status: string;
  count: number;
}

interface FlagReasonRow {
  reason: string;
  count: number;
}

interface ActionTypeRow {
  actionType: string;
  count: number;
}

@customElement('aml-operations-view')
export class AmlOperationsView extends LitElement {
  @state() private _activeTab: TabId = 'throughput';

  // Throughput state
  @state() private _throughputMetrics: ThroughputMetrics | null = null;
  @state() private _throughputLoading = false;
  @state() private _throughputError: string | null = null;

  // Trust scores state
  @state() private _trustMetrics: TrustScoreMetrics | null = null;
  @state() private _trustLoading = false;
  @state() private _trustError: string | null = null;

  // Gates state
  @state() private _gateMetrics: GateMetrics | null = null;
  @state() private _gateLoading = false;
  @state() private _gateError: string | null = null;

  // Intervention state
  @state() private _interventionMetrics: InterventionMetrics | null = null;
  @state() private _interventionLoading = false;
  @state() private _interventionError: string | null = null;
  @state() private _suspendCaseId = '';
  @state() private _resumeCaseId = '';
  @state() private _escalateWorkItemId = '';
  @state() private _overrideWorkItemId = '';

  static override styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
      background: var(--pages-neutral-1, #ffffff);
    }

    .tab-bar {
      display: flex;
      gap: 0;
      border-bottom: 2px solid var(--pages-neutral-4, #d4d4d4);
      background: var(--pages-neutral-2, #f5f5f5);
      overflow-x: auto;
    }

    .tab {
      padding: var(--pages-space-2, 8px) var(--pages-space-4, 16px);
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
      color: var(--pages-neutral-8, #404040);
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      white-space: nowrap;
      user-select: none;
    }

    .tab:hover {
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .tab.active {
      color: var(--pages-accent-9, #3b82f6);
      border-bottom-color: var(--pages-accent-9, #3b82f6);
    }

    .tab-content {
      flex: 1;
      overflow: auto;
      padding: var(--pages-space-4, 16px);
    }

    .kpi-summary {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: var(--pages-space-4, 16px);
      margin-bottom: var(--pages-space-6, 24px);
    }

    .kpi-card {
      background: var(--pages-neutral-1, #ffffff);
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
    }

    .kpi-label {
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
      color: var(--pages-neutral-7, #525252);
      margin-bottom: var(--pages-space-2, 8px);
    }

    .kpi-value {
      font-size: 32px;
      font-weight: 700;
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .section {
      margin-bottom: var(--pages-space-6, 24px);
    }

    .section-title {
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 600;
      color: var(--pages-neutral-11, #0a0a0a);
      margin-bottom: var(--pages-space-3, 12px);
    }

    .table-container {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      overflow: hidden;
      background: var(--pages-neutral-1, #ffffff);
      margin-bottom: var(--pages-space-4, 16px);
    }

    .action-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: var(--pages-space-4, 16px);
    }

    .action-card {
      background: var(--pages-neutral-1, #ffffff);
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
    }

    .action-card h3 {
      font-size: var(--pages-font-size-md, 14px);
      font-weight: 600;
      color: var(--pages-neutral-11, #0a0a0a);
      margin: 0 0 var(--pages-space-3, 12px) 0;
    }

    .action-card input {
      width: 100%;
      padding: var(--pages-space-2, 8px);
      border: 1px solid var(--pages-neutral-5, #a3a3a3);
      border-radius: 4px;
      font-size: var(--pages-font-size-sm, 13px);
      margin-bottom: var(--pages-space-2, 8px);
      box-sizing: border-box;
    }

    .action-card button {
      width: 100%;
      padding: var(--pages-space-2, 8px) var(--pages-space-3, 12px);
      background: var(--pages-accent-9, #3b82f6);
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
    }

    .action-card button:hover {
      background: var(--pages-accent-10, #2563eb);
    }

    .action-card button:disabled {
      background: var(--pages-neutral-5, #a3a3a3);
      cursor: not-allowed;
    }

    .skeleton {
      height: 200px;
      background: linear-gradient(
        90deg,
        var(--pages-neutral-2, #f5f5f5) 25%,
        var(--pages-neutral-3, #e5e5e5) 50%,
        var(--pages-neutral-2, #f5f5f5) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
      border-radius: 8px;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .error-card {
      background: var(--pages-error-1, #fef2f2);
      border: 1px solid var(--pages-error-4, #fca5a5);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      color: var(--pages-error-9, #dc2626);
    }

    .error-card button {
      margin-top: var(--pages-space-2, 8px);
      padding: var(--pages-space-1, 4px) var(--pages-space-3, 12px);
      background: var(--pages-error-9, #dc2626);
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
    }

    .error-card button:hover {
      background: var(--pages-error-10, #b91c1c);
    }

    /* Trust score row styling */
    .row-low-trust {
      background-color: var(--pages-error-1, #fef2f2) !important;
    }

    .row-medium-trust {
      background-color: var(--pages-warning-1, #fffbeb) !important;
    }

    .row-high-trust {
      background-color: var(--pages-success-1, #f0fdf4) !important;
    }

    .placeholder-note {
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px dashed var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px);
      font-style: italic;
      margin-top: var(--pages-space-2, 8px);
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: var(--pages-space-4, 16px);
      margin-bottom: var(--pages-space-6, 24px);
    }

    .metric-card {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      text-align: center;
    }

    .metric-value {
      font-size: 28px;
      font-weight: 700;
      color: var(--pages-neutral-12, #0a0a0a);
    }

    .metric-label {
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 600;
      color: var(--pages-neutral-9, #262626);
      margin-top: var(--pages-space-1, 4px);
    }

    .metric-desc {
      font-size: 11px;
      color: var(--pages-neutral-7, #525252);
      margin-top: var(--pages-space-1, 4px);
    }

    .intervention-list {
      display: flex;
      flex-direction: column;
      gap: var(--pages-space-2, 8px);
      margin-bottom: var(--pages-space-6, 24px);
    }

    .intervention-item {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px);
    }

    .intervention-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: var(--pages-space-1, 4px);
    }

    .intervention-type {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      padding: 2px 8px;
      border-radius: 12px;
    }

    .intervention-type.escalation {
      background: var(--pages-error-3, #fee2e2);
      color: var(--pages-error-9, #dc2626);
    }

    .intervention-type.decline_reroute {
      background: var(--pages-warning-3, #fef3c7);
      color: var(--pages-warning-9, #d97706);
    }

    .intervention-type.gate_rejection {
      background: var(--pages-accent-3, #dbeafe);
      color: var(--pages-accent-9, #3b82f6);
    }

    .intervention-type.manual_override {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-9, #262626);
    }

    .intervention-date {
      font-size: 11px;
      color: var(--pages-neutral-7, #525252);
    }

    .intervention-reason {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .intervention-meta {
      font-size: 11px;
      color: var(--pages-neutral-7, #525252);
      margin-top: var(--pages-space-1, 4px);
    }

    .loading {
      padding: var(--pages-space-6, 24px);
      text-align: center;
      color: var(--pages-neutral-7, #525252);
    }

    .error {
      padding: var(--pages-space-4, 16px);
      background: var(--pages-error-1, #fef2f2);
      color: var(--pages-error-9, #dc2626);
      border-radius: 8px;
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchDataForActiveTab();
  }

  private _switchTab(tab: TabId): void {
    this._activeTab = tab;
    this._fetchDataForActiveTab();
  }

  private _fetchDataForActiveTab(): void {
    switch (this._activeTab) {
      case 'throughput':
        this._fetchThroughputMetrics();
        break;
      case 'trust-scores':
        this._fetchTrustMetrics();
        break;
      case 'gates':
        this._fetchGateMetrics();
        break;
      case 'intervention':
        this._fetchInterventionMetrics();
        break;
    }
  }

  private async _fetchThroughputMetrics(): Promise<void> {
    this._throughputLoading = true;
    this._throughputError = null;
    try {
      const response = await fetch('/api/metrics/throughput');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._throughputMetrics = await response.json();
    } catch (error) {
      this._throughputError = error instanceof Error ? error.message : String(error);
    } finally {
      this._throughputLoading = false;
    }
  }

  private async _fetchTrustMetrics(): Promise<void> {
    this._trustLoading = true;
    this._trustError = null;
    try {
      const response = await fetch('/api/metrics/trust-scores');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._trustMetrics = await response.json();
    } catch (error) {
      this._trustError = error instanceof Error ? error.message : String(error);
    } finally {
      this._trustLoading = false;
    }
  }

  private async _fetchGateMetrics(): Promise<void> {
    this._gateLoading = true;
    this._gateError = null;
    try {
      const response = await fetch('/api/metrics/gates');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._gateMetrics = await response.json();
    } catch (error) {
      this._gateError = error instanceof Error ? error.message : String(error);
    } finally {
      this._gateLoading = false;
    }
  }

  private async _fetchInterventionMetrics(): Promise<void> {
    this._interventionLoading = true;
    this._interventionError = null;
    try {
      const response = await fetch('/api/metrics/interventions');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._interventionMetrics = await response.json();
    } catch (error) {
      this._interventionError = error instanceof Error ? error.message : String(error);
    } finally {
      this._interventionLoading = false;
    }
  }

  override render() {
    return html`
      <div class="tab-bar">
        ${this._renderTab('throughput', 'Throughput')}
        ${this._renderTab('trust-scores', 'Trust Scores')}
        ${this._renderTab('gates', 'Gate Activity')}
        ${this._renderTab('intervention', 'Intervention')}
        ${this._renderTab('sar-quality', 'SAR Quality')}
      </div>
      <div class="tab-content">
        ${this._renderActiveTabContent()}
      </div>
    `;
  }

  private _renderTab(id: TabId, label: string) {
    return html`
      <div
        class="tab ${this._activeTab === id ? 'active' : ''}"
        @click=${() => this._switchTab(id)}
      >
        ${label}
      </div>
    `;
  }

  private _renderActiveTabContent() {
    switch (this._activeTab) {
      case 'throughput':
        return this._renderThroughputTab();
      case 'trust-scores':
        return this._renderTrustScoresTab();
      case 'gates':
        return this._renderGatesTab();
      case 'intervention':
        return this._renderInterventionTab();
      case 'sar-quality':
        return html`<aml-sar-quality-tab></aml-sar-quality-tab>`;
      default:
        return nothing;
    }
  }

  // ========== Throughput Tab ==========

  private _renderThroughputTab() {
    if (this._throughputLoading) {
      return html`<div class="skeleton"></div>`;
    }

    if (this._throughputError) {
      return html`
        <div class="error-card">
          <div>Failed to load throughput metrics: ${this._throughputError}</div>
          <button @click=${this._fetchThroughputMetrics}>Retry</button>
        </div>
      `;
    }

    if (!this._throughputMetrics) {
      return html`<div>No throughput data available</div>`;
    }

    const statusRows: StatusRow[] = Object.entries(this._throughputMetrics.byStatus).map(
      ([status, count]) => ({ status, count })
    );

    const flagReasonRows: FlagReasonRow[] = Object.entries(this._throughputMetrics.byFlagReason).map(
      ([reason, count]) => ({ reason, count })
    );

    const statusColumnConfig: TableColumnConfig[] = [
      { id: columnId('status'), label: 'Status', sortable: true },
      { id: columnId('count'), label: 'Count', sortable: true },
    ];

    const statusColumnDefs = [
      { id: columnId('status'), type: ColumnType.TEXT, getValue: (row: StatusRow) => row.status },
      { id: columnId('count'), type: ColumnType.TEXT, getValue: (row: StatusRow) => String(row.count) },
    ];

    const flagReasonColumnConfig: TableColumnConfig[] = [
      { id: columnId('reason'), label: 'Flag Reason', sortable: true },
      { id: columnId('count'), label: 'Count', sortable: true },
    ];

    const flagReasonColumnDefs = [
      { id: columnId('reason'), type: ColumnType.TEXT, getValue: (row: FlagReasonRow) => row.reason },
      { id: columnId('count'), type: ColumnType.TEXT, getValue: (row: FlagReasonRow) => String(row.count) },
    ];

    return html`
      <div class="kpi-summary">
        <div class="kpi-card">
          <div class="kpi-label">Total Investigations</div>
          <div class="kpi-value">${this._throughputMetrics.totalInvestigations}</div>
        </div>
      </div>

      <div class="section">
        <div class="section-title">By Status</div>
        <div class="table-container">
          <pages-table
            .dataSet=${fromRows(statusRows, statusColumnDefs)}
            .columnConfig=${statusColumnConfig}
            .getRowKey=${(row: TypedRow) => row.text(columnId('status'))}
            mode="auto"
            client-sort
            emptyMessage="No status data"
          ></pages-table>
        </div>
      </div>

      <div class="section">
        <div class="section-title">By Flag Reason</div>
        <div class="table-container">
          <pages-table
            .dataSet=${fromRows(flagReasonRows, flagReasonColumnDefs)}
            .columnConfig=${flagReasonColumnConfig}
            .getRowKey=${(row: TypedRow) => row.text(columnId('reason'))}
            mode="auto"
            client-sort
            emptyMessage="No flag reason data"
          ></pages-table>
        </div>
      </div>
    `;
  }

  // ========== Trust Scores Tab ==========

  private _renderTrustScoresTab() {
    if (this._trustLoading) {
      return html`<div class="skeleton"></div>`;
    }

    if (this._trustError) {
      return html`
        <div class="error-card">
          <div>Failed to load trust metrics: ${this._trustError}</div>
          <button @click=${this._fetchTrustMetrics}>Retry</button>
        </div>
      `;
    }

    if (!this._trustMetrics || this._trustMetrics.scores.length === 0) {
      return html`<div>No trust score data available</div>`;
    }

    const trustColumnConfig: TableColumnConfig[] = [
      { id: columnId('agentId'), label: 'Agent ID', sortable: true },
      { id: columnId('capabilityTag'), label: 'Capability Tag', sortable: true },
      { id: columnId('score'), label: 'Score', sortable: true },
    ];

    const trustColumnDefs = [
      { id: columnId('agentId'), type: ColumnType.TEXT, getValue: (row: AgentTrustScore) => row.agentId },
      { id: columnId('capabilityTag'), type: ColumnType.TEXT, getValue: (row: AgentTrustScore) => row.capabilityTag },
      { id: columnId('score'), type: ColumnType.TEXT, getValue: (row: AgentTrustScore) => row.score !== null ? row.score.toFixed(3) : '—' },
    ];

    return html`
      <div class="section">
        <div class="section-title">Agent Trust Scores</div>
        <div class="table-container">
          <pages-table
            .dataSet=${fromRows(this._trustMetrics.scores, trustColumnDefs)}
            .columnConfig=${trustColumnConfig}
            .getRowKey=${(row: TypedRow) => `${row.text(columnId('agentId'))}-${row.text(columnId('capabilityTag'))}`}
            .getRowClass=${this._getTrustRowClass}
            mode="auto"
            client-sort
            emptyMessage="No trust scores available"
          ></pages-table>
        </div>
      </div>
    `;
  }

  private _getTrustRowClass = (row: TypedRow): string => {
    const scoreText = row.text(columnId('score'));
    if (scoreText === '—') return '';
    const score = parseFloat(scoreText);
    if (isNaN(score)) return '';
    if (score < 0.5) return 'row-low-trust';
    if (score < 0.75) return 'row-medium-trust';
    return 'row-high-trust';
  };

  // ========== Gates Tab ==========

  private _renderGatesTab() {
    if (this._gateLoading) {
      return html`<div class="skeleton"></div>`;
    }

    if (this._gateError) {
      return html`
        <div class="error-card">
          <div>Failed to load gate metrics: ${this._gateError}</div>
          <button @click=${this._fetchGateMetrics}>Retry</button>
        </div>
      `;
    }

    if (!this._gateMetrics) {
      return html`<div>No gate metrics available</div>`;
    }

    const actionTypeRows: ActionTypeRow[] = Object.entries(this._gateMetrics.byActionType).map(
      ([actionType, count]) => ({ actionType, count })
    );

    const actionTypeColumnConfig: TableColumnConfig[] = [
      { id: columnId('actionType'), label: 'Action Type', sortable: true },
      { id: columnId('count'), label: 'Count', sortable: true },
    ];

    const actionTypeColumnDefs = [
      { id: columnId('actionType'), type: ColumnType.TEXT, getValue: (row: ActionTypeRow) => row.actionType },
      { id: columnId('count'), type: ColumnType.TEXT, getValue: (row: ActionTypeRow) => String(row.count) },
    ];

    return html`
      <div class="kpi-summary">
        <div class="kpi-card">
          <div class="kpi-label">Total Gates</div>
          <div class="kpi-value">${this._gateMetrics.totalGates}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Average Approval Time</div>
          <div class="kpi-value">
            ${this._gateMetrics.averageApprovalTimeSeconds !== null
              ? `${this._gateMetrics.averageApprovalTimeSeconds.toFixed(1)}s`
              : '—'}
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-title">By Action Type</div>
        <div class="table-container">
          <pages-table
            .dataSet=${fromRows(actionTypeRows, actionTypeColumnDefs)}
            .columnConfig=${actionTypeColumnConfig}
            .getRowKey=${(row: TypedRow) => row.text(columnId('actionType'))}
            mode="auto"
            client-sort
            emptyMessage="No gate data"
          ></pages-table>
        </div>
      </div>
    `;
  }

  // ========== Intervention Tab ==========

  private _renderInterventionTab() {
    if (this._interventionLoading) {
      return html`<div class="loading">Loading intervention metrics...</div>`;
    }
    if (this._interventionError) {
      return html`<div class="error">${this._interventionError}</div>`;
    }

    const m = this._interventionMetrics;
    const avgMins = m ? Math.round(m.averageResponseTimeSeconds / 60) : 0;

    return html`
      ${m ? html`
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value">${m.escalationCount}</div>
            <div class="metric-label">Escalations</div>
            <div class="metric-desc">SLA breach auto-escalations</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${m.declineRoutingCount}</div>
            <div class="metric-label">DECLINE Re-routes</div>
            <div class="metric-desc">Agent declined — out of clearance</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${m.gateRejectionCount}</div>
            <div class="metric-label">Gate Rejections</div>
            <div class="metric-desc">MLRO / compliance rejected</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${m.manualOverrideCount}</div>
            <div class="metric-label">Manual Overrides</div>
            <div class="metric-desc">Analyst overrode routing</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${avgMins}m</div>
            <div class="metric-label">Avg Response</div>
            <div class="metric-desc">Time to resolve intervention</div>
          </div>
        </div>

        ${m.recentInterventions.length > 0 ? html`
          <h3>Recent Interventions</h3>
          <div class="intervention-list">
            ${m.recentInterventions.map(i => html`
              <div class="intervention-item">
                <div class="intervention-header">
                  <span class="intervention-type ${i.type.toLowerCase()}">${i.type.replace('_', ' ')}</span>
                  <span class="intervention-date">${new Date(i.occurredAt).toLocaleDateString()}</span>
                </div>
                <div class="intervention-reason">${i.reason}</div>
                <div class="intervention-meta">Case: ${i.caseId.slice(0, 8)}… · Actor: ${i.actor}</div>
              </div>
            `)}
          </div>
        ` : nothing}
      ` : nothing}

      <h3 style="margin-top: var(--pages-space-6, 24px)">Operational Actions</h3>
      <div class="action-grid">
        ${this._renderSuspendInvestigation()}
        ${this._renderResumeInvestigation()}
        ${this._renderEscalateWorkItem()}
        ${this._renderOverrideGate()}
      </div>
    `;
  }

  private _renderSuspendInvestigation() {
    return html`
      <div class="action-card">
        <h3>Suspend Investigation</h3>
        <input
          type="text"
          placeholder="Case ID"
          .value=${this._suspendCaseId}
          @input=${(e: InputEvent) => {
            this._suspendCaseId = (e.target as HTMLInputElement).value;
          }}
        />
        <button
          ?disabled=${!this._suspendCaseId}
          @click=${this._handleSuspendInvestigation}
        >
          Suspend
        </button>
        <div class="placeholder-note">
          Endpoint: POST /api/layer9/investigations/{caseId}/suspend
          <br />
          (Check if endpoint exists — placeholder if not)
        </div>
      </div>
    `;
  }

  private _renderResumeInvestigation() {
    return html`
      <div class="action-card">
        <h3>Resume Investigation</h3>
        <input
          type="text"
          placeholder="Case ID"
          .value=${this._resumeCaseId}
          @input=${(e: InputEvent) => {
            this._resumeCaseId = (e.target as HTMLInputElement).value;
          }}
        />
        <button
          ?disabled=${!this._resumeCaseId}
          @click=${this._handleResumeInvestigation}
        >
          Resume
        </button>
        <div class="placeholder-note">
          Endpoint: POST /api/layer9/investigations/{caseId}/resume
          <br />
          (Check if endpoint exists — placeholder if not)
        </div>
      </div>
    `;
  }

  private _renderEscalateWorkItem() {
    return html`
      <div class="action-card">
        <h3>Escalate Work Item</h3>
        <input
          type="text"
          placeholder="Work Item ID"
          .value=${this._escalateWorkItemId}
          @input=${(e: InputEvent) => {
            this._escalateWorkItemId = (e.target as HTMLInputElement).value;
          }}
        />
        <button
          ?disabled=${!this._escalateWorkItemId}
          @click=${this._handleEscalateWorkItem}
        >
          Escalate
        </button>
        <div class="placeholder-note">Placeholder action — endpoint TBD</div>
      </div>
    `;
  }

  private _renderOverrideGate() {
    return html`
      <div class="action-card">
        <h3>Override Gate</h3>
        <input
          type="text"
          placeholder="Work Item ID"
          .value=${this._overrideWorkItemId}
          @input=${(e: InputEvent) => {
            this._overrideWorkItemId = (e.target as HTMLInputElement).value;
          }}
        />
        <button
          ?disabled=${!this._overrideWorkItemId}
          @click=${this._handleOverrideGate}
        >
          Override
        </button>
        <div class="placeholder-note">Placeholder action — endpoint TBD</div>
      </div>
    `;
  }

  private async _handleSuspendInvestigation(): Promise<void> {
    if (!window.confirm(`Suspend investigation ${this._suspendCaseId}?`)) {
      return;
    }

    try {
      const response = await fetch(`/api/layer9/investigations/${this._suspendCaseId}/suspend`, {
        method: 'POST',
      });
      if (response.ok) {
        alert(`Investigation ${this._suspendCaseId} suspended`);
        this._suspendCaseId = '';
      } else {
        alert(`Failed to suspend: HTTP ${response.status}`);
      }
    } catch (error) {
      alert(`Error: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private async _handleResumeInvestigation(): Promise<void> {
    if (!window.confirm(`Resume investigation ${this._resumeCaseId}?`)) {
      return;
    }

    try {
      const response = await fetch(`/api/layer9/investigations/${this._resumeCaseId}/resume`, {
        method: 'POST',
      });
      if (response.ok) {
        alert(`Investigation ${this._resumeCaseId} resumed`);
        this._resumeCaseId = '';
      } else {
        alert(`Failed to resume: HTTP ${response.status}`);
      }
    } catch (error) {
      alert(`Error: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private _handleEscalateWorkItem(): void {
    if (!window.confirm(`Escalate work item ${this._escalateWorkItemId}?`)) {
      return;
    }
    alert(`Escalate work item ${this._escalateWorkItemId} — placeholder action`);
    this._escalateWorkItemId = '';
  }

  private _handleOverrideGate(): void {
    if (!window.confirm(`Override gate for work item ${this._overrideWorkItemId}?`)) {
      return;
    }
    alert(`Override gate for work item ${this._overrideWorkItemId} — placeholder action`);
    this._overrideWorkItemId = '';
  }
}
