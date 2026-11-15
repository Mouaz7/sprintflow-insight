import { VirtualTable } from '../../shared/ui/VirtualList';
import type { UseBacklogState } from './useBacklog';
export function StoryTable({ controller }: { controller: UseBacklogState }) {
  const { t, visibleStories, sprints, sortBy, saving, move, begin, remove } =
    controller;
  return (
    <VirtualTable
      rows={visibleStories}
      label={t('productBacklog')}
      className="backlog-table"
      columns={6}
      estimateSize={180}
      getKey={(story) => story.id}
      headCells={
        <>
          <th>{t('story')}</th>
          <th>{t('priority')}</th>
          <th>{t('points')}</th>
          <th>{t('value')}</th>
          <th>Sprint</th>
          <th>
            <span className="sr-only">{t('actions')}</span>
          </th>
        </>
      }
      renderRow={(story) => (
        <>
          <td data-label={t('story')}>
            <h2>{story.title}</h2>
            <p>{story.description || t('noDescription')}</p>
            <details>
              <summary>{t('acceptanceCriteria')}</summary>
              <p>{story.acceptanceCriteria}</p>
            </details>
          </td>
          <td data-label={t('priority')}>
            <span className={'status-badge priority-' + story.priorityId}>
              {t(
                story.priorityId === 1
                  ? 'high'
                  : story.priorityId === 2
                    ? 'medium'
                    : 'low',
              )}
            </span>
          </td>
          <td data-label={t('points')}>{story.storyPoints}</td>
          <td data-label={t('value')}>{story.businessValue ?? '—'}</td>
          <td data-label="Sprint">
            {sprints.find((sprint) => sprint.id === story.sprintId)?.name ??
              t('unplanned')}
          </td>
          <td>
            <div className="card-actions">
              {sortBy === 'manual' && (
                <>
                  <button
                    type="button"
                    disabled={saving || story === visibleStories[0]}
                    aria-label={t('moveUp', { title: story.title })}
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
                    aria-label={t('moveDown', { title: story.title })}
                    onClick={() => move(story, 1)}
                  >
                    ↓
                  </button>
                </>
              )}
              <button type="button" onClick={() => begin(story)}>
                {t('edit')}
              </button>
              <button
                type="button"
                className="danger-action"
                onClick={() => remove(story)}
              >
                {t('delete')}
              </button>
            </div>
          </td>
        </>
      )}
    />
  );
}
