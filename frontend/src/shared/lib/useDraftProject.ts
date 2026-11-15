import { createContext, useContext, useEffect, useState } from 'react';
import type { Language, Project } from '../../api/contracts';
import { api } from '../../api/client';

export const DraftProjectsContext = createContext<{
  projects: Project[];
  onSelect: (id: number) => void;
} | undefined>(undefined);

// A form's destination is local until the write succeeds, so changing it never
// remounts the editor or loses the title/description already entered.
export function useDraftProject(projectId: number | undefined, language: Language) {
  const scope = useContext(DraftProjectsContext);
  const [targetId, setTargetId] = useState(projectId);
  const [access, setAccess] = useState<{ id: number; allowed: boolean; error?: string }>();
  const projects = scope?.projects.filter(project => !project.archivedAt && !project.trashedAt);
  useEffect(() => {
    if (!scope || !targetId) return;
    let live = true;
    api<{ role: string }>(`/projects/${targetId}/role`).then(({ role }) => {
      if (live) setAccess({ id: targetId, allowed: ['Owner', 'Admin', 'Member'].includes(role) });
    }).catch((cause: Error) => {
      if (live) setAccess({ id: targetId, allowed: false, error: cause.message });
    });
    return () => { live = false; };
  }, [scope, targetId]);
  const loading = Boolean(scope && targetId && access?.id !== targetId);
  const invalid = Boolean(scope && !projects?.some(project => project.id === targetId));
  const denied = Boolean(scope && !loading && !access?.allowed);
  return {
    id: targetId,
    projects,
    loading,
    blocked: loading || invalid || denied,
    error: access?.id === targetId && access?.error || (denied
      ? language === 'SV' ? 'Du har inte skrivrättighet i det valda projektet.' : 'You cannot create items in the selected project.'
      : ''),
    change: (id: number) => { if (id !== targetId) { setAccess(undefined); setTargetId(id); } },
    reset: () => { setTargetId(projectId); },
    reveal: (id: number) => {
      if (scope && id !== projectId) { scope.onSelect(id); return true; }
      return false;
    },
  };
}
