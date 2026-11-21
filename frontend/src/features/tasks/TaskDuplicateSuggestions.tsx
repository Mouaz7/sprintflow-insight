import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Language } from '../../api/contracts';
import { projectScope } from '../../api/queries';

type Match = { taskId: number; key: string; title: string };

export default function TaskDuplicateSuggestions({
  projectId,
  title,
  language,
  linkToBoard,
}: {
  projectId: number;
  title: string;
  language: Language;
  linkToBoard: boolean;
}) {
  const query = title.trim();
  const [settled, setSettled] = useState('');
  useEffect(() => {
    const delay = setTimeout(() => setSettled(query), 300);
    return () => clearTimeout(delay);
  }, [query]);
  const matches = useQuery({
    queryKey: [...projectScope(projectId), 'task-duplicates', query],
    queryFn: ({ signal }) =>
      api<Match[]>(`/projects/${projectId}/task-duplicates`, {
        method: 'POST',
        body: JSON.stringify({ title: query }),
        signal,
      }),
    enabled: query === settled,
    gcTime: 0,
    retry: false,
  });
  const sv = language === 'SV';

  return (
    <div aria-live="polite">
      {matches.isError && (
        <small>
          {sv
            ? 'Dubbletter kunde inte kontrolleras.'
            : 'Could not check for duplicates.'}
        </small>
      )}
      {matches.isSuccess && matches.data.length > 0 && (
        <>
          <strong>{sv ? 'Möjliga dubbletter' : 'Possible duplicates'}</strong>
          <ul>
            {matches.data.map((match) => (
              <li key={match.taskId}>
                {linkToBoard ? (
                  <a
                    href={`/tasks?task=${encodeURIComponent(match.key)}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {match.key} · {match.title}
                  </a>
                ) : (
                  <span>
                    {match.key} · {match.title}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
