import {
  rows, columns, table, metric,
  lookup, groupBy, filterBy, col, count,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { workItemsDataset } from "../datasets.js";

const DS = workItemsDataset.uuid;

const totalOpen = metric({
  title: "Total Open",
  lookup: lookup(DS, groupBy(null, count("id"))),
  subtype: "card",
});

const approachingSla = metric({
  title: "Approaching SLA",
  lookup: lookup(DS,
    filterBy("slaStatus", "EQUALS_TO", "approaching"),
    groupBy(null, count("id")),
  ),
  subtype: "card",
});

const overdue = metric({
  title: "Overdue",
  lookup: lookup(DS,
    filterBy("slaStatus", "EQUALS_TO", "overdue"),
    groupBy(null, count("id")),
  ),
  subtype: "card",
});

const byGroup = metric({
  title: "By Group",
  lookup: lookup(DS, groupBy("candidateGroups", col("candidateGroups"), count("id"))),
  subtype: "card",
});

const workItemTable = table({
  title: "Open Work Items",
  lookup: lookup(DS),
  pageSize: 25,
  sortable: true,
  columns: [
    { id: "id" as never, name: "ID" },
    { id: "title" as never, name: "Title" },
    { id: "priority" as never, name: "Priority" },
    { id: "candidateGroups" as never, name: "Group" },
    { id: "claimDeadline" as never, name: "Deadline" },
    { id: "createdAt" as never, name: "Created" },
    { id: "status" as never, name: "Status" },
  ],
  rowStyle: [
    { condition: "slaStatus == 'overdue'", style: { "background-color": "#fee2e2" } },
    { condition: "slaStatus == 'approaching'", style: { "background-color": "#fef3c7" } },
    { condition: "slaStatus == 'healthy'", style: { "background-color": "#dcfce7" } },
  ],
  filter: { enabled: true },
});

export function workQueueView(): Component {
  return rows(
    columns([25, 25, 25, 25], [totalOpen], [approachingSla], [overdue], [byGroup]),
    workItemTable,
  );
}

export const workQueueDatasets = [workItemsDataset];
