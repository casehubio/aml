import {
  rows, tabs, table, metric, accordion, panel, html, iframePlugin,
  dataset, lookup, groupBy, filterBy, col, count,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { investigationsDataset } from "../datasets.js";

const DS = investigationsDataset.uuid;

const statusCounts = rows(
  metric({
    title: "In Progress",
    lookup: lookup(DS,
      filterBy("status", "EQUALS_TO", "IN_PROGRESS"),
      groupBy(null, count("caseId")),
    ),
    subtype: "card",
  }),
);

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

// Case detail datasets — created per-case when drill-down is wired.
// For now, define them as static datasets with placeholder URLs.
// When casehub-pages supports parameterised datasets, these become dynamic.
export const priorContextDataset = dataset("prior-context", "/api/investigations/_/prior-context", {
  dataPath: "facts",
});

export const findingsDataset = dataset("findings", "/api/investigations/_/findings");

export const gatesDataset = dataset("gates", "/api/investigations/_/gates", {
  dataPath: "gates",
});

export const flowDataset = dataset("investigation-flow", "/api/investigations/_/flow");

export const complianceEvidenceDataset = dataset("compliance-evidence", "/api/investigations/_/compliance-evidence");

// Case detail view — accordion with 7 sections
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
  findingsDataset,
  gatesDataset,
  complianceEvidenceDataset,
];
