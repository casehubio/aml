// Import custom elements — must be registered before loadSite renders host panels
import './panels/centre.js';
import './panels/investigation-nav.js';
import './panels/audit-dock.js';
import './panels/compliance-dock.js';
import './panels/routing-dock.js';
import './panels/findings-dock.js';
import './panels/operations-dock.js';
import './panels/work-queue-nav.js';
import './panels/worker-nav.js';
import './panels/scenario-dock.js';

// Import existing detail tab panels (used by blocks-detail-pane in centre)
import './panels/aml-investigation-overview.js';
import './detail/investigation-flow.js';
import './panels/aml-findings-panel.js';
import './panels/aml-routing-panel.js';
import './panels/aml-compliance-panel.js';
import './panels/aml-audit-trail.js';
import '@casehubio/blocks-ui-blocks-timeline';

import { registerPanel } from '@casehubio/pages-runtime';
import { loadSite } from '@casehubio/pages-runtime';
import { workbench } from './layout.js';

// Register all panels before loadSite — GE-20260805-e3211c
registerPanel('aml-centre', 'aml-centre');
registerPanel('aml-investigation-nav', 'aml-investigation-nav');
registerPanel('aml-audit-dock', 'aml-audit-dock');
registerPanel('aml-compliance-dock', 'aml-compliance-dock');
registerPanel('aml-routing-dock', 'aml-routing-dock');
registerPanel('aml-findings-dock', 'aml-findings-dock');

registerPanel('aml-worker-nav', 'aml-worker-nav');
registerPanel('aml-work-queue-nav', 'aml-work-queue-nav');
registerPanel('aml-operations-dock', 'aml-operations-dock');
registerPanel('aml-scenario-dock', 'aml-scenario-dock');

(async () => {
  const container = document.getElementById('app')!;
  const site = await loadSite(container, workbench);

  // GE-20260814-0d4123 — first tab "No data" workaround
  site.navigate('Investigations');
})();
