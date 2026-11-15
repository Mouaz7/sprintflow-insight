import { useState } from 'react';
import { api } from '../../api/client';
import type { Language, WorkspaceView } from '../../api/contracts';
import { Drawer, Notice } from '../../shared/ui/ui';
export function Onboarding({
  language,
  onClose,
  onNavigate,
}: {
  language: Language;
  onClose: () => void;
  onNavigate: (view: WorkspaceView) => void;
}) {
  const sv = language === 'SV';
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function finish(view?: WorkspaceView) {
    if (busy) return;
    setBusy(true);
    try {
      await api('/auth/preferences', {
        method: 'PUT',
        body: JSON.stringify({ onboardingCompleted: true }),
      });
      onClose();
      if (view) onNavigate(view);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={sv ? 'Kom igång med SprintFlow' : 'Get started with SprintFlow'}
      language={language}
      busy={busy}
      onClose={() => {
        void finish();
      }}
    >
      <p>
        {sv
          ? 'Tre enkla steg hjälper ditt team att komma igång.'
          : 'Three simple steps help your team get started.'}
      </p>
      <ol className="onboarding-steps">
        <li>
          <h2>{sv ? 'Skapa ert projekt' : 'Create your project'}</h2>
          <p>
            {sv
              ? 'Välj en tom arbetsyta eller börja med Scrum- eller Kanbanmallen.'
              : 'Choose a blank workspace or start with a Scrum or Kanban template.'}
          </p>
          <button disabled={busy} onClick={() => finish('projects')}>
            {sv ? 'Öppna projekt' : 'Open projects'}
          </button>
        </li>
        <li>
          <h2>{sv ? 'Planera nästa leverans' : 'Plan your next delivery'}</h2>
          <p>
            {sv
              ? 'Lägg till berättelser och välj ett tydligt sprintmål.'
              : 'Add stories and choose a clear sprint goal.'}
          </p>
          <button disabled={busy} onClick={() => finish('sprints')}>
            {sv ? 'Öppna sprintar' : 'Open sprints'}
          </button>
        </li>
        <li>
          <h2>{sv ? 'Arbeta tillsammans' : 'Work together'}</h2>
          <p>
            {sv
              ? 'Tilldela uppgifter, skriv kommentarer och följ checklistan.'
              : 'Assign tasks, add comments and follow the checklist.'}
          </p>
          <button disabled={busy} onClick={() => finish('tasks')}>
            {sv ? 'Öppna uppgifter' : 'Open tasks'}
          </button>
        </li>
      </ol>
      <Notice language={language} error={error} />
      <button
        className="primary-action"
        disabled={busy}
        onClick={() => finish()}
      >
        {sv ? 'Jag är redo' : 'I’m ready'}
      </button>
    </Drawer>
  );
}
