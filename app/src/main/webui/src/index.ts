import { loadSite } from "@casehubio/pages-runtime";
import { page, sidebar, html } from "@casehubio/pages-ui";
import { workQueueView, workQueueDatasets } from "./views/work-queue.js";

const app = page("AML Investigations",
  sidebar(
    ["Work Queue",      workQueueView()],
    ["Investigations",  html("<p>Investigations — coming soon</p>")],
    ["Accountability",  html("<p>Accountability — coming soon</p>")],
    ["Operations",      html("<p>Operations — coming soon</p>")],
  ),
  {
    datasets: [...workQueueDatasets],
  },
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch(console.error);
}
