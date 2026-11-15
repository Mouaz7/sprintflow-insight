import type { Activity, Language } from '../../api/contracts';

const namedEvents: Record<string, readonly [string, string]> = {
  PROJECT_CREATED: ['Created project', 'Skapade projektet'],
  PROJECT_UPDATED: ['Updated project', 'Uppdaterade projektet'],
  STORY_CREATED: ['Created story', 'Skapade berättelsen'],
  STORY_UPDATED: ['Updated story', 'Uppdaterade berättelsen'],
  SPRINT_CREATED: ['Created sprint', 'Skapade sprinten'],
  TASK_CREATED: ['Created task', 'Skapade uppgiften'],
  TASK_BLOCKED: ['Blocked task', 'Blockerade uppgiften'],
  TASK_UNBLOCKED: ['Unblocked task', 'Avblockerade uppgiften'],
  COMMENT_ADDED: ['Commented on task', 'Kommenterade uppgiften'],
};

const statuses: Record<string, string> = {
  'To Do': 'Att göra',
  'In Progress': 'Pågår',
  Review: 'Granskning',
  Done: 'Klart',
  Planned: 'Planerad',
  Active: 'Aktiv',
  Completed: 'Avslutad',
  Cancelled: 'Avbruten',
};

const membership: Record<string, readonly [RegExp, string]> = {
  MEMBER_UPDATED: [
    /^Updated project role for ([\s\S]+)\.$/,
    'Ändrade projektrollen för',
  ],
  MEMBER_REMOVED: [
    /^Removed project member ([\s\S]+)\.$/,
    'Tog bort projektmedlemmen',
  ],
  OWNERSHIP_TRANSFERRED: [
    /^Transferred project ownership to ([\s\S]+)\.$/,
    'Överlät projektägarskapet till',
  ],
};

/** Translate known system formats at display time; keep names, reasons and stored text intact. */
export function activitySummary(
  activity: Pick<Activity, 'eventType' | 'summary'>,
  language: Language,
): string {
  const { summary, eventType } = activity;
  if (language !== 'SV' || !eventType) return summary;

  if (eventType === 'PROJECT_CREATED') {
    const template =
      /^Created project “([\s\S]+)” from (Blank|Scrum|Kanban) template\.$/.exec(
        summary,
      );
    if (template) {
      const name = template[2] === 'Blank' ? 'Tomt projekt' : template[2];
      return `Skapade projektet “${template[1]}” från mallen ${name}.`;
    }
  }

  if (eventType === 'TASK_BLOCKED') {
    const blocked = /^Blocked task “([\s\S]+)”: ([\s\S]+)$/.exec(summary);
    if (blocked) return `Blockerade uppgiften “${blocked[1]}”: ${blocked[2]}`;
  }

  const named = Object.hasOwn(namedEvents, eventType)
    ? namedEvents[eventType]
    : undefined;
  if (named && summary.startsWith(`${named[0]} “`) && summary.endsWith('”.')) {
    const name = summary.slice(named[0].length + 2, -2);
    return `${named[1]} “${name}”.`;
  }

  if (eventType === 'TASK_STATUS' || eventType === 'SPRINT_STATUS') {
    const moved =
      /^Moved (task|sprint) “([\s\S]+)” to (To Do|In Progress|Review|Done|Planned|Active|Completed|Cancelled)\.$/.exec(
        summary,
      );
    const kind = eventType === 'TASK_STATUS' ? 'task' : 'sprint';
    if (moved && moved[1] === kind && moved[3]) {
      return `Flyttade ${kind === 'task' ? 'uppgiften' : 'sprinten'} “${moved[2]}” till ${statuses[moved[3]]}.`;
    }
  }

  if (
    eventType === 'BACKLOG_REORDERED' &&
    summary === 'Updated backlog order.'
  ) {
    return 'Uppdaterade backloggens ordning.';
  }

  const member = Object.hasOwn(membership, eventType)
    ? membership[eventType]
    : undefined;
  const person = member?.[0].exec(summary);
  if (member && person) return `${member[1]} ${person[1]}.`;

  const lifecycle =
    /^(Restored |Moved to trash: |Permanently deleted )(task|story|sprint) #(\d+)\.$/.exec(
      summary,
    );
  if (lifecycle && lifecycle[2]) {
    const action =
      lifecycle[1] === 'Restored '
        ? 'RESTORED'
        : lifecycle[1] === 'Moved to trash: '
          ? 'TRASHED'
          : 'PURGED';
    if (eventType === `${lifecycle[2].toUpperCase()}_${action}`) {
      const kind =
        lifecycle[2] === 'task'
          ? 'uppgift'
          : lifecycle[2] === 'story'
            ? 'berättelse'
            : 'sprint';
      const item = `${kind} #${lifecycle[3]}`;
      return action === 'RESTORED'
        ? `Återställde ${item}.`
        : action === 'TRASHED'
          ? `Flyttade ${item} till papperskorgen.`
          : `Raderade ${item} permanent.`;
    }
  }

  // Custom edits and events introduced by a newer server remain readable in their original form.
  return summary;
}
