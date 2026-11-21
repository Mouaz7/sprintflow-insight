import { expectAccessible } from '../support/a11y.ts';
import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expect, test } from '../support/fixtures.ts';

test('creates an organization, joins from an invitation, and applies a scoped template', async ({
  account,
  api,
  baseURL,
  browser,
  page,
}) => {
  if (!baseURL) throw new Error('The application URL is required.');
  const peer = await browser.newContext({ baseURL });
  const peerPage = await peer.newPage();
  const peerApi = new Api(peer.request);
  const invited = newAccount('org-viewer');
  let registered = false;
  const name = `Delivery ${account.email.split('@')[0]}`;
  const slug = `delivery-${Date.now()}`;
  try {
    await register(peerApi, invited);
    registered = true;
    await page.goto('/organizations');
    await expect(
      page.getByRole('link', { name: 'Personal workspace' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'New organization' }).click();
    const newOrganization = page.getByRole('dialog', {
      name: 'New organization',
    });
    await newOrganization.getByLabel('Organization name').fill(name);
    await newOrganization.getByLabel('URL name').fill(slug);
    await newOrganization
      .getByRole('button', { name: 'Create', exact: true })
      .click();
    await expect(page).toHaveURL(new RegExp(`/o/${slug}$`));
    await expect(page.getByRole('heading', { name })).toBeVisible();
    const nav = page.getByRole('navigation', {
      name: 'Organization navigation',
    });
    await expectAccessible(page, 'organization-projects');

    await nav.getByRole('link', { name: 'Teams' }).click();
    await page.getByRole('button', { name: 'New team' }).click();
    const form = page.getByRole('dialog', { name: 'New team' });
    await form.getByLabel('Team name').fill('Delivery team');
    await form.getByRole('button', { name: 'Create', exact: true }).click();
    await expect(
      page.getByRole('listitem').filter({ hasText: 'Delivery team' }),
    ).toBeVisible();

    await nav.getByRole('link', { name: 'Invitations' }).click();
    await page.getByLabel('Invite email').fill(invited.email);
    await page.getByLabel('Role').selectOption('Viewer');
    await page.getByRole('button', { name: 'Create invitation link' }).click();
    const invitation = page.getByRole('textbox', { name: 'Invitation link' });
    await expect(invitation).toBeVisible();
    await peerPage.goto(await invitation.inputValue());
    await peerPage.getByRole('button', { name: 'Accept invitation' }).click();
    await expect(peerPage).toHaveURL(new RegExp(`/o/${slug}$`));
    await expect(peerPage.getByRole('heading', { name })).toBeVisible();
    await expect(
      peerPage.getByRole('button', { name: 'New project' }),
    ).toHaveCount(0);
    expect(
      await peerApi.status(`/organizations/${slug}/teams`, 'POST', {
        name: 'Viewer cannot write',
      }),
    ).toBe(403);

    await nav.getByRole('link', { name: 'Teams' }).click();
    await page.getByRole('button', { name: 'Team members' }).click();
    const team = page.getByRole('dialog', {
      name: 'Team members · Delivery team',
    });
    await team
      .getByLabel('Select member')
      .selectOption({ label: invited.email });
    await team.getByRole('button', { name: 'Add member', exact: true }).click();
    await expect(team.getByText(invited.displayName)).toBeVisible();
    await expectAccessible(page, 'organization-team-roster');
    await team
      .getByRole('button', { name: 'Close panel', exact: true })
      .click();

    await nav.getByRole('link', { name: 'Templates' }).click();
    await page.getByRole('button', { name: 'New template' }).click();
    const newTemplate = page.getByRole('dialog', { name: 'New template' });
    await newTemplate.getByLabel('Template name').fill('Delivery starter');
    await newTemplate.getByLabel('Starter layout').selectOption('Scrum');
    await newTemplate
      .getByRole('button', { name: 'Create', exact: true })
      .click();
    await page.getByRole('button', { name: 'Create from template' }).click();
    const newProject = page.getByRole('dialog', {
      name: 'Create from template',
    });
    await newProject.getByLabel('Project name').fill('Delivery project');
    await newProject
      .getByRole('button', { name: 'Create', exact: true })
      .click();
    await expect(newProject).toHaveCount(0);
    await nav.getByRole('link', { name: 'Projects' }).click();
    await expect(
      page.getByRole('main').getByText('Delivery project', { exact: false }),
    ).toBeVisible();
    const projects = await api.json<{ id: number }[]>(
      `/organizations/${slug}/projects`,
    );
    expect(projects).toHaveLength(1);
    expect(
      await api.json<unknown[]>(`/tasks?projectId=${projects[0]?.id}`),
    ).toHaveLength(3);
  } finally {
    try {
      if (registered) await removeAccount(peerApi, invited);
    } finally {
      await peer.close();
    }
  }
});
