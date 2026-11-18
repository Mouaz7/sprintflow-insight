import { mockApi } from '../test/api';

export const projects = [
  {
    id: 1,
    name: 'Alpha',
    description: 'First project',
    ownerEmail: 'leo@example.com',
  },
  {
    id: 2,
    name: 'Beta',
    description: 'Second project',
    ownerEmail: 'leo@example.com',
  },
];

export function mockSignedIn(projectList = projects) {
  const requests: string[] = [];
  mockApi(async (input) => {
    const url = String(input);
    requests.push(url);
    const payload = url.endsWith('/auth/me')
      ? { id: 1, email: 'leo@example.com', displayName: 'Leo' }
      : url.endsWith('/projects')
        ? projectList
        : url.endsWith('/metrics')
          ? {
              taskCount: 8,
              completionRate: 50,
              throughputLast14Days: 3,
              sprintHealth: { status: 'Healthy' },
            }
          : [];
    return payload;
  });
  return requests;
}
