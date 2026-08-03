// Import all panels (custom elements must be registered before detail-pane creates them)
import './panels/aml-investigation-overview.js';
import './panels/aml-findings-panel.js';
import './panels/aml-routing-panel.js';
import './panels/aml-compliance-panel.js';
import './panels/aml-audit-trail.js';
import '@casehubio/blocks-ui-blocks-timeline';

// Import and define the root app element
import './aml-app.js';
