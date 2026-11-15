import { localizeMessage } from '../../shared/i18n/messages';
import { PageHeader } from '../../shared/ui/ui';
import { BlockTaskPanel } from './BlockTaskPanel';
import { Board } from './Board';
import { TaskDiscussion } from './TaskCollaboration';
import { TaskEditor } from './TaskEditor';
import { TaskFilters } from './TaskFilters';
import { useTaskBoard, type TasksViewProps } from './useTaskBoard';
export function TasksView(props: TasksViewProps) {
  const controller = useTaskBoard(props);
  const {
    language,
    sv,
    projectId,
    canWrite,
    clearForm,
    setMessage,
    setPanelOpen,
    hasProjects,
    blocking,
    setBlocking,
    setTasks,
    panelOpen,
    savedTitle,
    message,
    loading,
    discussion,
    userEmail,
    role,
    setDiscussion,
  } = controller;
  return (
    <section className="page tasks-page">
      <PageHeader
        guide="tasks"
        language={language}
        eyebrow={sv ? 'TEAMETS ARBETE' : 'TEAM EXECUTION'}
        title={sv ? 'Uppgifter' : 'Tasks'}
        description={
          sv
            ? 'Från nästa steg till levererat arbete.'
            : 'From the next step to delivered work.'
        }
        action={
          projectId && (
            <button
              className="primary-action"
              disabled={!canWrite}
              onClick={() => {
                clearForm();
                setMessage('');
                setPanelOpen(true);
              }}
            >
              + {sv ? 'Ny uppgift' : 'New task'}
            </button>
          )
        }
      />
      {hasProjects && projectId ? (
        <>
          {blocking && (
            <BlockTaskPanel
              task={blocking}
              language={language}
              onClose={() => setBlocking(undefined)}
              onSaved={(saved) => {
                setTasks((items) =>
                  items.map((item) => (item.id === saved.id ? saved : item)),
                );
                setBlocking(undefined);
              }}
            />
          )}
          {panelOpen && <TaskEditor controller={controller} />}
          {!panelOpen && savedTitle && (
            <p className="form-message success" role="status">
              {sv
                ? `Uppgiften ”${savedTitle}” har sparats. Alla uppgifter visas.`
                : `Task “${savedTitle}” saved. Showing all tasks.`}
            </p>
          )}
          <TaskFilters controller={controller} />
          {!panelOpen && message && (
            <p className="form-message error" role="alert">
              {localizeMessage(message, sv ? 'SV' : 'EN')}
            </p>
          )}
          {loading && (
            <p role="status">{sv ? 'Laddar uppgifter…' : 'Loading tasks…'}</p>
          )}
          <Board controller={controller} />
        </>
      ) : (
        <div className="empty-state">
          {sv
            ? 'Skapa ett projekt innan du lägger till uppgifter.'
            : 'Create a project before adding tasks.'}
        </div>
      )}
      {discussion && (
        <TaskDiscussion
          task={discussion}
          language={language}
          userEmail={userEmail}
          canModerate={role === 'Owner' || role === 'Admin'}
          readOnly={!canWrite}
          onClose={() => setDiscussion(undefined)}
        />
      )}
    </section>
  );
}
