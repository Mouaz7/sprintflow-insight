import { lazy, Suspense, useRef, useState } from 'react';
import { localizeMessage } from '../../shared/i18n/messages';
import { emptyTaskFilters } from './filterCriteria';
import type { UseTaskBoardState } from './useTaskBoard';

const SavedFilters = lazy(() =>
  import('./SavedFilters').then((m) => ({ default: m.SavedFilters })),
);
const CustomFieldFilter = lazy(() =>
  import('../../shared/ui/custom-fields/CustomFieldFilter').then((m) => ({
    default: m.CustomFieldFilter,
  })),
);
export function TaskFilters({ controller }: { controller: UseTaskBoardState }) {
  const {
    customCondition,
    setCustomCondition,
    customMatches,
    language,
    projectId,
    userEmail,
    query,
    statusFilter,
    sprintFilter,
    assigneeFilter,
    dueFilter,
    blockedOnly,
    labelFilter,
    setQuery,
    setStatusFilter,
    setSprintFilter,
    setAssigneeFilter,
    setDueFilter,
    setBlockedOnly,
    setLabelFilter,
    t,
    tasks,
    columns,
    labels,
    sprints,
    members,
    exportQuery,
    applyFilters,
    filtered,
    loading,
    loadError,
  } = controller;
  const search = useRef<HTMLInputElement>(null);
  const [initiallyOpen] = useState(
    () => window.matchMedia?.('(min-width: 768px)').matches ?? true,
  );
  const [optionsVisited, setOptionsVisited] = useState(initiallyOpen);
  if (projectId === undefined) return null;
  const exportsUnavailable =
    !!customCondition && (customMatches.isPending || customMatches.isError);
  return (
    <div className="board-controls surface">
      <div className="task-filters">
        <input
          ref={search}
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('searchPlaceholder')}
          aria-label={t('searchTasks')}
        />
        {exportQuery && (
          <button
            className="secondary-action"
            type="button"
            onClick={() => {
              applyFilters(emptyTaskFilters);
              search.current?.focus();
            }}
          >
            {t('clearFilters')}
          </button>
        )}
      </div>
      <p className="form-guide" role="status">
        {exportQuery &&
          !loading &&
          !loadError &&
          !(
            customCondition &&
            (customMatches.isFetching || customMatches.isError)
          ) &&
          t('filterResults', { shown: filtered.length, total: tasks.length })}
      </p>
      <details
        open={initiallyOpen}
        onToggle={(event) => {
          if (event.currentTarget.open) setOptionsVisited(true);
        }}
      >
        <summary className="text-button">{t('filtersAndExport')}</summary>
        {optionsVisited && (
          <Suspense fallback={<p role="status">{t('loadingTasks')}</p>}>
            <SavedFilters
              projectId={projectId}
              userEmail={userEmail}
              current={{
                query,
                status: statusFilter,
                sprint: sprintFilter,
                assignee: assigneeFilter,
                due: dueFilter,
                blocked: blockedOnly,
                label: labelFilter,
                custom: customCondition,
              }}
              onApply={applyFilters}
            />
          </Suspense>
        )}
        <div className="task-filters">
          <select
            aria-label={t('filterLabel')}
            value={labelFilter}
            onChange={(event) => setLabelFilter(event.target.value)}
          >
            <option value="">{t('allLabels')}</option>
            {[...new Set(tasks.flatMap((task) => task.labels ?? []))]
              .sort()
              .map((label) => (
                <option key={label} value={label}>
                  {label}
                </option>
              ))}
          </select>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            aria-label={t('filterStatus')}
          >
            <option value="">{t('allStatuses')}</option>
            {columns.map((column) => (
              <option key={column} value={column}>
                {labels[column]}
              </option>
            ))}
          </select>
          <select
            value={sprintFilter}
            onChange={(event) => setSprintFilter(event.target.value)}
            aria-label={t('filterSprint')}
          >
            <option value="">{t('allSprints')}</option>
            <option value="unassigned">{t('productBacklogFilter')}</option>
            {sprints.map((sprint) => (
              <option key={sprint.id} value={sprint.id}>
                {sprint.name}
              </option>
            ))}
          </select>
          <select
            value={assigneeFilter}
            onChange={(event) => setAssigneeFilter(event.target.value)}
            aria-label={t('filterAssignee')}
          >
            <option value="">{t('allAssignees')}</option>
            <option value="unassigned">{t('noAssignee')}</option>
            {members.map((member) => (
              <option key={member.id} value={member.email}>
                {member.email}
              </option>
            ))}
          </select>
          <select
            value={dueFilter}
            onChange={(event) => setDueFilter(event.target.value)}
            aria-label={t('filterDue')}
          >
            <option value="">{t('allDueDates')}</option>
            <option value="overdue">{t('overdue')}</option>
            <option value="has-date">{t('hasDueDate')}</option>
            <option value="no-date">{t('noDueDate')}</option>
          </select>
          <label className="blocked-filter">
            <input
              type="checkbox"
              checked={blockedOnly}
              onChange={(event) => setBlockedOnly(event.target.checked)}
            />
            {t('blocked')}
          </label>
        </div>
        {optionsVisited && (
          <Suspense fallback={<p role="status">{t('loadingTasks')}</p>}>
            <CustomFieldFilter
              projectId={projectId}
              value={customCondition}
              onChange={setCustomCondition}
            />
          </Suspense>
        )}
        <div className="export-actions">
          {(['csv', 'pdf'] as const).map((format) => (
            <a
              key={format}
              className="secondary-action"
              href={`/api/projects/${projectId}/export.${format}${exportQuery}`}
              aria-disabled={exportsUnavailable || undefined}
              tabIndex={exportsUnavailable ? -1 : undefined}
              onClick={(event) => {
                if (exportsUnavailable) event.preventDefault();
              }}
            >
              {t(format === 'csv' ? 'exportCsv' : 'exportPdf')}
            </a>
          ))}
        </div>
      </details>
      {customCondition && customMatches.isFetching && (
        <p role="status">{t('loadingTasks')}</p>
      )}
      {customCondition && customMatches.isError && (
        <p role="alert">
          {localizeMessage(customMatches.error.message, language)}{' '}
          <button
            onClick={() => {
              void customMatches.refetch();
            }}
          >
            {t('fieldsRetry')}
          </button>
        </p>
      )}
    </div>
  );
}
