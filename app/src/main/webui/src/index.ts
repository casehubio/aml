// Import custom elements — must be registered before loadSite renders host panels
import './panels/centre.js';
import './panels/investigation-nav.js';
import './panels/stub-panel.js';

// Import existing detail tab panels (used by blocks-detail-pane in centre)
import './panels/aml-investigation-overview.js';
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

// Panels implemented in later batches — use stubs for now
registerPanel('aml-worker-nav', 'aml-stub-panel');
registerPanel('aml-work-queue-nav', 'aml-stub-panel');
registerPanel('aml-findings-dock', 'aml-stub-panel');
registerPanel('aml-compliance-dock', 'aml-stub-panel');
registerPanel('aml-audit-dock', 'aml-stub-panel');
registerPanel('aml-routing-dock', 'aml-stub-panel');
registerPanel('aml-operations-dock', 'aml-stub-panel');
registerPanel('aml-scenario-dock', 'aml-stub-panel');

(async () => {
  const container = document.getElementById('app')!;
  const site = await loadSite(container, workbench);

  // GE-20260814-0d4123 — first tab "No data" workaround
  site.navigate('Investigations');
})();
