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
    t,
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
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
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
              + {t('newTask')}
            </button>
          )
        }
      />
      {hasProjects && projectId ? (
        <>
          {blocking && (
            <BlockTaskPanel
              task={blocking}
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
              {t('taskSaved', { title: savedTitle })}
            </p>
          )}
          <TaskFilters controller={controller} />
          {!panelOpen && message && (
            <p className="form-message error" role="alert">
              {localizeMessage(message, language)}
            </p>
          )}
          {loading && <p role="status">{t('loadingTasks')}</p>}
          <Board controller={controller} />
        </>
      ) : (
        <div className="empty-state">{t('createProjectFirst')}</div>
      )}
      {discussion && (
        <TaskDiscussion
          task={discussion}
          userEmail={userEmail}
          canModerate={role === 'Owner' || role === 'Admin'}
          readOnly={!canWrite}
          onClose={() => setDiscussion(undefined)}
        />
      )}
    </section>
  );
}
