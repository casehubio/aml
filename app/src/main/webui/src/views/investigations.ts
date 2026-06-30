import {
  rows, table, accordion, panel, html, iframePlugin,
  inlineDataset, lookup, filterBy, groupBy, col, count,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { investigationsDataset } from "../datasets.js";

const DS = investigationsDataset.uuid;

const caseListTable = table({
  title: "Investigations",
  lookup: lookup(DS),
  pageSize: 25,
  sortable: true,
  columns: [
    { id: "caseId" as never, name: "Case ID" },
    { id: "status" as never, name: "Status" },
    { id: "outcomeType" as never, name: "Outcome" },
    { id: "transactionId" as never, name: "Transaction" },
    { id: "originAccount" as never, name: "Origin" },
    { id: "destinationAccount" as never, name: "Destination" },
    { id: "amount" as never, name: "Amount" },
    { id: "currency" as never, name: "Currency" },
    { id: "flagReason" as never, name: "Flag Reason" },
    { id: "createdAt" as never, name: "Created" },
  ],
  rowStyle: [
    { condition: "status == 'COMPLETED'", style: { "background-color": "#dcfce7" } },
    { condition: "status == 'IN_PROGRESS'", style: { "background-color": "#dbeafe" } },
    { condition: "status == 'FAILED'", style: { "background-color": "#fee2e2" } },
    { condition: "status == 'SUSPENDED'", style: { "background-color": "#fef3c7" } },
  ],
  filter: { enabled: true },
});

// Detail datasets use inline mock data. When parameterised dataset URLs
// are supported by casehub-pages, these will be replaced with dynamic
// dataset() calls using the selected caseId.
const priorContextDataset = inlineDataset("prior-context",
  "domain,text,createdAt,confidence\n" +
  "ENTITY_RISK,Prior SAR filed — entity linked to PEP network,2026-05-15T10:00:00Z,0.9\n" +
  "NETWORK,Connected to 3 flagged counterparties in jurisdiction X,2026-04-20T14:30:00Z,0.85\n" +
  "PATTERN,Structuring pattern detected across 5 transactions,2026-03-10T09:15:00Z,0.78",
);

const flowDataset = inlineDataset("investigation-flow",
  "nodes,edges,parallelGroups\n" +
  "\"[{\"\"capabilityTag\"\":\"\"entity-resolution\"\",\"\"workerId\"\":\"\"entity-resolution-agent\"\",\"\"trustScoreAtRouting\"\":0.82,\"\"status\"\":\"\"completed\"\",\"\"timestamp\"\":\"\"2026-06-15T09:01:00Z\"\"},{\"\"capabilityTag\"\":\"\"pattern-analysis\"\",\"\"workerId\"\":\"\"pattern-analysis-agent\"\",\"\"trustScoreAtRouting\"\":0.75,\"\"status\"\":\"\"completed\"\",\"\"timestamp\"\":\"\"2026-06-15T09:02:00Z\"\"},{\"\"capabilityTag\"\":\"\"osint-screening\"\",\"\"workerId\"\":\"\"osint-screening-agent\"\",\"\"trustScoreAtRouting\"\":0.88,\"\"status\"\":\"\"declined\"\",\"\"timestamp\"\":\"\"2026-06-15T09:02:30Z\"\"},{\"\"capabilityTag\"\":\"\"sar-drafting\"\",\"\"workerId\"\":\"\"sar-drafting-agent-senior\"\",\"\"trustScoreAtRouting\"\":0.91,\"\"status\"\":\"\"completed\"\",\"\"timestamp\"\":\"\"2026-06-15T09:03:00Z\"\"},{\"\"capabilityTag\"\":\"\"compliance-review\"\",\"\"workerId\"\":\"\"officer-jones\"\",\"\"trustScoreAtRouting\"\":null,\"\"status\"\":\"\"completed\"\",\"\"timestamp\"\":\"\"2026-06-15T09:30:00Z\"\"}]\",\"[{\"\"from\"\":0,\"\"to\"\":1},{\"\"from\"\":0,\"\"to\"\":2},{\"\"from\"\":1,\"\"to\"\":3},{\"\"from\"\":2,\"\"to\"\":3},{\"\"from\"\":3,\"\"to\"\":4}]\",\"[[1,2]]\"",
);

const gatesDataset = inlineDataset("gates",
  "actionType,gatePolicy,status,candidateGroups,approvedBy,approvedAt\n" +
  "sar.filing,ALWAYS,COMPLETED,aml-mlro,officer-jones,2026-06-15T09:30:00Z\n" +
  "account.restriction,RISK_SCORE_THRESHOLD,PENDING,aml-compliance,,",
);

function caseDetailView(): Component {
  return accordion(
    ["Transaction",
      html("<p>Select an investigation from the list to view transaction details.</p>"),
    ],
    ["Prior Context",
      table({
        title: "Prior Entity Context",
        lookup: lookup(priorContextDataset.uuid),
        columns: [
          { id: "domain" as never, name: "Domain" },
          { id: "text" as never, name: "Detail" },
          { id: "createdAt" as never, name: "Date" },
          { id: "confidence" as never, name: "Confidence" },
        ],
      }),
    ],
    ["Investigation Flow",
      iframePlugin({
        componentId: "investigation-flow",
        lookup: lookup(flowDataset.uuid),
        height: "400px",
      }),
    ],
    ["Specialist Findings",
      panel("Entity Resolution",
        html("<p>Entity resolution findings will appear here when a case is selected.</p>"),
      ),
      panel("Pattern Analysis",
        html("<p>Pattern analysis findings will appear here.</p>"),
      ),
      panel("OSINT Screening",
        html("<p>OSINT screening results will appear here.</p>"),
      ),
      panel("SAR Narrative",
        html("<p>SAR narrative will appear here.</p>"),
      ),
    ],
    ["Oversight Gates",
      table({
        title: "Gate Decisions",
        lookup: lookup(gatesDataset.uuid),
        columns: [
          { id: "actionType" as never, name: "Action" },
          { id: "gatePolicy" as never, name: "Policy" },
          { id: "status" as never, name: "Status" },
          { id: "candidateGroups" as never, name: "Group" },
          { id: "approvedBy" as never, name: "Approved By" },
          { id: "approvedAt" as never, name: "Approved At" },
        ],
        rowStyle: [
          { condition: "status == 'COMPLETED'", style: { "background-color": "#dcfce7" } },
          { condition: "status == 'PENDING'", style: { "background-color": "#fef3c7" } },
          { condition: "status == 'REJECTED'", style: { "background-color": "#fee2e2" } },
        ],
      }),
    ],
    ["Compliance Review",
      html("<p>Compliance review WorkItem status will appear here when a case is selected.</p>"),
    ],
    ["Failure Context",
      html("<p>Failure context is shown only for failed, cancelled, or suspended investigations.</p>"),
    ],
  );
}

export function investigationsView(): Component {
  return rows(
    caseListTable,
    caseDetailView(),
  );
}

export const investigationDatasets = [
  investigationsDataset,
  priorContextDataset,
  flowDataset,
  gatesDataset,
];
