import { LitElement, html, css, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { SarQualityReport, OutcomeSegment } from '../types.js';

@customElement('aml-sar-quality-tab')
export class AmlSarQualityTab extends LitElement {

  @state() private _metrics: SarQualityReport | null = null;
  @state() private _loading = true;
  @state() private _error: string | null = null;

  static override styles = css`
    :host { display: block; }

    .kpi-summary {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
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

    .kpi-value.positive { color: var(--pages-success-9, #16a34a); }
    .kpi-value.negative { color: var(--pages-error-9, #dc2626); }
    .kpi-value.neutral { color: var(--pages-neutral-8, #404040); }

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

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--pages-font-size-sm, 13px);
    }

    th {
      text-align: left;
      padding: var(--pages-space-2, 8px) var(--pages-space-3, 12px);
      background: var(--pages-neutral-2, #f5f5f5);
      font-weight: 600;
      color: var(--pages-neutral-8, #404040);
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }

    td {
      padding: var(--pages-space-2, 8px) var(--pages-space-3, 12px);
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    tr:last-child td { border-bottom: none; }

    .insufficient { color: var(--pages-neutral-6, #737373); font-style: italic; }

    .skeleton {
      height: 200px;
      background: linear-gradient(90deg,
        var(--pages-neutral-2, #f5f5f5) 25%,
        var(--pages-neutral-3, #e5e5e5) 50%,
        var(--pages-neutral-2, #f5f5f5) 75%);
      background-size: 200% 100%;
      border-radius: 8px;
    }

    .error-card {
      background: var(--pages-error-2, #fef2f2);
      border: 1px solid var(--pages-error-5, #fca5a5);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      color: var(--pages-error-9, #dc2626);
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this._fetchMetrics();
  }

  private async _fetchMetrics() {
    this._loading = true;
    this._error = null;
    try {
      const res = await fetch('/api/metrics/sar-quality');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._metrics = await res.json();
    } catch (e) {
      this._error = e instanceof Error ? e.message : 'Failed to load SAR quality metrics';
    } finally {
      this._loading = false;
    }
  }

  override render() {
    if (this._loading) return html`<div class="skeleton"></div>`;
    if (this._error) return html`<div class="error-card">${this._error}</div>`;
    if (!this._metrics) return nothing;

    const m = this._metrics;
    const lift = this._computeLift(m.seeded, m.unseeded);

    return html`
      <div class="kpi-summary">
        <div class="kpi-card">
          <div class="kpi-label">Total Cases</div>
          <div class="kpi-value">${m.totalCases}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Seeded UPHELD Rate</div>
          <div class="kpi-value">${this._formatRate(m.seeded)}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Unseeded UPHELD Rate</div>
          <div class="kpi-value">${this._formatRate(m.unseeded)}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Lift (seeded vs unseeded)</div>
          <div class="kpi-value ${lift !== null ? (lift >= 0 ? 'positive' : 'negative') : 'neutral'}">
            ${lift !== null ? `${lift >= 0 ? '+' : ''}${lift.toFixed(1)}pp` : '—'}
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-title">Segmentation</div>
        <div class="table-container">
          <table>
            <thead>
              <tr><th>Segment</th><th>Total</th><th>Upheld</th><th>Not Upheld</th><th>UPHELD Rate</th></tr>
            </thead>
            <tbody>
              ${this._renderSegmentRow('Seeded', m.seeded)}
              ${this._renderSegmentRow('Unseeded', m.unseeded)}
            </tbody>
          </table>
        </div>
      </div>

      ${m.bySeedCount.length > 0 ? html`
        <div class="section">
          <div class="section-title">Seed Count Correlation</div>
          <div class="table-container">
            <table>
              <thead>
                <tr><th>Seeds</th><th>Total</th><th>UPHELD Rate</th></tr>
              </thead>
              <tbody>
                ${m.bySeedCount.map(b => html`
                  <tr>
                    <td>${b.range}</td>
                    <td>${b.total}</td>
                    <td>${b.total < 5
                      ? html`<span class="insufficient">Insufficient data</span>`
                      : `${(b.upheldRate * 100).toFixed(1)}%`}</td>
                  </tr>
                `)}
              </tbody>
            </table>
          </div>
        </div>
      ` : nothing}
    `;
  }

  private _renderSegmentRow(label: string, seg: OutcomeSegment) {
    return html`
      <tr>
        <td>${label}</td>
        <td>${seg.total}</td>
        <td>${seg.upheld}</td>
        <td>${seg.notUpheld}</td>
        <td>${seg.total < 5
          ? html`<span class="insufficient">Insufficient data</span>`
          : `${(seg.upheldRate * 100).toFixed(1)}%`}</td>
      </tr>
    `;
  }

  private _formatRate(seg: OutcomeSegment): string {
    if (seg.total < 5) return '—';
    return `${(seg.upheldRate * 100).toFixed(1)}%`;
  }

  private _computeLift(seeded: OutcomeSegment, unseeded: OutcomeSegment): number | null {
    if (seeded.total < 5 || unseeded.total < 5) return null;
    return (seeded.upheldRate - unseeded.upheldRate) * 100;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'aml-sar-quality-tab': AmlSarQualityTab;
  }
}
