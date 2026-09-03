import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { InvestigationFlowResponse } from '../types.js';
import type { CaseRuntimeState, PlanItemSnapshot, TrustScoreSnapshot } from '@casehubio/graph-stencil-case';
import '@casehubio/blocks-ui-case-flow-viewer';

const STATUS_MAP: Record<string, string> = {
  scheduled: 'RUNNING',
  completed: 'COMPLETED',
  failed: 'FAULTED',
  declined: 'REJECTED',
};

function flowToRuntimeState(flow: InvestigationFlowResponse, caseStatus?: string): CaseRuntimeState {
  const planItems: PlanItemSnapshot[] = flow.nodes.map(n => ({
    id: `binding:${n.capabilityTag}`,
    bindingName: n.capabilityTag,
    status: (STATUS_MAP[n.status] ?? 'PENDING') as any,
    createdAt: n.timestamp,
  }));

  const trustScores: TrustScoreSnapshot[] = flow.nodes
    .filter(n => n.trustScoreAtRouting != null)
    .map(n => ({
      bindingName: n.capabilityTag,
      workerId: n.workerId,
      score: n.trustScoreAtRouting!,
    }));

  const parallelGroups: string[][] = flow.parallelGroups.map(group =>
    group.map(idx => flow.nodes[idx]!.capabilityTag)
  );

  return {
    planItems,
    milestones: [],
    timestamp: new Date().toISOString(),
    caseStatus,
    trustScores: trustScores.length > 0 ? trustScores : undefined,
    parallelGroups: parallelGroups.length > 0 ? parallelGroups : undefined,
  };
}

@customElement('aml-investigation-flow')
export class AmlInvestigationFlow extends LitElement {
  @property({ attribute: false }) item: any = null;

  get caseId(): string { return this.item?.text?.('caseId') ?? this.item?.caseId ?? ''; }

  @state() private _runtimeState: CaseRuntimeState | null = null;
  @state() private _loading = false;
  @state() private _error: string | null = null;

  static override styles = css`
    :host { display: block; height: 100%; }
    .loading, .error, .empty {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px); font-style: italic;
    }
    .error { color: var(--pages-error-9, #dc2626); }
  `;

  override updated(changedProps: Map<string, unknown>): void {
    if (changedProps.has('item') && this.caseId) {
      this._fetchFlow();
    }
  }

  private async _fetchFlow(): Promise<void> {
    this._loading = true;
    this._error = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/flow`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const flow: InvestigationFlowResponse = await response.json();
      this._runtimeState = flowToRuntimeState(flow);
    } catch (error) {
      this._error = error instanceof Error ? error.message : String(error);
    } finally {
      this._loading = false;
    }
  }

  override render() {
    if (!this.caseId) return html`<div class="empty">Select an investigation</div>`;
    if (this._loading) return html`<div class="loading">Loading flow...</div>`;
    if (this._error) return html`<div class="error">Failed to load flow: ${this._error}</div>`;
    if (!this._runtimeState) return html`<div class="empty">No flow data</div>`;
    return html`
      <blocks-case-flow-viewer
        src="/aml/aml-investigation.yaml"
        .runtimeState=${this._runtimeState}>
      </blocks-case-flow-viewer>
    `;
  }
}
