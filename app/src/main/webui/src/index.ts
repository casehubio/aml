import { loadSite } from "@casehubio/pages-runtime";
import { page, sidebar, html } from "@casehubio/pages-ui";
import { workQueueView, workQueueDatasets } from "./views/work-queue.js";
import { investigationsView, investigationDatasets } from "./views/investigations.js";
import { accountabilityView, accountabilityDatasets } from "./views/accountability.js";

const app = page("AML Investigations",
  sidebar(
    ["Work Queue",      workQueueView()],
    ["Investigations",  investigationsView()],
    ["Accountability",  accountabilityView()],
    ["Operations",      html("<p>Operations — coming soon</p>")],
  ),
  {
    datasets: [...workQueueDatasets, ...investigationDatasets, ...accountabilityDatasets],
  },
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch(console.error);
}
