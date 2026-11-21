import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Language, Task } from '../../api/contracts';
import { projectScope } from '../../api/queries';
import { formatter } from '../../shared/i18n/lexicon';
import { Drawer } from '../../shared/ui/ui';

const features = {
  AGE_IN_COLUMN_VS_P85: ['Time in this column', 'Tid i den här kolumnen'],
  TIMES_BLOCKED: ['Times blocked', 'Antal blockeringar'],
  REASSIGNMENTS: ['Reassignments', 'Antal omtilldelningar'],
  COMMENTS_LAST_7_DAYS: [
    'Comments in the last seven days',
    'Kommentarer de senaste sju dagarna',
  ],
  STORY_POINTS: ['Story points', 'Berättelsepoäng'],
  DEPENDENCY_DEPTH: [
    'Unfinished dependency chain',
    'Kedja av oavslutade beroenden',
  ],
  REMAINING_SPRINT_DAYS: ['Sprint days remaining', 'Återstående sprintdagar'],
  COLUMN_WIP: ['Work in this column', 'Arbete i den här kolumnen'],
} as const;

type Risk = {
  exampleCount: number;
  trainedAt: string | null;
  tasks: {
    taskId: number;
    prediction: {
      probability: number;
      source: 'HEURISTIC' | 'TRAINED';
      topReasons: { feature: keyof typeof features; logOdds: number }[];
    };
  }[];
};

export default function TaskRiskDrawer({
  task,
  language,
  onClose,
}: {
  task: Task;
  language: Language;
  onClose: () => void;
}) {
  const sv = language === 'SV';
  const format = formatter(language);
  const risk = useQuery({
    queryKey: [...projectScope(task.projectId), 'task-risk'],
    queryFn: ({ signal }) =>
      api<Risk>(`/projects/${task.projectId}/task-risk`, { signal }),
    staleTime: 0,
  });
  const prediction = risk.isSuccess
    ? risk.data.tasks.find((entry) => entry.taskId === task.id)?.prediction
    : undefined;

  return (
    <Drawer
      title={`${sv ? 'Sprintrisk' : 'Sprint risk'} · ${task.title}`}
      onClose={onClose}
    >
      {risk.isPending && (
        <p role="status">
          {sv ? 'Hämtar sprintrisk…' : 'Loading sprint risk…'}
        </p>
      )}
      {risk.isError && (
        <p role="alert">
          {sv
            ? 'Sprintrisken kunde inte hämtas.'
            : 'Could not load sprint risk.'}{' '}
          <button type="button" onClick={() => void risk.refetch()}>
            {sv ? 'Försök igen' : 'Retry'}
          </button>
        </p>
      )}
      {risk.isSuccess && !prediction && (
        <p>
          {sv
            ? 'Ingen aktuell prognos för uppgiften.'
            : 'There is no current prediction for this task.'}
        </p>
      )}
      {prediction && (
        <>
          <p>
            <strong>
              {format.number(prediction.probability, {
                style: 'percent',
                maximumFractionDigits: 1,
              })}
            </strong>{' '}
            {sv
              ? 'risk att uppgiften inte blir klar under sprinten'
              : 'chance the task is unfinished at sprint end'}
          </p>
          <p>
            {prediction.source === 'TRAINED'
              ? sv
                ? 'Tränad på projektets sprintutfall.'
                : 'Trained on this project’s sprint outcomes.'
              : sv
                ? 'Uppskattning för projekt med begränsad historik.'
                : 'Estimate for a project with limited history.'}
          </p>
          {prediction.topReasons.length > 0 && (
            <>
              <h3>{sv ? 'Störst påverkan' : 'Largest influences'}</h3>
              <ul>
                {prediction.topReasons.map(({ feature, logOdds }) => (
                  <li key={feature}>
                    {features[feature][sv ? 1 : 0]} ·{' '}
                    {logOdds > 0
                      ? sv
                        ? 'höjer risken'
                        : 'raises risk'
                      : sv
                        ? 'sänker risken'
                        : 'lowers risk'}
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </Drawer>
  );
}
