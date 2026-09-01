import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import type { ColumnId } from '@casehubio/pages-data/dist/dataset/types.js';
import '@casehubio/blocks-ui-list-pane';

const investigationColumns: TableColumnConfig[] = [
  { id: 'status' as ColumnId, label: 'Status', sortable: true, width: '90px' },
  { id: 'flagReason' as ColumnId, label: 'Flag', sortable: true },
  { id: 'riskScore' as ColumnId, label: 'Risk', sortable: true, width: '50px' },
  { id: 'createdAt' as ColumnId, label: 'Created', sortable: true, width: '90px' },
  { id: 'caseId' as ColumnId, visible: false },
];

@customElement('aml-investigation-nav')
export class AmlInvestigationNav extends LitElement {
  static override styles = css`
    :host { display: block; height: 100%; }
  `;

  override render() {
    return html`
      <blocks-list-pane
        selection-topic="case"
        endpoint="/api/investigations"
        .columnConfig=${investigationColumns}
        .getRowKey=${(row: any) => row.text('caseId')}>
      </blocks-list-pane>
    `;
  }
}
