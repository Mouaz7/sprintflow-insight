import type { Browser, BrowserContext } from '@playwright/test';
import {
  Api,
  newAccount,
  register,
  removeAccount,
  type Account,
} from '../support/api.ts';
import { expect, isoDate, navigationItem, test } from '../support/fixtures.ts';

// Ported from scripts/verification/business-rules.ts: role matrix and
// cross-project isolation against the real database.
const roles = ['Owner', 'Admin', 'Member', 'Viewer', 'Outsider'] as const;
type Role = (typeof roles)[number];
interface Member {
  account: Account;
  context: BrowserContext;
  api: Api;
}

async function signUp(
  browser: Browser,
  role: Role,
  baseURL: string,
): Promise<Member> {
  const context = await browser.newContext({ baseURL });
  const api = new Api(context.request);
  const account = newAccount(role.toLowerCase());
  await register(api, account);
  return { account, context, api };
}

test('enforces project roles, isolation and ownership transfer', async ({
  browser,
  baseURL,
}) => {
  const members = new Map<Role, Member>();
  try {
    for (const role of roles)
      members.set(role, await signUp(browser, role, baseURL ?? ''));
    const get = (role: Role): Member => members.get(role) as Member;
    const owner = get('Owner');
    const { id: projectId } = await owner.api.json<{ id: number }>(
      '/projects',
      'POST',
      {
        name: 'E2E roles',
      },
    );
    const { id: otherProject } = await owner.api.json<{ id: number }>(
      '/projects',
      'POST',
      {
        name: 'E2E separate project',
      },
    );
    for (const role of ['Admin', 'Member', 'Viewer'] as const) {
      await owner.api.json(`/projects/${projectId}/members`, 'POST', {
        email: get(role).account.email,
        role,
      });
    }

    for (const role of ['Admin', 'Member', 'Viewer'] as const) {
      expect(
        await get(role).api.status(`/tasks?projectId=${otherProject}`),
        role,
      ).toBe(403);
    }
    expect(
      await get('Outsider').api.status(`/tasks?projectId=${projectId}`),
    ).toBe(403);
    for (const role of ['Owner', 'Admin', 'Member'] as const) {
      await get(role).api.json('/tasks', 'POST', {
        projectId,
        title: `Allowed for ${role}`,
        status: 'To Do',
      });
    }
    const viewerWrite = { projectId, title: 'Forbidden', status: 'To Do' };
    expect(await get('Viewer').api.status('/tasks', 'POST', viewerWrite)).toBe(
      403,
    );
    expect(
      await get('Member').api.status(`/projects/${projectId}/members`, 'POST', {
        email: get('Outsider').account.email,
        role: 'Viewer',
      }),
    ).toBe(403);

    // Only one active sprint per project, and associations must stay inside the project.
    const dates = { startDate: isoDate(0), endDate: isoDate(7) };
    const sprint = await owner.api.json<{ id: number }>('/sprints', 'POST', {
      projectId,
      name: 'Active sprint',
      status: 'Active',
      ...dates,
    });
    expect(
      await owner.api.status('/sprints', 'POST', {
        projectId,
        name: 'Second active',
        status: 'Active',
        ...dates,
      }),
    ).toBe(409);
    const foreignSprint = await owner.api.json<{ id: number }>(
      '/sprints',
      'POST',
      {
        projectId: otherProject,
        name: 'Foreign sprint',
        status: 'Planned',
        ...dates,
      },
    );
    const story = await owner.api.json<{ id: number }>('/stories', 'POST', {
      projectId,
      title: 'User can log in',
      acceptanceCriteria: 'Valid credentials open workspace',
      storyPoints: 3,
      priorityId: 2,
    });
    const task = {
      projectId,
      title: 'Build login form',
      status: 'To Do',
      storyId: story.id,
    };
    // A task linked to a story inherits the story's sprint and cannot pick its own.
    expect(
      await owner.api.status('/tasks', 'POST', {
        ...task,
        sprintId: sprint.id,
      }),
    ).toBe(400);
    expect(
      await owner.api.status('/tasks', 'POST', {
        ...task,
        sprintId: foreignSprint.id,
      }),
    ).toBe(400);
    expect(
      await owner.api.status('/tasks', 'POST', {
        ...task,
        assigneeEmail: get('Outsider').account.email,
      }),
    ).toBe(400);

    // Admins manage members but cannot hand over ownership.
    const adminPage = await get('Admin').context.newPage();
    await adminPage.goto('/');
    await navigationItem(adminPage, 'Projects').click();
    await expect(
      adminPage.getByRole('button', { name: 'Add member', exact: true }),
    ).toBeVisible();
    await expect(
      adminPage.getByRole('button', { name: 'Make owner', exact: true }),
    ).toHaveCount(0);

    await owner.api.json(`/projects/${projectId}/owner`, 'PUT', {
      email: get('Admin').account.email,
    });
    expect(
      (await owner.api.json<{ role: string }>(`/projects/${projectId}/role`))
        .role,
    ).toBe('Member');
    expect(
      (
        await get('Admin').api.json<{ role: string }>(
          `/projects/${projectId}/role`,
        )
      ).role,
    ).toBe('Owner');
    expect(
      await owner.api.status(`/projects/${projectId}/owner`, 'PUT', {
        email: owner.account.email,
      }),
    ).toBe(403);
    await get('Admin').api.json(`/projects/${projectId}/owner`, 'PUT', {
      email: owner.account.email,
    });
  } finally {
    for (const member of [...members.values()].reverse()) {
      await removeAccount(member.api, member.account);
      await member.context.close();
    }
  }
});
