import { dockWorkbench, hostPanel } from '@casehubio/pages-ui/dist/dsl/builders.js';

export const workbench = dockWorkbench({
  storageKey: 'aml-workbench',
  centre: hostPanel('aml-centre'),
  left: [
    { key: 'investigations', label: 'Investigations', icon: 'search',
      defaultOpen: true, content: hostPanel('aml-investigation-nav') },
    { key: 'worker-tasks', label: 'My Tasks', icon: 'assignment',
      content: hostPanel('aml-worker-nav') },
    { key: 'work-queue', label: 'Work Queue', icon: 'inbox',
      content: hostPanel('aml-work-queue-nav') },
  ],
  right: [
    { key: 'findings', label: 'Findings', icon: 'biotech',
      content: hostPanel('aml-findings-dock') },
    { key: 'compliance', label: 'Compliance', icon: 'verified',
      content: hostPanel('aml-compliance-dock') },
    { key: 'audit', label: 'Audit', icon: 'history',
      defaultOpen: true, content: hostPanel('aml-audit-dock') },
    { key: 'routing', label: 'Routing', icon: 'route',
      content: hostPanel('aml-routing-dock') },
  ],
  bottom: [
    { key: 'operations', label: 'Operations', icon: 'monitoring',
      content: hostPanel('aml-operations-dock') },
    { key: 'scenarios', label: 'Scenarios', icon: 'play_circle',
      content: hostPanel('aml-scenario-dock') },
  ],
});
