import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { ComplianceEvidence } from '../types.js';
import type { TableColumnConfig, ColumnRenderer } from '@casehubio/pages-table';
import type { TypedRow, CellValue, ColumnId } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import { ColumnType, columnId } from '@casehubio/pages-data/dist/dataset/types.js';
import '@casehubio/pages-table';
import '@casehubio/blocks-ui-work-item-detail';

interface RequirementRow {
  id: string;
  citation: string;
  mechanism: string;
  status: string; // MET | NOT_MET | PARTIAL
}

@customElement('aml-compliance-panel')
export class AmlCompliancePanel extends LitElement {
  @property({ attribute: false }) item: any = null;

  get caseId(): string { return this.item?.text?.('caseId') ?? this.item?.caseId ?? ''; }

  @state() private _evidence: ComplianceEvidence | null = null;
  @state() private _loading = false;
  @state() private _error: string | null = null;

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

    .card {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      background: var(--pages-neutral-1, #ffffff);
    }

    .card-row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 8px) 0;
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
    }

    .card-row:last-child {
      border-bottom: none;
    }

    .card-label {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-8, #404040);
      font-weight: 500;
    }

    .card-value {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
      font-weight: 400;
      text-align: right;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .status-badge::before {
      content: '';
      display: inline-block;
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .status-badge.met {
      background: var(--pages-success-3, #dcfce7);
      color: var(--pages-success-9, #16a34a);
    }

    .status-badge.met::before {
      background: var(--pages-success-9, #16a34a);
    }

    .status-badge.partial {
      background: var(--pages-warning-3, #fef3c7);
      color: var(--pages-warning-9, #d97706);
    }

    .status-badge.partial::before {
      background: var(--pages-warning-9, #d97706);
    }

    .status-badge.not_met {
      background: var(--pages-error-3, #fee2e2);
      color: var(--pages-error-9, #dc2626);
    }

    .status-badge.not_met::before {
      background: var(--pages-error-9, #dc2626);
    }

    .sar-status-card {
      background: var(--pages-accent-1, #f0f9ff);
      border: 2px solid var(--pages-accent-4, #93c5fd);
      border-radius: 8px;
      padding: var(--pages-space-5, 20px);
      margin-bottom: var(--pages-space-4, 16px);
    }

    .sar-status-title {
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 700;
      color: var(--pages-accent-11, #1e3a8a);
      margin-bottom: var(--pages-space-3, 12px);
    }

    .sar-status-row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 8px) 0;
    }

    .sar-status-label {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-accent-9, #3b82f6);
      font-weight: 600;
    }

    .sar-status-value {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-accent-11, #1e3a8a);
      font-weight: 500;
    }

    .erasure-button {
      margin-top: var(--pages-space-3, 12px);
      padding: var(--pages-space-2, 8px) var(--pages-space-4, 16px);
      background: var(--pages-accent-9, #3b82f6);
      color: white;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 600;
      transition: background 150ms;
    }

    .erasure-button:hover {
      background: var(--pages-accent-10, #2563eb);
    }

    .erasure-button:disabled {
      background: var(--pages-neutral-4, #d4d4d4);
      cursor: not-allowed;
    }

    .skeleton {
      height: 20px;
      background: linear-gradient(
        90deg,
        var(--pages-neutral-2, #f5f5f5) 25%,
        var(--pages-neutral-3, #e5e5e5) 50%,
        var(--pages-neutral-2, #f5f5f5) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
      border-radius: 4px;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .error-card {
      background: var(--pages-error-1, #fef2f2);
      border-color: var(--pages-error-4, #fca5a5);
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

    .signature-row {
      margin-top: var(--pages-space-4, 16px);
      padding: var(--pages-space-3, 12px);
      background: var(--pages-neutral-2, #f5f5f5);
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-8, #404040);
      word-break: break-all;
    }
  `;

  override updated(changedProps: Map<string, unknown>): void {
    if (changedProps.has('item') && this.caseId) {
      this._fetchCompliance();
    }
  }

  private async _fetchCompliance(): Promise<void> {
    this._loading = true;
    this._error = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/compliance-evidence`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._evidence = await response.json();
    } catch (error) {
      this._error = error instanceof Error ? error.message : String(error);
    } finally {
      this._loading = false;
    }
  }

  override render() {
    if (this._loading) {
      return html`
        <div class="section">
          <div class="card">
            <div class="skeleton" style="margin-bottom: 12px;"></div>
            <div class="skeleton" style="width: 60%;"></div>
          </div>
        </div>
      `;
    }

    if (this._error) {
      return html`
        <div class="section">
          <div class="card error-card">
            <div>Failed to load compliance evidence: ${this._error}</div>
            <button @click=${this._fetchCompliance}>Retry</button>
          </div>
        </div>
      `;
    }

    if (!this._evidence) {
      return html`<div class="placeholder">No compliance evidence available</div>`;
    }

    return html`
      ${this._renderSarStatusCard()}
      ${this._renderFinCenRequirements()}
      ${this._renderOfficerReview()}
      ${this._renderGdprErasure()}
      ${this._renderSignature()}
    `;
  }

  private _renderSarStatusCard() {
    if (!this._evidence) return nothing;

    // SLA requirement contains SAR-related data
    const sla = this._evidence.sla;
    const slaMet = sla.slaMet ? 'Met' : 'Breached';
    const slaClass = sla.slaMet ? 'met' : 'not_met';

    return html`
      <div class="sar-status-card">
        <div class="sar-status-title">
          Suspicious Activity Report (SAR)
        </div>
        <div class="sar-status-row">
          <span class="sar-status-label">SLA Status</span>
          <span class="sar-status-value">
            <span class="status-badge ${slaClass}">${slaMet}</span>
          </span>
        </div>
        <div class="sar-status-row">
          <span class="sar-status-label">Claim Deadline</span>
          <span class="sar-status-value">
            ${new Date(sla.claimDeadline).toLocaleString()}
          </span>
        </div>
        ${sla.completedAt
          ? html`
              <div class="sar-status-row">
                <span class="sar-status-label">Completed At</span>
                <span class="sar-status-value">
                  ${new Date(sla.completedAt).toLocaleString()}
                </span>
              </div>
            `
          : nothing}
        <div class="sar-status-row">
          <span class="sar-status-label">WorkItem ID</span>
          <span class="sar-status-value">${sla.workItemId}</span>
        </div>
        <div class="sar-status-row">
          <span class="sar-status-label">Candidate Groups</span>
          <span class="sar-status-value">${sla.candidateGroups.join(', ')}</span>
        </div>
      </div>
    `;
  }

  private _renderFinCenRequirements() {
    if (!this._evidence) return nothing;

    const rows: RequirementRow[] = [
      {
        id: this._evidence.auditChain.id,
        citation: this._evidence.auditChain.citation,
        mechanism: this._evidence.auditChain.mechanism,
        status: this._evidence.auditChain.status,
      },
      {
        id: this._evidence.sla.id,
        citation: this._evidence.sla.citation,
        mechanism: this._evidence.sla.mechanism,
        status: this._evidence.sla.status,
      },
      {
        id: this._evidence.trustRouting.id,
        citation: this._evidence.trustRouting.citation,
        mechanism: this._evidence.trustRouting.mechanism,
        status: this._evidence.trustRouting.status,
      },
      {
        id: this._evidence.gdprErasure.id,
        citation: this._evidence.gdprErasure.citation,
        mechanism: this._evidence.gdprErasure.mechanism,
        status: this._evidence.gdprErasure.status,
      },
    ];

    const columnConfig: TableColumnConfig[] = [
      { id: columnId('id'), label: 'Requirement ID', sortable: false },
      { id: columnId('citation'), label: 'Citation', sortable: false },
      { id: columnId('mechanism'), label: 'Mechanism', sortable: false },
      { id: columnId('status'), label: 'Status', sortable: false },
    ];

    const columnDefs = [
      { id: columnId('id'), type: ColumnType.TEXT, getValue: (row: RequirementRow) => row.id },
      { id: columnId('citation'), type: ColumnType.TEXT, getValue: (row: RequirementRow) => row.citation },
      { id: columnId('mechanism'), type: ColumnType.TEXT, getValue: (row: RequirementRow) => row.mechanism },
      { id: columnId('status'), type: ColumnType.TEXT, getValue: (row: RequirementRow) => row.status },
    ];

    const columnRenderers: ReadonlyMap<ColumnId, ColumnRenderer> = new Map([
      [columnId('status'), (cell: CellValue) => {
        const status = cell.type === 'NULL' ? '' : (cell as { value: string }).value;
        const statusClass = status.toLowerCase().replace('_', '-');
        return html`<span class="status-badge ${statusClass}">${status}</span>`;
      }],
    ]);

    const dataSet = fromRows(rows, columnDefs);

    return html`
      <div class="section">
        <div class="section-title">FinCEN Requirements</div>
        <pages-table
          .dataSet=${dataSet}
          .columnConfig=${columnConfig}
          .columnRenderers=${columnRenderers}
          mode="auto"
          .getRowKey=${(row: TypedRow) => row.text(columnId('id'))}
        ></pages-table>
      </div>
    `;
  }

  private _renderOfficerReview() {
    if (!this._evidence) return nothing;

    return html`
      <div class="section">
        <div class="section-title">Officer Review</div>
        <blocks-work-item-detail
          endpoint=""
          .workItemId=${this._evidence.sla.workItemId}
          .identity=${{ userId: 'officer-001', groups: ['compliance-officers'] }}
        ></blocks-work-item-detail>
      </div>
    `;
  }

  private _renderGdprErasure() {
    if (!this._evidence) return nothing;

    const gdpr = this._evidence.gdprErasure;

    return html`
      <div class="section">
        <div class="section-title">GDPR Art.17 Erasure</div>
        <div class="card">
          <div class="card-row">
            <span class="card-label">Tokenisation Enabled</span>
            <span class="card-value">${gdpr.tokenisationEnabled ? 'Yes' : 'No'}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Erasure Receipt Enabled</span>
            <span class="card-value">${gdpr.erasureReceiptEnabled ? 'Yes' : 'No'}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Memories Erased</span>
            <span class="card-value">${gdpr.erasureReceiptCount}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Erasure Endpoint</span>
            <span class="card-value">${gdpr.erasureEndpoint}</span>
          </div>
          <button
            class="erasure-button"
            @click=${this._requestErasure}
            disabled
            title="Placeholder - endpoint wiring to be added"
          >
            Request Art.17 Erasure
          </button>
        </div>
      </div>
    `;
  }

  private _renderSignature() {
    if (!this._evidence?.signature) return nothing;

    return html`
      <div class="signature-row">
        <strong>Evidence Signature:</strong><br/>
        ${this._evidence.signature}
      </div>
    `;
  }

  private _requestErasure(): void {
    // Placeholder - POST endpoint exists but wiring can be done later
    console.log('Request erasure for case:', this.caseId);
  }
}
