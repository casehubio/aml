import { loadSite } from "@casehubio/pages-runtime";
import { page, sidebar, html } from "@casehubio/pages-ui";

const app = page("AML Investigations",
  sidebar(
    ["Work Queue",      html("<p>Work Queue — coming soon</p>")],
    ["Investigations",  html("<p>Investigations — coming soon</p>")],
    ["Accountability",  html("<p>Accountability — coming soon</p>")],
    ["Operations",      html("<p>Operations — coming soon</p>")],
  ),
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch(console.error);
}
