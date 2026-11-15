import type { ReactNode } from 'react';

type IconName =
  | 'overview'
  | 'projects'
  | 'backlog'
  | 'sprints'
  | 'tasks'
  | 'metrics'
  | 'updates'
  | 'profile'
  | 'sun'
  | 'moon'
  | 'calendar'
  | 'retrospective'
  | 'trash';

const paths: Record<IconName, ReactNode> = {
  overview: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>
  ),
  projects: (
    <path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
  ),
  backlog: (
    <>
      <path d="M8 5h13M8 12h13M8 19h13" />
      <circle cx="3.5" cy="5" r=".5" />
      <circle cx="3.5" cy="12" r=".5" />
      <circle cx="3.5" cy="19" r=".5" />
    </>
  ),
  sprints: (
    <>
      <path d="M13 2 4 14h7l-1 8 10-13h-7Z" />
    </>
  ),
  tasks: (
    <>
      <rect x="3" y="4" width="18" height="17" rx="2" />
      <path d="m7 12 3 3 7-7" />
    </>
  ),
  metrics: (
    <>
      <path d="M4 3v17h17M8 16v-4m5 4V7m5 9v-6" />
    </>
  ),
  updates: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  profile: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" />
    </>
  ),
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4m10-4v4M3 10h18M7 14h2m4 0h2m-8 4h2" />
    </>
  ),
  retrospective: (
    <>
      <path d="M4 10a8 8 0 1 1 1 8M4 4v6h6M12 8v5l3 2" />
    </>
  ),
  trash: (
    <>
      <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" />
    </>
  ),
  moon: <path d="M20.5 14.2A9 9 0 0 1 9.8 3.5a9 9 0 1 0 10.7 10.7Z" />,
};

export function InterfaceIcon({ name }: { name: IconName }) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
