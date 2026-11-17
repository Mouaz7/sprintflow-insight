import { localizeMessage } from '../../shared/i18n/messages';
import { StoryEditor } from './StoryEditor';
import { StoryTable } from './StoryTable';
import { useBacklog, type BacklogManagerProps } from './useBacklog';
export function BacklogManager(props: BacklogManagerProps) {
  const controller = useBacklog(props);
  const {
    items,
    t,
    language,
    begin,
    panelOpen,
    savedTitle,
    error,
    search,
    setSearch,
    sprintFilter,
    setSprintFilter,
    sprints,
    sortBy,
    setSortBy,
    visibleStories,
  } = controller;
  return (
    <div className="manager-layout">
      <div className="collection-actions">
        <span>{t('storyCount', { count: items.length })}</span>
        <button className="primary-action" onClick={() => begin()}>
          + {t('newStory')}
        </button>
      </div>
      {!panelOpen && savedTitle && (
        <p className="form-message success" role="status">
          {t('storySaved', { title: savedTitle })}
        </p>
      )}
      {!panelOpen && error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, language)}
        </p>
      )}
      {panelOpen && <StoryEditor controller={controller} />}
      <div className="backlog-toolbar">
        <input
          type="search"
          aria-label={t('searchStories')}
          placeholder={t('searchBacklog')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          aria-label={t('filterBySprint')}
          value={sprintFilter}
          onChange={(event) => setSprintFilter(event.target.value)}
        >
          <option value="all">{t('allSprints')}</option>
          <option value="backlog">{t('productBacklog')}</option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
        <select
          aria-label={t('sortBacklog')}
          value={sortBy}
          onChange={(event) => setSortBy(event.target.value as typeof sortBy)}
        >
          <option value="manual">{t('manualOrder')}</option>
          <option value="priority">{t('priority')}</option>
          <option value="title">{t('title')}</option>
          <option value="points">Story points</option>
        </select>
      </div>
      {visibleStories.length ? (
        <StoryTable controller={controller} />
      ) : (
        <div className="empty-state">
          {t(items.length ? 'noMatchingStories' : 'emptyBacklog')}
        </div>
      )}
    </div>
  );
}
