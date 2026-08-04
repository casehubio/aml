import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import type { TypedRow } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import { ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import '@casehubio/pages-table';
import '@casehubio/blocks-ui-trust-score-panel';
import type {
  Layer6InvestigationResponse,
  WorkerRoutingDecision,
  InvestigationGatesResponse,
  GateDecisionResponse,
} from '../types.js';

@customElement('aml-routing-panel')
export class AmlRoutingPanel extends LitElement {
  @property({ attribute: false }) item: any = null;

  get caseId(): string { return this.item?.text?.('caseId') ?? this.item?.caseId ?? ''; }

  @state() private _routingData: Layer6InvestigationResponse | null = null;
  @state() private _routingLoading = false;
  @state() private _routingError: string | null = null;

  @state() private _gatesData: InvestigationGatesResponse | null = null;
  @state() private _gatesLoading = false;
  @state() private _gatesError: string | null = null;

  private _routingColumnConfig: TableColumnConfig[] = [
    {
      id: 'capabilityTag',
      label: 'Capability',
      sortable: true,
    },
    {
      id: 'selectedWorker',
      label: 'Selected Worker',
      sortable: true,
    },
    {
      id: 'trustScore',
      label: 'Trust Score',
      sortable: true,
    },
  ];

  private _routingColumnDefs = [
    { id: 'capabilityTag', type: ColumnType.TEXT, getValue: (row: WorkerRoutingDecision) => row.capabilityTag },
    { id: 'selectedWorker', type: ColumnType.TEXT, getValue: (row: WorkerRoutingDecision) => row.selectedWorker },
    { id: 'trustScore', type: ColumnType.TEXT, getValue: (row: WorkerRoutingDecision) => row.trustScore !== null ? row.trustScore.toFixed(3) : '—' },
  ];

  static override styles = css`
    :host {
      display: block;
      padding: var(--pages-space-4, 16px);
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
    }

    .gate-card {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      background: var(--pages-neutral-1, #ffffff);
      margin-bottom: var(--pages-space-3, 12px);
    }

    .gate-card:last-child {
      margin-bottom: 0;
    }

    .gate-header {
      display: flex;
      justify-content: space-between;
      align-items: start;
      margin-bottom: var(--pages-space-3, 12px);
      padding-bottom: var(--pages-space-3, 12px);
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
    }

    .gate-title {
      font-size: var(--pages-font-size-md, 14px);
      font-weight: 600;
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .gate-row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 8px) 0;
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
    }

    .gate-row:last-child {
      border-bottom: none;
    }

    .gate-label {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-8, #404040);
      font-weight: 500;
    }

    .gate-value {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
      font-weight: 400;
      text-align: right;
    }

    .badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .badge.approved,
    .badge.completed {
      background: var(--pages-success-3, #dcfce7);
      color: var(--pages-success-9, #16a34a);
    }

    .badge.rejected {
      background: var(--pages-error-3, #fee2e2);
      color: var(--pages-error-9, #dc2626);
    }

    .badge.pending,
    .badge.assigned,
    .badge.in_progress {
      background: var(--pages-warning-3, #fef3c7);
      color: var(--pages-warning-9, #d97706);
    }

    .badge.expired {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-8, #404040);
    }

    .tag-list {
      display: flex;
      gap: var(--pages-space-2, 8px);
      flex-wrap: wrap;
    }

    .tag {
      display: inline-block;
      padding: 2px 8px;
      background: var(--pages-accent-3, #dbeafe);
      color: var(--pages-accent-9, #3b82f6);
      border-radius: 4px;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 500;
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

    .placeholder {
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px dashed var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-6, 24px);
      text-align: center;
      color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px);
      font-style: italic;
    }

    .trust-score-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: var(--pages-space-3, 12px);
    }

    .trust-score-card {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-3, 12px);
      background: var(--pages-neutral-1, #ffffff);
      display: flex;
      flex-direction: column;
      gap: var(--pages-space-2, 8px);
    }

    .trust-card-header {
      display: flex;
      flex-direction: column;
      gap: var(--pages-space-1, 4px);
    }

    .trust-worker-name {
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-7, #525252);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Trust score row styling based on value */
    .row-low-trust {
      background-color: var(--pages-error-1, #fef2f2) !important;
    }

    .row-medium-trust {
      background-color: var(--pages-warning-1, #fffbeb) !important;
    }

    .row-high-trust {
      background-color: var(--pages-success-1, #f0fdf4) !important;
    }
  `;

  override updated(changedProps: Map<string, unknown>): void {
    if (changedProps.has('item') && this.caseId) {
      this._fetchRoutingData();
      this._fetchGatesData();
    }
  }

  private async _fetchRoutingData(): Promise<void> {
    this._routingLoading = true;
    this._routingError = null;
    try {
      const response = await fetch(`/api/layer6/investigations/${this.caseId}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._routingData = await response.json();
    } catch (error) {
      this._routingError = error instanceof Error ? error.message : String(error);
    } finally {
      this._routingLoading = false;
    }
  }

  private async _fetchGatesData(): Promise<void> {
    this._gatesLoading = true;
    this._gatesError = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/gates`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._gatesData = await response.json();
    } catch (error) {
      this._gatesError = error instanceof Error ? error.message : String(error);
    } finally {
      this._gatesLoading = false;
    }
  }

  private _getRowClass = (row: TypedRow): string => {
    const trustScoreText = row.text('trustScore');
    if (trustScoreText === '—') return '';
    const trustScore = parseFloat(trustScoreText);
    if (isNaN(trustScore)) return '';
    if (trustScore < 0.5) return 'row-low-trust';
    if (trustScore < 0.75) return 'row-medium-trust';
    return 'row-high-trust';
  };

  override render() {
    return html`
      ${this._renderRoutingDecisions()}
      ${this._renderTrustScores()}
      ${this._renderGateDecisions()}
    `;
  }

  private _renderRoutingDecisions() {
    if (this._routingLoading) {
      return html`
        <div class="section">
          <div class="section-title">Routing Decisions</div>
          <div class="skeleton"></div>
        </div>
      `;
    }

    if (this._routingError) {
      return html`
        <div class="section">
          <div class="section-title">Routing Decisions</div>
          <div class="error-card">
            <div>Failed to load routing decisions: ${this._routingError}</div>
            <button @click=${this._fetchRoutingData}>Retry</button>
          </div>
        </div>
      `;
    }

    if (!this._routingData || this._routingData.routingDecisions.length === 0) {
      return html`
        <div class="section">
          <div class="section-title">Routing Decisions</div>
          <div class="placeholder">No routing decisions available</div>
        </div>
      `;
    }

    const dataSet = fromRows(this._routingData.routingDecisions, this._routingColumnDefs);

    return html`
      <div class="section">
        <div class="section-title">Routing Decisions</div>
        <div class="table-container">
          <pages-table
            .dataSet=${dataSet}
            .columnConfig=${this._routingColumnConfig}
            .getRowKey=${(row: TypedRow) => row.text('capabilityTag')}
            .getRowClass=${this._getRowClass}
            mode="auto"
            client-sort
            emptyMessage="No routing decisions found"
          ></pages-table>
        </div>
      </div>
    `;
  }

  private _renderTrustScores() {
    if (!this._routingData || this._routingData.routingDecisions.length === 0) {
      return nothing;
    }

    return html`
      <div class="section">
        <div class="section-title">Trust Scores</div>
        <div class="trust-score-grid">
          ${this._routingData.routingDecisions.map(d => html`
            <div class="trust-score-card">
              <div class="trust-card-header">
                <span class="tag">${d.capabilityTag}</span>
                <span class="trust-worker-name">${d.selectedWorker}</span>
              </div>
              <blocks-trust-score-panel
                mode="compact"
                .score=${d.trustScore ?? undefined}
              ></blocks-trust-score-panel>
            </div>
          `)}
        </div>
      </div>
    `;
  }

  private _renderGateDecisions() {
    if (this._gatesLoading) {
      return html`
        <div class="section">
          <div class="section-title">Gate Decisions</div>
          <div class="skeleton"></div>
        </div>
      `;
    }

    if (this._gatesError) {
      return html`
        <div class="section">
          <div class="section-title">Gate Decisions</div>
          <div class="error-card">
            <div>Failed to load gate decisions: ${this._gatesError}</div>
            <button @click=${this._fetchGatesData}>Retry</button>
          </div>
        </div>
      `;
    }

    if (!this._gatesData || this._gatesData.gates.length === 0) {
      return html`
        <div class="section">
          <div class="section-title">Gate Decisions</div>
          <div class="placeholder">No gate decisions available</div>
        </div>
      `;
    }

    return html`
      <div class="section">
        <div class="section-title">Gate Decisions</div>
        ${this._gatesData.gates.map(gate => this._renderGateCard(gate))}
      </div>
    `;
  }

  private _renderGateCard(gate: GateDecisionResponse) {
    const statusClass = gate.status.toLowerCase().replace('_', '-');

    return html`
      <div class="gate-card">
        <div class="gate-header">
          <div class="gate-title">${gate.actionType}</div>
          <span class="badge ${statusClass}">${gate.status}</span>
        </div>

        <div class="gate-row">
          <span class="gate-label">Gate Policy</span>
          <span class="gate-value">${gate.gatePolicy}</span>
        </div>

        <div class="gate-row">
          <span class="gate-label">Reversible</span>
          <span class="gate-value">${gate.reversible ? 'Yes' : 'No'}</span>
        </div>

        ${gate.description
          ? html`
              <div class="gate-row">
                <span class="gate-label">Description</span>
                <span class="gate-value">${gate.description}</span>
              </div>
            `
          : nothing}

        ${gate.candidateGroups.length > 0
          ? html`
              <div class="gate-row">
                <span class="gate-label">Candidate Groups</span>
                <div class="tag-list">
                  ${gate.candidateGroups.map(group => html`<span class="tag">${group}</span>`)}
                </div>
              </div>
            `
          : nothing}

        ${gate.approvedBy
          ? html`
              <div class="gate-row">
                <span class="gate-label">Approved By</span>
                <span class="gate-value">${gate.approvedBy}</span>
              </div>
            `
          : nothing}

        ${gate.approvedAt
          ? html`
              <div class="gate-row">
                <span class="gate-label">Approved At</span>
                <span class="gate-value">
                  ${new Date(gate.approvedAt).toLocaleString()}
                </span>
              </div>
            `
          : nothing}

        ${gate.expiresAt
          ? html`
              <div class="gate-row">
                <span class="gate-label">Expires At</span>
                <span class="gate-value">
                  ${new Date(gate.expiresAt).toLocaleString()}
                </span>
              </div>
            `
          : nothing}
      </div>

      <!-- NOTE: Future integration point for <approval-gate> from blocks-ui.
           The current gate data is historical (read-only display of past decisions).
           When <approval-gate> is available, use it here for live gate interactions
           (approve/reject actions), falling back to this card-based display for
           completed or expired gates. -->
    `;
  }
}
