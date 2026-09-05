import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { MetricDefinition } from '@casehubio/blocks-ui-kpi-metric-row';
import type { ThroughputMetrics, GateMetrics } from '../types.js';
import '@casehubio/blocks-ui-kpi-metric-row';
import '../views/aml-sar-quality-tab.js';

function throughputToMetrics(m: ThroughputMetrics): MetricDefinition[] {
  const completed = m.byStatus['COMPLETED'] ?? 0;
  const inProgress = m.byStatus['IN_PROGRESS'] ?? 0;
  const failed = m.byStatus['FAILED'] ?? 0;
  return [
    { key: 'total', value: m.totalInvestigations, label: 'Total Investigations' },
    { key: 'completed', value: completed, label: 'Completed' },
    { key: 'in-progress', value: inProgress, label: 'In Progress' },
    { key: 'failed', value: failed, label: 'Failed', status: failed > 0 ? 'warning' : 'normal' },
  ];
}

function gateToMetrics(m: GateMetrics): MetricDefinition[] {
  const pending = m.byStatus['PENDING'] ?? 0;
  const approved = m.byStatus['COMPLETED'] ?? 0;
  const rejected = m.byStatus['REJECTED'] ?? 0;
  return [
    { key: 'total-gates', value: m.totalGates, label: 'Total Gates' },
    { key: 'pending', value: pending, label: 'Pending', status: pending > 3 ? 'warning' : 'normal' },
    { key: 'approved', value: approved, label: 'Approved' },
    { key: 'rejected', value: rejected, label: 'Rejected', status: rejected > 0 ? 'warning' : 'normal' },
    { key: 'avg-approval', value: m.averageApprovalTimeSeconds != null ? `${Math.round(m.averageApprovalTimeSeconds / 3600)}h` : '—', label: 'Avg Approval Time' },
  ];
}

@customElement('aml-operations-dock')
export class AmlOperationsDock extends LitElement {
  @state() private _throughputMetrics: MetricDefinition[] = [];
  @state() private _gateMetrics: MetricDefinition[] = [];
  @state() private _loading = true;

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; }
    .section-label {
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--pages-neutral-8, #404040);
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px) var(--pages-space-2, 8px);
    }
    .section { padding: 0 var(--pages-space-4, 16px) var(--pages-space-4, 16px); }
    .loading {
      padding: var(--pages-space-6, 24px); text-align: center;
      color: var(--pages-neutral-7, #525252); font-size: var(--pages-font-size-sm, 13px);
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this._fetchAll();
  }

  private async _fetchAll() {
    this._loading = true;
    try {
      const [throughputRes, gateRes] = await Promise.all([
        fetch('/api/metrics/throughput'),
        fetch('/api/metrics/gates'),
      ]);
      if (throughputRes.ok) {
        this._throughputMetrics = throughputToMetrics(await throughputRes.json());
      }
      if (gateRes.ok) {
        this._gateMetrics = gateToMetrics(await gateRes.json());
      }
    } finally {
      this._loading = false;
    }
  }

  override render() {
    if (this._loading) return html`<div class="loading">Loading metrics...</div>`;
    return html`
      <div class="section-label">Throughput</div>
      <div class="section">
        <blocks-kpi-metric-row .metrics=${this._throughputMetrics} density="compact"></blocks-kpi-metric-row>
      </div>

      <div class="section-label">Gate Activity</div>
      <div class="section">
        <blocks-kpi-metric-row .metrics=${this._gateMetrics} density="compact"></blocks-kpi-metric-row>
      </div>

      <div class="section-label">SAR Quality</div>
      <div class="section">
        <aml-sar-quality-tab></aml-sar-quality-tab>
      </div>
    `;
  }
}
