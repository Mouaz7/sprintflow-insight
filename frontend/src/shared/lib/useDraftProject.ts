import { useQuery } from '@tanstack/react-query';
import { createContext, useContext, useState } from 'react';
import type { Language, Project } from '../../api/contracts';
import { roleQuery } from '../../api/queries';
import { common } from '../i18n/common';
import { translator } from '../i18n/lexicon';

export const DraftProjectsContext = createContext<
  | {
      projects: Project[];
      onSelect: (id: number) => void;
    }
  | undefined
>(undefined);

// A form's destination is local until the write succeeds, so changing it never
// remounts the editor or loses the title/description already entered.
export function useDraftProject(
  projectId: number | undefined,
  language: Language,
) {
  const scope = useContext(DraftProjectsContext);
  const [targetId, setTargetId] = useState(projectId);
  const projects = scope?.projects.filter(
    (project) => !project.archivedAt && !project.trashedAt,
  );
  const role = useQuery({
    ...roleQuery(targetId ?? 0),
    enabled: Boolean(scope && targetId),
  });
  const access =
    role.isSuccess && role.data
      ? {
          id: targetId,
          allowed: ['Owner', 'Admin', 'Member'].includes(role.data.role),
          error: undefined,
        }
      : role.isError
        ? { id: targetId, allowed: false, error: role.error.message }
        : undefined;
  const loading = Boolean(scope && targetId && access?.id !== targetId);
  const invalid = Boolean(
    scope && !projects?.some((project) => project.id === targetId),
  );
  const denied = Boolean(scope && !loading && !access?.allowed);
  return {
    id: targetId,
    projects,
    loading,
    blocked: loading || invalid || denied,
    error:
      (access?.id === targetId && access?.error) ||
      (denied ? translator(common, language)('noWriteAccess') : ''),
    change: (id: number) => {
      if (id !== targetId) setTargetId(id);
    },
    reset: () => {
      setTargetId(projectId);
    },
    reveal: (id: number) => {
      if (scope && id !== projectId) {
        scope.onSelect(id);
        return true;
      }
      return false;
    },
  };
}
