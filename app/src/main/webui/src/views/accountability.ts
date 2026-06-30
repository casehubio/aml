import {
  rows, tabs, table, panel, html,
  inlineDataset, lookup,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";

export const auditTrailDataset = inlineDataset("audit-trail",
  "id,entryType,actorId,actorRole,occurredAt,causedByEntryId,digest\n" +
  "le-001,CASE_OPENED,system,SYSTEM,2026-06-15T09:00:00Z,,sha256:a1b2c3d4\n" +
  "le-002,WORKER_DECISION,entity-resolution-agent,AGENT,2026-06-15T09:01:00Z,le-001,sha256:e5f6a7b8\n" +
  "le-003,WORKER_DECISION,pattern-analysis-agent,AGENT,2026-06-15T09:02:00Z,le-002,sha256:c9d0e1f2\n" +
  "le-004,WORKER_DECISION,osint-screening-agent,AGENT,2026-06-15T09:02:30Z,le-002,sha256:a3b4c5d6\n" +
  "le-005,WORKER_DECISION,sar-drafting-agent,AGENT,2026-06-15T09:03:00Z,le-003,sha256:e7f8a9b0\n" +
  "le-006,GATE_APPROVED,officer-jones,HUMAN,2026-06-15T09:30:00Z,le-005,sha256:c1d2e3f4\n" +
  "le-007,CASE_COMPLETED,system,SYSTEM,2026-06-15T09:30:01Z,le-006,sha256:a5b6c7d8",
);

export const complianceDataset = inlineDataset("compliance",
  "requirementId,citation,mechanism,status\n" +
  "FINCEN-1,31 CFR 1020.320,Auditable evidence chain per agent task,CLOSED\n" +
  "FINCEN-2,31 CFR 1020.320(b),Human sign-off on SAR with 30-day SLA,CLOSED\n" +
  "GDPR-17,Art. 17 GDPR,LedgerErasureService + DecisionContextSanitiser,CLOSED\n" +
  "FINCEN-3,31 CFR 1010.520,Tamper-evident investigation record,CLOSED\n" +
  "FATF-R15,FATF Recommendation 15,Trust-weighted agent routing,PARTIAL\n" +
  "GDPR-22,Art. 22 GDPR,Automated decision record compliance,GAP",
);

export const erasureLogDataset = inlineDataset("erasure-log",
  "subjectId,subjectType,erasureReason,memoriesErased,receiptEntryId,erasedAt\n" +
  "actor-001,ACTOR,GDPR_ART_17_REQUEST,3,le-er-001,2026-06-20T14:00:00Z\n" +
  "entity-pep-001,ENTITY,GDPR_ART_17_REQUEST,5,le-er-002,2026-06-22T10:30:00Z",
);

function auditTrailTab(): Component {
  return table({
    title: "Ledger Entry Chain",
    lookup: lookup(auditTrailDataset.uuid),
    sortable: true,
    columns: [
      { id: "id" as never, name: "Entry ID" },
      { id: "entryType" as never, name: "Type" },
      { id: "actorId" as never, name: "Actor" },
      { id: "actorRole" as never, name: "Role" },
      { id: "occurredAt" as never, name: "Timestamp" },
      { id: "causedByEntryId" as never, name: "Caused By" },
      { id: "digest" as never, name: "Digest" },
    ],
  });
}

function merkleVerificationTab(): Component {
  return panel("Merkle Inclusion Proof",
    html(
      "<p>Select a ledger entry from the Audit Trail tab, then verify its inclusion proof.</p>" +
      "<p>The proof shows the leaf hash, sibling path to root, and tree root — independently verifiable.</p>" +
      "<p><em>Verification endpoint pending casehub-ledger#162.</em></p>"
    ),
  );
}

function complianceEvidenceTab(): Component {
  return table({
    title: "FinCEN/FATF Compliance Evidence",
    lookup: lookup(complianceDataset.uuid),
    sortable: true,
    columns: [
      { id: "requirementId" as never, name: "Requirement" },
      { id: "citation" as never, name: "Citation" },
      { id: "mechanism" as never, name: "Mechanism" },
      { id: "status" as never, name: "Status" },
    ],
    rowStyle: [
      { condition: "status == 'CLOSED'", style: { "background-color": "#dcfce7" } },
      { condition: "status == 'PARTIAL'", style: { "background-color": "#fef3c7" } },
      { condition: "status == 'BREACHED'", style: { "background-color": "#fee2e2" } },
      { condition: "status == 'GAP'", style: { "background-color": "#fecaca" } },
    ],
  });
}

function gdprErasureTab(): Component {
  return rows(
    table({
      title: "Completed Erasures",
      lookup: lookup(erasureLogDataset.uuid),
      sortable: true,
      columns: [
        { id: "subjectId" as never, name: "Subject" },
        { id: "subjectType" as never, name: "Type" },
        { id: "erasureReason" as never, name: "Reason" },
        { id: "memoriesErased" as never, name: "Memories Erased" },
        { id: "receiptEntryId" as never, name: "Receipt Entry" },
        { id: "erasedAt" as never, name: "Erased At" },
      ],
    }),
    panel("Erasure Actions",
      html(
        "<p><strong>Erase Actor:</strong> POST /api/actors/{actorId}/erasure</p>" +
        "<p><strong>Erase Entity:</strong> POST /api/entities/{entityId}/erasure</p>" +
        "<p><em>Action forms will be added when casehub-pages form submission is wired.</em></p>"
      ),
    ),
  );
}

export function accountabilityView(): Component {
  return tabs(
    ["Audit Trail",         auditTrailTab()],
    ["Merkle Verification", merkleVerificationTab()],
    ["Compliance Evidence",  complianceEvidenceTab()],
    ["GDPR Erasure",        gdprErasureTab()],
  );
}

export const accountabilityDatasets = [
  auditTrailDataset,
  complianceDataset,
  erasureLogDataset,
];
