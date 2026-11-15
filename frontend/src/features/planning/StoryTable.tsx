import type { UseBacklogState } from './useBacklog';
export function StoryTable({ controller }: { controller: UseBacklogState }) {
  const { sv, visibleStories, sprints, sortBy, saving, move, begin, remove } =
    controller;
  return (
    <table className="backlog-table">
      <caption className="sr-only">
        {sv ? 'Produktbacklog' : 'Product backlog'}
      </caption>
      <thead>
        <tr>
          <th>{sv ? 'Berättelse' : 'Story'}</th>
          <th>{sv ? 'Prioritet' : 'Priority'}</th>
          <th>{sv ? 'Poäng' : 'Points'}</th>
          <th>{sv ? 'Affärsvärde' : 'Value'}</th>
          <th>Sprint</th>
          <th>
            <span className="sr-only">{sv ? 'Åtgärder' : 'Actions'}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {visibleStories.map((story) => (
          <tr key={story.id}>
            <td data-label={sv ? 'Berättelse' : 'Story'}>
              <h2>{story.title}</h2>
              <p>
                {story.description ||
                  (sv ? 'Ingen beskrivning.' : 'No description.')}
              </p>
              <details>
                <summary>
                  {sv ? 'Acceptanskriterier' : 'Acceptance criteria'}
                </summary>
                <p>{story.acceptanceCriteria}</p>
              </details>
            </td>
            <td data-label={sv ? 'Prioritet' : 'Priority'}>
              <span className={'status-badge priority-' + story.priorityId}>
                {story.priorityId === 1
                  ? sv
                    ? 'Hög'
                    : 'High'
                  : story.priorityId === 2
                    ? sv
                      ? 'Medel'
                      : 'Medium'
                    : sv
                      ? 'Låg'
                      : 'Low'}
              </span>
            </td>
            <td data-label={sv ? 'Poäng' : 'Points'}>{story.storyPoints}</td>
            <td data-label={sv ? 'Affärsvärde' : 'Value'}>
              {story.businessValue ?? '—'}
            </td>
            <td data-label="Sprint">
              {sprints.find((sprint) => sprint.id === story.sprintId)?.name ??
                (sv ? 'Ej planerad' : 'Unplanned')}
            </td>
            <td>
              <div className="card-actions">
                {sortBy === 'manual' && (
                  <>
                    <button
                      type="button"
                      disabled={saving || story === visibleStories[0]}
                      aria-label={`${sv ? 'Flytta upp' : 'Move up'}: ${story.title}`}
                      onClick={() => move(story, -1)}
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      disabled={
                        saving ||
                        story === visibleStories[visibleStories.length - 1]
                      }
                      aria-label={`${sv ? 'Flytta ned' : 'Move down'}: ${story.title}`}
                      onClick={() => move(story, 1)}
                    >
                      ↓
                    </button>
                  </>
                )}
                <button type="button" onClick={() => begin(story)}>
                  {sv ? 'Redigera' : 'Edit'}
                </button>
                <button
                  type="button"
                  className="danger-action"
                  onClick={() => remove(story)}
                >
                  {sv ? 'Ta bort' : 'Delete'}
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
