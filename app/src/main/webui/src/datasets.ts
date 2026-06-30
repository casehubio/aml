import { dataset, inlineDataset } from "@casehubio/pages-ui";

// Work queue — inline mock data until casehub-work#241 ships the query API.
export const workItemsDataset = inlineDataset("work-items",
  "id,title,priority,candidateGroups,claimDeadline,createdAt,status,callerRef,slaStatus\n" +
  "wi-001,Review SAR filing — TXN-2026-0412,HIGH,aml-compliance,2026-07-15T00:00:00Z,2026-06-15T09:00:00Z,OPEN,aml:investigation:case-001,healthy\n" +
  "wi-002,Approve account restriction — TXN-2026-0455,CRITICAL,aml-mlro,2026-07-03T00:00:00Z,2026-06-20T14:30:00Z,OPEN,case:case-002/gate:gate-001,approaching\n" +
  "wi-003,Review SAR filing — TXN-2026-0501,HIGH,aml-compliance,2026-06-28T00:00:00Z,2026-06-01T11:00:00Z,OPEN,aml:investigation:case-003,overdue\n" +
  "wi-004,Senior compliance escalation — TXN-2026-0388,CRITICAL,aml-senior-compliance,2026-07-20T00:00:00Z,2026-06-22T08:15:00Z,OPEN,aml:investigation:case-004,healthy\n" +
  "wi-005,Approve SAR filing — TXN-2026-0523,HIGH,aml-mlro,2026-07-05T00:00:00Z,2026-06-25T16:45:00Z,OPEN,case:case-005/gate:gate-002,approaching\n" +
  "wi-006,Review entity restriction — TXN-2026-0467,MEDIUM,compliance-officers,2026-07-25T00:00:00Z,2026-06-18T10:00:00Z,OPEN,case:case-006/gate:gate-003,healthy\n" +
  "wi-007,Review SAR filing — TXN-2026-0544,HIGH,aml-compliance,2026-06-29T00:00:00Z,2026-06-05T13:20:00Z,OPEN,aml:investigation:case-007,overdue\n" +
  "wi-008,PEP clearance review — TXN-2026-0499,CRITICAL,aml-senior-compliance,2026-07-08T00:00:00Z,2026-06-28T07:30:00Z,OPEN,aml:investigation:case-008,approaching",
);

// Investigation list — fetches from real API. dataPath extracts the items array.
export const investigationsDataset = dataset("investigations", "/api/investigations", {
  dataPath: "items",
});

// Throughput metrics — API returns a nested object, not tabular data.
// Inline mock until a tabular metrics endpoint is added.
export const throughputDataset = inlineDataset("throughput",
  "metric,value\n" +
  "Total Investigations,142\n" +
  "In Progress,12\n" +
  "Completed,118\n" +
  "Failed,7\n" +
  "Cancelled,5",
);

// Trust scores — API returns an array via dataPath, suitable for pages-data.
export const trustScoresDataset = dataset("trust-scores", "/api/metrics/trust-scores", {
  dataPath: "scores",
});

// Gate metrics — API returns a nested object, not tabular.
// Inline mock until a tabular endpoint is added.
export const gateMetricsDataset = inlineDataset("gate-metrics",
  "metric,value\n" +
  "Total Gates,24\n" +
  "Pending,2\n" +
  "Approved,19\n" +
  "Rejected,3\n" +
  "Avg Approval Time (s),3420",
);
