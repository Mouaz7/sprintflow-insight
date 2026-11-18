import { defineLexicon } from '../../shared/i18n/lexicon';

export const realtimeText = defineLexicon({
  EN: {
    teamMember: 'Team member',
    live: 'Live updates on',
    reconnecting: 'Live updates reconnecting',
    viewers: ({ names }: { names: string }) => `Viewing now: ${names}`,
    openTasks: ({ name, tasks }: { name: string; tasks: string }) =>
      `${name} (task ${tasks})`,
  },
  SV: {
    teamMember: 'Teammedlem',
    live: 'Liveuppdateringar på',
    reconnecting: 'Liveuppdateringar återansluter',
    viewers: ({ names }: { names: string }) => `Tittar nu: ${names}`,
    openTasks: ({ name, tasks }: { name: string; tasks: string }) =>
      `${name} (uppgift ${tasks})`,
  },
});
