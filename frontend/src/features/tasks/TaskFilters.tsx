import { SavedFilters } from './SavedFilters';
import type { UseTaskBoardState } from './useTaskBoard';
export function TaskFilters({ controller }: { controller: UseTaskBoardState }) {
  const {
    projectId,
    userEmail,
    language,
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
    sv,
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
        language={language}
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
          aria-label={sv ? 'Filtrera etikett' : 'Filter label'}
          value={labelFilter}
          onChange={(event) => setLabelFilter(event.target.value)}
        >
          <option value="">{sv ? 'Alla etiketter' : 'All labels'}</option>
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
          placeholder={sv ? 'Sök uppgifter…' : 'Search tasks…'}
          aria-label={sv ? 'Sök uppgifter' : 'Search tasks'}
        />
        <select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
          aria-label={sv ? 'Filtrera status' : 'Filter status'}
        >
          <option value="">{sv ? 'Alla statusar' : 'All statuses'}</option>
          {columns.map((column) => (
            <option key={column} value={column}>
              {labels[column]}
            </option>
          ))}
        </select>
        <select
          value={sprintFilter}
          onChange={(event) => setSprintFilter(event.target.value)}
          aria-label={sv ? 'Filtrera sprint' : 'Filter sprint'}
        >
          <option value="">{sv ? 'Alla sprintar' : 'All sprints'}</option>
          <option value="unassigned">
            {sv ? 'Produktbacklogg' : 'Product backlog'}
          </option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
        <select
          value={assigneeFilter}
          onChange={(event) => setAssigneeFilter(event.target.value)}
          aria-label={sv ? 'Filtrera ansvarig' : 'Filter assignee'}
        >
          <option value="">{sv ? 'Alla ansvariga' : 'All assignees'}</option>
          <option value="unassigned">
            {sv ? 'Ingen ansvarig' : 'Unassigned'}
          </option>
          {members.map((member) => (
            <option key={member.id} value={member.email}>
              {member.email}
            </option>
          ))}
        </select>
        <select
          value={dueFilter}
          onChange={(event) => setDueFilter(event.target.value)}
          aria-label={sv ? 'Filtrera slutdatum' : 'Filter due date'}
        >
          <option value="">{sv ? 'Alla slutdatum' : 'All due dates'}</option>
          <option value="overdue">{sv ? 'Försenade' : 'Overdue'}</option>
          <option value="has-date">
            {sv ? 'Har slutdatum' : 'Has due date'}
          </option>
          <option value="no-date">
            {sv ? 'Inget slutdatum' : 'No due date'}
          </option>
        </select>
        <label className="blocked-filter">
          <input
            type="checkbox"
            checked={blockedOnly}
            onChange={(event) => setBlockedOnly(event.target.checked)}
          />
          {sv ? 'Blockerade' : 'Blocked'}
        </label>
      </div>
      <div className="export-actions">
        <a
          className="secondary-action"
          href={`/api/projects/${projectId}/export.csv${exportQuery}`}
        >
          {sv
            ? 'Exportera filtrerade uppgifter (CSV)'
            : 'Export filtered tasks (CSV)'}
        </a>
        <a
          className="secondary-action"
          href={`/api/projects/${projectId}/export.pdf${exportQuery}`}
        >
          {sv
            ? 'Exportera filtrerade uppgifter (PDF)'
            : 'Export filtered tasks (PDF)'}
        </a>
      </div>
    </>
  );
}
