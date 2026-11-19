import { lazy, Suspense, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import type { Task } from '../../api/contracts';
import { customFieldsQuery } from '../../api/customFields';
import { projectScope } from '../../api/queries';

import { useTaskPresence } from '../../shared/lib/useTaskPresence';
import { localizeMessage } from '../../shared/i18n/messages';
import { Drawer, PageHeader } from '../../shared/ui/ui';
import { BlockTaskPanel } from './BlockTaskPanel';
import { Board } from './Board';
import { TaskDiscussion } from './TaskCollaboration';
import { TaskEditor } from './TaskEditor';
import { TaskFilters } from './TaskFilters';
import { useTaskBoard, type TasksViewProps } from './useTaskBoard';
const TaskCustomFieldsDrawer = lazy(() =>
  import('./TaskCustomFields').then((m) => ({
    default: m.TaskCustomFieldsDrawer,
  })),
);
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
  const customFields = useQuery({
    ...customFieldsQuery(projectId ?? 0),
    enabled: !!projectId,
  });
  const client = useQueryClient();
  const [customTask, setCustomTask] = useState<Task>();
  const [fieldsSaved, setFieldsSaved] = useState(false);
  useTaskPresence(
    projectId,
    discussion?.id ??
      blocking?.id ??
      customTask?.id ??
      (panelOpen ? controller.editing?.id : undefined),
  );
  return (
    <section className="page tasks-page">
      <PageHeader
        guide="tasks"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
        action={
          projectId && (
            <>
              <Link to="/fields" className="secondary-action">
                {t('customFields')}
              </Link>
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
            </>
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
          {customFields.isError && (
            <p role="alert">
              {t('fieldsLoadFailed')}{' '}
              <button
                onClick={() => {
                  void customFields.refetch();
                }}
              >
                {t('fieldsRetry')}
              </button>
            </p>
          )}
          {fieldsSaved && (
            <p role="status" className="form-message success">
              {t('fieldValuesSaved')}
            </p>
          )}
          {!panelOpen && message && (
            <p className="form-message error" role="alert">
              {localizeMessage(message, language)}
            </p>
          )}
          {loading && <p role="status">{t('loadingTasks')}</p>}
          <Board
            controller={controller}
            onCustomFields={
              customFields.data?.length
                ? (task) => {
                    setFieldsSaved(false);
                    setCustomTask(task);
                  }
                : undefined
            }
          />
          {customTask && customTask.projectId === projectId && (
            <Suspense
              fallback={
                <Drawer
                  title={t('customFields')}
                  onClose={() => setCustomTask(undefined)}
                >
                  <p role="status">{t('loadingTasks')}</p>
                </Drawer>
              }
            >
              <TaskCustomFieldsDrawer
                key={customTask.id}
                task={customTask}
                definitions={customFields.data ?? []}
                members={controller.members}
                readOnly={!canWrite}
                onClose={() => setCustomTask(undefined)}
                onSaved={async () => {
                  await client.invalidateQueries({
                    queryKey: projectScope(projectId),
                  });
                  setCustomTask(undefined);
                  setFieldsSaved(true);
                }}
              />
            </Suspense>
          )}
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
