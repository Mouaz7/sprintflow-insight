import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { projectScope } from '../../api/queries';
import { taskPresenceEvent, type TaskPresenceDetail } from '../../shared/lib/useTaskPresence';
import { ProjectConnection, type RealtimeStatus, type Viewer } from './connection';
import type { RealtimeMessage } from './sequence';

export type ProjectRealtime = { viewers: Viewer[]; status: RealtimeStatus };
const COALESCE_MS = 250;

/** Refresh only the active project and the affected open threads. */
export function useProjectRealtime(projectId: number | undefined): ProjectRealtime {
  const client = useQueryClient();
  const [viewers, setViewers] = useState<Viewer[]>([]);
  const [status, setStatus] = useState<RealtimeStatus>('offline');
  useEffect(() => {
    setViewers([]);
    setStatus('offline');
    if (projectId === undefined || typeof WebSocket !== 'function') return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let resync = false;
    const comments = new Set<number>();
    const connection = new ProjectConnection(projectId, {
      onChange: (messages: RealtimeMessage[]) => {
        if (!messages.length) resync = true;
        for (const message of messages) {
          if (message.type.startsWith('Comment') && message.entityId !== null)
            comments.add(message.entityId);
        }
        if (timer !== undefined) return;
        timer = setTimeout(() => {
          timer = undefined;
          void client.invalidateQueries({ queryKey: projectScope(projectId) });
          void client.invalidateQueries({ queryKey: ['projects'] });
          void client.invalidateQueries({ queryKey: ['notifications'] });
          // Resync also refreshes open comments whose missed ids were pruned.
          if (resync) void client.invalidateQueries({ queryKey: ['task'] });
          else for (const taskId of comments)
            void client.invalidateQueries({ queryKey: ['task', taskId, 'comments'] });
          comments.clear();
          resync = false;
        }, COALESCE_MS);
      },
      onPresence: setViewers,
      onStatus: setStatus,
    });
    const openTask = (event: Event) => {
      const detail = (event as CustomEvent<TaskPresenceDetail>).detail;
      if (detail.projectId === projectId) connection.openTask(detail.taskId);
    };
    window.addEventListener(taskPresenceEvent, openTask);
    connection.open();
    return () => {
      window.removeEventListener(taskPresenceEvent, openTask);
      if (timer !== undefined) clearTimeout(timer);
      connection.close();
    };
  }, [projectId, client]);
  return { viewers, status };
}
