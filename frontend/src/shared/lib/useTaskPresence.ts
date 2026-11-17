import { useEffect } from 'react';
export const taskPresenceEvent = 'sprintflow:open-task';
export type TaskPresenceDetail = { projectId: number; taskId: number | null };
/** Keep presence free of task titles or other task content. */
export function useTaskPresence(
  projectId: number | undefined,
  taskId: number | undefined,
) {
  useEffect(() => {
    if (projectId === undefined) return;
    const publish = (id: number | null) =>
      window.dispatchEvent(
        new CustomEvent<TaskPresenceDetail>(taskPresenceEvent, {
          detail: { projectId, taskId: id },
        }),
      );
    publish(taskId ?? null);
    return () => {
      publish(null);
    };
  }, [projectId, taskId]);
}
