import { describe, expect, it } from 'vitest';
import { activitySummary } from './activityText';

describe('Activity language', () => {
  it.each([
    ['PROJECT_CREATED', 'Created project “Mobilkontroll”.', 'Skapade projektet “Mobilkontroll”.'],
    ['PROJECT_CREATED', 'Created project “Alpha” from Scrum template.', 'Skapade projektet “Alpha” från mallen Scrum.'],
    ['PROJECT_CREATED', 'Created project “Alpha” from Blank template.', 'Skapade projektet “Alpha” från mallen Tomt projekt.'],
    ['PROJECT_UPDATED', 'Updated project “Alpha”.', 'Uppdaterade projektet “Alpha”.'],
    ['STORY_CREATED', 'Created story “Logga in”.', 'Skapade berättelsen “Logga in”.'],
    ['STORY_UPDATED', 'Updated story “Logga in”.', 'Uppdaterade berättelsen “Logga in”.'],
    ['SPRINT_CREATED', 'Created sprint “Sprint 1”.', 'Skapade sprinten “Sprint 1”.'],
    ['SPRINT_STATUS', 'Moved sprint “Sprint 1” to Completed.', 'Flyttade sprinten “Sprint 1” till Avslutad.'],
    ['TASK_CREATED', 'Created task “Build login”.', 'Skapade uppgiften “Build login”.'],
    ['TASK_STATUS', 'Moved task “Build login” to In Progress.', 'Flyttade uppgiften “Build login” till Pågår.'],
    ['TASK_STATUS', 'Moved task “Build login” to Done.', 'Flyttade uppgiften “Build login” till Klart.'],
    ['TASK_BLOCKED', 'Blocked task “Build login”.', 'Blockerade uppgiften “Build login”.'],
    ['TASK_BLOCKED', 'Blocked task “Build login”: Waiting for client', 'Blockerade uppgiften “Build login”: Waiting for client'],
    ['TASK_UNBLOCKED', 'Unblocked task “Build login”.', 'Avblockerade uppgiften “Build login”.'],
    ['COMMENT_ADDED', 'Commented on task “Build login”.', 'Kommenterade uppgiften “Build login”.'],
    ['BACKLOG_REORDERED', 'Updated backlog order.', 'Uppdaterade backloggens ordning.'],
    ['MEMBER_UPDATED', 'Updated project role for ada@example.test.', 'Ändrade projektrollen för ada@example.test.'],
    ['MEMBER_REMOVED', 'Removed project member ada@example.test.', 'Tog bort projektmedlemmen ada@example.test.'],
    ['OWNERSHIP_TRANSFERRED', 'Transferred project ownership to ada@example.test.', 'Överlät projektägarskapet till ada@example.test.'],
    ['TASK_TRASHED', 'Moved to trash: task #42.', 'Flyttade uppgift #42 till papperskorgen.'],
    ['STORY_RESTORED', 'Restored story #42.', 'Återställde berättelse #42.'],
    ['SPRINT_PURGED', 'Permanently deleted sprint #42.', 'Raderade sprint #42 permanent.'],
  ])('localizes %s while keeping the stored English intact', (eventType, summary, translated) => {
    const activity = Object.freeze({ eventType, summary });
    expect(activitySummary(activity, 'SV')).toBe(translated);
    expect(activitySummary(activity, 'EN')).toBe(summary);
    expect(activity.summary).toBe(summary);
  });

  it('keeps punctuation, quoted words and line breaks inside user-provided names', () => {
    expect(activitySummary({
      eventType: 'TASK_CREATED',
      summary: 'Created task “Review “Done”\nwithout rewriting the title”.',
    }, 'SV')).toBe('Skapade uppgiften “Review “Done”\nwithout rewriting the title”.');
  });

  it.each([
    { eventType: 'PROJECT_CREATED', summary: 'Reviewed the release with the customer.' },
    { eventType: 'UNKNOWN', summary: 'Created project “Alpha”.' },
    { eventType: 'constructor', summary: 'Created project “Alpha”.' },
    { summary: 'Created project “Alpha”.' },
    { eventType: 'TASK_STATUS', summary: 'Moved task “Alpha” to a future status.' },
    { eventType: 'SPRINT_RESTORED', summary: 'Restored task #42.' },
  ])('preserves custom, unsupported and mismatched summaries: $summary', (activity) => {
    expect(activitySummary(activity, 'SV')).toBe(activity.summary);
  });
});
