import { createEventConnection } from '@casehubio/pages-data';
import type { EventConnection } from '@casehubio/pages-data';

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/push`;

export const pushConnection: EventConnection = createEventConnection(wsUrl);

pushConnection.listen([
  'investigation:status',
  'work-item:lifecycle',
  'worker-task:decision',
]);
