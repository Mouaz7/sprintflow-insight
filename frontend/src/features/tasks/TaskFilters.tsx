import { SavedFilters } from './SavedFilters';
import type { UseTaskBoardState } from './useTaskBoard';
export function TaskFilters({ controller }: { controller: UseTaskBoardState }) {
  const {
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
  } = controller;
  if (projectId === undefined) return null;
  return (
    <>
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
        }}
        onApply={(value) => {
          setQuery(value.query);
          setStatusFilter(value.status);
          setSprintFilter(value.sprint);
          setAssigneeFilter(value.assignee);
          setDueFilter(value.due);
          setBlockedOnly(value.blocked);
          setLabelFilter(value.label);
        }}
      />
      <div className="task-toolbar task-filters">
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
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('searchPlaceholder')}
          aria-label={t('searchTasks')}
        />
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
      <div className="export-actions">
        <a
          className="secondary-action"
          href={`/api/projects/${projectId}/export.csv${exportQuery}`}
        >
          {t('exportCsv')}
        </a>
        <a
          className="secondary-action"
          href={`/api/projects/${projectId}/export.pdf${exportQuery}`}
        >
          {t('exportPdf')}
        </a>
      </div>
    </>
  );
}
