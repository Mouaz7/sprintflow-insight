import { localizeMessage } from '../../shared/i18n/messages';
import { StoryEditor } from './StoryEditor';
import { StoryTable } from './StoryTable';
import { useBacklog, type BacklogManagerProps } from './useBacklog';
export function BacklogManager(props: BacklogManagerProps) {
  const controller = useBacklog(props);
  const {
    items,
    sv,
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
        <span>
          {items.length}{' '}
          {sv
            ? items.length === 1
              ? 'berättelse'
              : 'berättelser'
            : items.length === 1
              ? 'story'
              : 'stories'}
        </span>
        <button className="primary-action" onClick={() => begin()}>
          + {sv ? 'Ny berättelse' : 'New story'}
        </button>
      </div>
      {!panelOpen && savedTitle && (
        <p className="form-message success" role="status">
          {sv
            ? `Berättelsen ”${savedTitle}” har sparats.`
            : `Story “${savedTitle}” saved.`}
        </p>
      )}
      {!panelOpen && error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, sv ? 'SV' : 'EN')}
        </p>
      )}
      {panelOpen && <StoryEditor controller={controller} />}
      <div className="backlog-toolbar">
        <input
          type="search"
          aria-label={sv ? 'Sök berättelser' : 'Search stories'}
          placeholder={sv ? 'Sök i backloggen…' : 'Search backlog…'}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          aria-label={
            sv ? 'Filtrera backlog efter sprint' : 'Filter backlog by sprint'
          }
          value={sprintFilter}
          onChange={(event) => setSprintFilter(event.target.value)}
        >
          <option value="all">{sv ? 'Alla sprintar' : 'All sprints'}</option>
          <option value="backlog">
            {sv ? 'Produktbacklog' : 'Product backlog'}
          </option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
        <select
          aria-label={sv ? 'Sortera backlog' : 'Sort backlog'}
          value={sortBy}
          onChange={(event) => setSortBy(event.target.value as typeof sortBy)}
        >
          <option value="manual">
            {sv ? 'Manuell ordning' : 'Manual order'}
          </option>
          <option value="priority">{sv ? 'Prioritet' : 'Priority'}</option>
          <option value="title">{sv ? 'Titel' : 'Title'}</option>
          <option value="points">Story points</option>
        </select>
      </div>
      {visibleStories.length ? (
        <StoryTable controller={controller} />
      ) : (
        <div className="empty-state">
          {items.length
            ? sv
              ? 'Inga berättelser matchar din sökning.'
              : 'No stories match your search.'
            : sv
              ? 'Backloggen är tom. Skapa den första berättelsen.'
              : 'The backlog is empty. Create the first story.'}
        </div>
      )}
    </div>
  );
}
