import {
  rows, columns, tabs, table, metric, barChart, pieChart,
  panel, html,
  lookup, groupBy, filterBy, col, count, sum,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import {
  throughputDataset,
  trustScoresDataset,
  gateMetricsDataset,
  workItemsDataset,
} from "../datasets.js";

// -- Throughput ---------------------------------------------------------------

const throughputDS = throughputDataset.uuid;

const throughputTable = table({
  title: "Investigation Throughput",
  lookup: lookup(throughputDS),
  columns: [
    { id: "metric" as never, name: "Metric" },
    { id: "value" as never, name: "Count" },
  ],
});

const throughputChart = barChart({
  title: "Investigations by Status",
  lookup: lookup(throughputDS,
    filterBy("metric", "NOT_EQUALS_TO", "Total Investigations"),
  ),
  subtype: "bar",
});

function throughputSection(): Component {
  return columns([50, 50], [throughputTable], [throughputChart]);
}

// -- Trust Scores -------------------------------------------------------------

const trustDS = trustScoresDataset.uuid;

const trustTable = table({
  title: "Agent Trust Scores",
  lookup: lookup(trustDS),
  sortable: true,
  columns: [
    { id: "agentId" as never, name: "Agent" },
    { id: "capabilityTag" as never, name: "Capability" },
    { id: "score" as never, name: "Score" },
  ],
});

const trustChart = barChart({
  title: "Trust Scores by Capability",
  lookup: lookup(trustDS, groupBy("capabilityTag", col("capabilityTag"), col("score"))),
  subtype: "bar",
});

function trustScoresSection(): Component {
  return columns([50, 50], [trustTable], [trustChart]);
}

// -- Gate Activity ------------------------------------------------------------

const gateDS = gateMetricsDataset.uuid;

const gateTable = table({
  title: "Gate Activity",
  lookup: lookup(gateDS),
  columns: [
    { id: "metric" as never, name: "Metric" },
    { id: "value" as never, name: "Count" },
  ],
});

const gateChart = barChart({
  title: "Gate Activity Breakdown",
  lookup: lookup(gateDS,
    filterBy("metric", "NOT_EQUALS_TO", "Avg Approval Time (s)"),
  ),
  subtype: "bar",
});

function gateActivitySection(): Component {
  return columns([50, 50], [gateTable], [gateChart]);
}

// -- SLA Health ---------------------------------------------------------------

const workDS = workItemsDataset.uuid;

const slaDonut = pieChart({
  title: "SLA Status",
  lookup: lookup(workDS, groupBy("slaStatus", col("slaStatus"), count("id"))),
  subtype: "donut",
});

function slaHealthSection(): Component {
  return slaDonut;
}

// -- Intervention -------------------------------------------------------------

function interventionSection(): Component {
  return panel("Operational Controls",
    html(
      "<p><strong>Suspend Investigation:</strong> POST /api/investigations/{caseId}/suspend</p>" +
      "<p><strong>Resume Investigation:</strong> POST /api/investigations/{caseId}/resume</p>" +
      "<p><strong>Escalate Work Item:</strong> POST /api/work-items/{id}/escalate</p>" +
      "<p><strong>Override Gate:</strong> POST /api/work-items/{id}/complete with override payload</p>" +
      "<p><em>Action forms with confirmation will be added when casehub-pages form submission is wired.</em></p>"
    ),
  );
}

// -- Simulation ---------------------------------------------------------------

function simulationSection(): Component {
  return panel("Live Simulation",
    html(
      "<p>Run an AML investigation scenario to demonstrate the full investigation flow.</p>" +
      "<p><strong>Seed all scenarios:</strong> POST /api/simulation/seed</p>" +
      "<p><strong>Run scenario:</strong> POST /api/simulation/investigate with scenario name</p>" +
      "<p><em>Scenario selector and run button will be added when casehub-pages form submission is wired.</em></p>"
    ),
  );
}

// -- Composed view ------------------------------------------------------------

export function operationsView(): Component {
  return tabs(
    ["Throughput",    throughputSection()],
    ["Trust Scores",  trustScoresSection()],
    ["Gate Activity", gateActivitySection()],
    ["SLA Health",    slaHealthSection()],
    ["Intervention",  interventionSection()],
    ["Simulation",    simulationSection()],
  );
}

export const operationsDatasets = [
  throughputDataset,
  trustScoresDataset,
  gateMetricsDataset,
];
