import { LitElement, html, css, nothing } from 'lit';
import '@casehubio/blocks-ui-blocks-timeline';
import { amlInvestigationTimelineStrategy } from '../strategies/aml-investigation-timeline.js';
import { customElement, property, state } from 'lit/decorators.js';
import type {
  InvestigationSummaryResponse,
  Layer6InvestigationResponse,
  PriorContextResponse,
} from '../types.js';

@customElement('aml-investigation-overview')
export class AmlInvestigationOverview extends LitElement {
  @property({ attribute: false }) item: any = null;

  get caseId(): string { return this.item?.text?.('caseId') ?? this.item?.caseId ?? ''; }
  get caseData(): InvestigationSummaryResponse | null {
    if (!this.item) return null;
    if (this.item.text) {
      // TypedRow — extract fields
      return {
        caseId: this.item.text('caseId'),
        status: this.item.text('status'),
        outcomeType: this.item.text('outcomeType'),
        transactionId: this.item.text('transactionId'),
        originAccount: this.item.text('originAccount'),
        destinationAccount: this.item.text('destinationAccount'),
        amount: this.item.number('amount'),
        currency: this.item.text('currency'),
        flagReason: this.item.text('flagReason'),
        createdAt: this.item.date('createdAt').toISOString(),
      } as InvestigationSummaryResponse;
    }
    return this.item;
  }

  @state() private _layer6Data: Layer6InvestigationResponse | null = null;
  @state() private _layer6Loading = false;
  @state() private _layer6Error: string | null = null;

  @state() private _priorContext: PriorContextResponse | null = null;
  @state() private _priorContextLoading = false;
  @state() private _priorContextError: string | null = null;

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

    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: var(--pages-space-3, 12px);
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

    .badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .badge.completed {
      background: var(--pages-success-3, #dcfce7);
      color: var(--pages-success-9, #16a34a);
    }

    .badge.in-progress {
      background: var(--pages-accent-3, #dbeafe);
      color: var(--pages-accent-9, #3b82f6);
    }

    .badge.failed {
      background: var(--pages-error-3, #fee2e2);
      color: var(--pages-error-9, #dc2626);
    }

    .badge.cancelled,
    .badge.suspended {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-8, #404040);
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

    .warning-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 8px;
      font-size: var(--pages-font-size-xs, 11px);
      background: var(--pages-warning-3, #fef3c7);
      color: var(--pages-warning-9, #d97706);
      font-weight: 600;
    }

    .fact-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .fact-item {
      padding: var(--pages-space-3, 12px);
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
      display: grid;
      grid-template-columns: 100px 1fr 120px 80px;
      gap: var(--pages-space-2, 8px);
      align-items: start;
    }

    .fact-item:last-child {
      border-bottom: none;
    }

    .fact-domain {
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      color: var(--pages-accent-9, #3b82f6);
      text-transform: uppercase;
    }

    .fact-text {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .fact-date {
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-7, #525252);
    }

    .fact-confidence {
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-8, #404040);
      font-weight: 500;
      text-align: right;
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
  `;

  override updated(changedProps: Map<string, unknown>): void {
    if (changedProps.has('item') && this.caseId) {
      this._fetchLayer6Data();
      this._fetchPriorContext();
    }
  }

  private async _fetchLayer6Data(): Promise<void> {
    this._layer6Loading = true;
    this._layer6Error = null;
    try {
      const response = await fetch(`/api/layer6/investigations/${this.caseId}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._layer6Data = await response.json();
    } catch (error) {
      this._layer6Error = error instanceof Error ? error.message : String(error);
    } finally {
      this._layer6Loading = false;
    }
  }

  private async _fetchPriorContext(): Promise<void> {
    this._priorContextLoading = true;
    this._priorContextError = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/prior-context`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._priorContext = await response.json();
    } catch (error) {
      this._priorContextError = error instanceof Error ? error.message : String(error);
    } finally {
      this._priorContextLoading = false;
    }
  }

  override render() {
    if (!this.caseData) {
      return html`<div class="placeholder">No investigation data available</div>`;
    }

    return html`
      ${this._renderTransactionCard()}
      ${this._renderStatusSection()}
      ${this._renderFailureContext()}
      ${this._renderPriorContext()}
      ${this._renderTimeline()}
    `;
  }

  private _renderTransactionCard() {
    if (!this.caseData) return nothing;

    return html`
      <div class="section">
        <div class="section-title">Transaction Details</div>
        <div class="card">
          <div class="card-row">
            <span class="card-label">Transaction ID</span>
            <span class="card-value">${this.caseData.transactionId}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Origin Account</span>
            <span class="card-value">${this.caseData.originAccount}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Destination Account</span>
            <span class="card-value">${this.caseData.destinationAccount}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Amount</span>
            <span class="card-value">
              ${this.caseData.amount.toLocaleString()} ${this.caseData.currency}
            </span>
          </div>
          <div class="card-row">
            <span class="card-label">Flag Reason</span>
            <span class="card-value">${this.caseData.flagReason}</span>
          </div>
        </div>
      </div>
    `;
  }

  private _renderStatusSection() {
    if (this._layer6Loading) {
      return html`
        <div class="section">
          <div class="section-title">Status & Outcome</div>
          <div class="card">
            <div class="skeleton"></div>
          </div>
        </div>
      `;
    }

    if (this._layer6Error) {
      return html`
        <div class="section">
          <div class="section-title">Status & Outcome</div>
          <div class="card error-card">
            <div>Failed to load investigation status: ${this._layer6Error}</div>
            <button @click=${this._fetchLayer6Data}>Retry</button>
          </div>
        </div>
      `;
    }

    if (!this._layer6Data) return nothing;

    const statusClass = this._layer6Data.status.toLowerCase().replace('_', '-');
    const outcomeText = this._layer6Data.outcome
      ? `${this._layer6Data.outcome.type}${
          this._layer6Data.outcome.reason ? `: ${this._layer6Data.outcome.reason}` : ''
        }`
      : 'No outcome recorded';

    return html`
      <div class="section">
        <div class="section-title">Status & Outcome</div>
        <div class="card">
          <div class="card-row">
            <span class="card-label">Status</span>
            <span class="card-value">
              <span class="badge ${statusClass}">${this._layer6Data.status}</span>
            </span>
          </div>
          <div class="card-row">
            <span class="card-label">Outcome</span>
            <span class="card-value">${outcomeText}</span>
          </div>
        </div>
      </div>
    `;
  }

  private _renderFailureContext() {
    if (!this._layer6Data?.failureContext) return nothing;

    const fc = this._layer6Data.failureContext;
    const failedStatus = ['FAILED', 'CANCELLED', 'SUSPENDED'].includes(
      this._layer6Data.status
    );

    if (!failedStatus) return nothing;

    return html`
      <div class="section">
        <div class="section-title">Failure Context</div>
        <div class="card">
          <div class="card-row">
            <span class="card-label">Trigger Goal</span>
            <span class="card-value">${fc.triggerGoalName} (${fc.triggerGoalKind})</span>
          </div>
          <div class="card-row">
            <span class="card-label">Occurred At</span>
            <span class="card-value">
              ${new Date(fc.occurredAt).toLocaleString()}
            </span>
          </div>
          ${fc.failureEvents.length > 0
            ? html`
                <div style="margin-top: var(--pages-space-3, 12px);">
                  <div class="card-label" style="margin-bottom: var(--pages-space-2, 8px);">
                    Failure Events (${fc.failureEvents.length})
                  </div>
                  ${fc.failureEvents.map(
                    event => html`
                      <div style="padding: var(--pages-space-2, 8px); background: var(--pages-neutral-2, #f5f5f5); border-radius: 4px; margin-bottom: var(--pages-space-2, 8px);">
                        <div style="font-size: var(--pages-font-size-xs, 11px); color: var(--pages-neutral-8, #404040);">
                          ${event.eventType} — ${event.workerId}
                        </div>
                        <div style="font-size: var(--pages-font-size-sm, 13px); margin-top: 4px;">
                          ${event.detail}
                        </div>
                        <div style="font-size: var(--pages-font-size-xs, 11px); color: var(--pages-neutral-7, #525252); margin-top: 4px;">
                          ${new Date(event.timestamp).toLocaleString()}
                        </div>
                      </div>
                    `
                  )}
                </div>
              `
            : nothing}
        </div>
      </div>
    `;
  }

  private _renderPriorContext() {
    if (this._priorContextLoading) {
      return html`
        <div class="section">
          <div class="section-title">Prior Context</div>
          <div class="card">
            <div class="skeleton"></div>
          </div>
        </div>
      `;
    }

    if (this._priorContextError) {
      return html`
        <div class="section">
          <div class="section-title">Prior Context</div>
          <div class="card error-card">
            <div>Failed to load prior context: ${this._priorContextError}</div>
            <button @click=${this._fetchPriorContext}>Retry</button>
          </div>
        </div>
      `;
    }

    if (!this._priorContext) return nothing;

    return html`
      <div class="section">
        <div class="section-title">
          Prior Context
          ${this._priorContext.knownHighRisk
            ? html`<span class="warning-badge">High Risk Entity</span>`
            : nothing}
        </div>
        <div class="card">
          <div class="card-row">
            <span class="card-label">Has History</span>
            <span class="card-value">${this._priorContext.hasHistory ? 'Yes' : 'No'}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Entity Risk Count</span>
            <span class="card-value">${this._priorContext.entityRiskCount}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Network Count</span>
            <span class="card-value">${this._priorContext.networkCount}</span>
          </div>
          <div class="card-row">
            <span class="card-label">Pattern Count</span>
            <span class="card-value">${this._priorContext.patternCount}</span>
          </div>

          ${this._priorContext.facts.length > 0
            ? html`
                <div style="margin-top: var(--pages-space-4, 16px);">
                  <div class="card-label" style="margin-bottom: var(--pages-space-2, 8px);">
                    Recent Facts (${this._priorContext.facts.length})
                  </div>
                  <ul class="fact-list">
                    ${this._priorContext.facts.map(
                      fact => html`
                        <li class="fact-item">
                          <span class="fact-domain">${fact.domain}</span>
                          <span class="fact-text">${fact.text}</span>
                          <span class="fact-date">
                            ${new Date(fact.createdAt).toLocaleDateString()}
                          </span>
                          <span class="fact-confidence">
                            ${fact.confidence ?? 'N/A'}
                          </span>
                        </li>
                      `
                    )}
                  </ul>
                </div>
              `
            : html`
                <div style="margin-top: var(--pages-space-3, 12px); color: var(--pages-neutral-7, #525252); font-size: var(--pages-font-size-sm, 13px);">
                  No prior facts available
                </div>
              `}
        </div>
      </div>
    `;
  }

  private _renderTimeline() {
    if (!this.caseId) return nothing;
    return html`
      <div class="section">
        <div class="section-title">Case Timeline</div>
        <blocks-timeline
          endpoint="/api/investigations/${this.caseId}/audit-trail"
          .strategy=${amlInvestigationTimelineStrategy()}
          layout="vertical"
        ></blocks-timeline>
      </div>
    `;
  }
}
