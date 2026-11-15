import { useEffect, useState } from 'react';
import {
  ProjectConnection,
  type RealtimeStatus,
  type Viewer,
} from './connection';

export type ProjectRealtime = {
  /** Increases (debounced) whenever the project changed on the server. */
  revision: number;
  viewers: Viewer[];
  status: RealtimeStatus;
};

const COALESCE_MS = 250;

/**
 * Live updates for one project. Consumers reload their data when `revision`
 * changes; a burst of messages leads to a single reload.
 */
export function useProjectRealtime(
  projectId: number | undefined,
): ProjectRealtime {
  const [revision, setRevision] = useState(0);
  const [viewers, setViewers] = useState<Viewer[]>([]);
  const [status, setStatus] = useState<RealtimeStatus>('offline');
  useEffect(() => {
    setViewers([]);
    setStatus('offline');
    if (projectId === undefined || typeof WebSocket !== 'function') return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const connection = new ProjectConnection(projectId, {
      onChange: () => {
        if (timer !== undefined) return;
        timer = setTimeout(() => {
          timer = undefined;
          setRevision((value) => value + 1);
        }, COALESCE_MS);
      },
      onPresence: setViewers,
      onStatus: setStatus,
    });
    connection.open();
    return () => {
      if (timer !== undefined) clearTimeout(timer);
      connection.close();
    };
  }, [projectId]);
  return { revision, viewers, status };
}
