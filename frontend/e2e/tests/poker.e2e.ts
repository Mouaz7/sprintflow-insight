import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

type Session = {
  id: number;
  round: number;
  status: string;
  myCard: string | null;
  rounds: { number: number; votes: { card: string }[] }[];
  finalStoryPoints: number | null;
  canVote: boolean;
  canFacilitate: boolean;
};

test('estimates privately in two browsers, reveals, revotes and saves a traceable consensus', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}, testInfo) => {
  const peerContext = await browser.newContext({
    baseURL,
    viewport: { width: 390, height: 844 },
    reducedMotion: 'reduce',
  });
  const peerApi = new Api(peerContext.request);
  const teammate = newAccount('poker-member');
  const errors: string[] = [];
  const peer = await peerContext.newPage();
  peer.on('pageerror', (error) => errors.push(error.message));
  let registered = false;
  const frames: string[] = [];
  page.on('websocket', (socket) =>
    socket.on('framereceived', (event) => frames.push(String(event.payload))),
  );
  try {
    await register(peerApi, teammate);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: `Poker ${account.email}`,
      key: 'PK',
    });
    const base = `/projects/${project.id}`;
    await api.json(`${base}/members`, 'POST', {
      email: teammate.email,
      role: 'Member',
    });
    const story = await api.json<{ id: number }>('/stories', 'POST', {
      title: 'Accessible checkout',
      projectId: project.id,
      acceptanceCriteria: 'Team agrees',
      storyPoints: 3,
      priorityId: 2,
    });
    await page.goto('/poker');
    await page
      .getByRole('button', { name: 'Start a session', exact: true })
      .click();
    await page
      .getByRole('combobox', { name: 'Story', exact: true })
      .selectOption(String(story.id));
    await page
      .getByRole('combobox', { name: 'Card deck', exact: true })
      .selectOption('FIBONACCI');
    await page
      .getByRole('button', { name: 'Start voting', exact: true })
      .click();
    await expect(
      page.getByRole('button', { name: 'Vote 3', exact: true }),
    ).toBeVisible();
    const id = Number(new URL(page.url()).searchParams.get('session'));
    expect(id).toBeGreaterThan(0);
    const sessionPath = `${base}/poker-sessions/${id}`;
    await peer.goto(`/poker?session=${id}`);
    await expect(peer.locator('.presence')).toHaveClass(/presence-live/);
    await page.getByRole('button', { name: 'Vote 3', exact: true }).focus();
    await page.keyboard.press('Enter');
    await peer.getByRole('button', { name: 'Vote 8', exact: true }).click();
    await expect(
      page.getByText('Round 1 · 2 people have voted', { exact: true }),
    ).toBeVisible();
    await expect(peer.getByText('Your vote: 8', { exact: true })).toBeVisible();
    await expect(
      page.getByRole('table', { name: 'Card distribution' }),
    ).toHaveCount(0);
    const hidden = await api.json<Session>(sessionPath);
    expect(hidden.myCard).toBe('3');
    expect(hidden.rounds).toEqual([]);
    expect((await peerApi.json<Session>(sessionPath)).myCard).toBe('8');
    await expect
      .poll(
        () =>
          frames.filter((frame) => frame.includes('PokerSessionChanged'))
            .length,
      )
      .toBeGreaterThan(0);
    for (const frame of frames.filter((frame) =>
      frame.includes('PokerSessionChanged'),
    ))
      expect(frame).not.toMatch(/"card"|"myCard"|"votes"|"statistics"/);
    await expectAccessible(page, 'poker-hidden-owner');
    await expectAccessible(peer, 'poker-hidden-mobile-member');
    await page
      .getByRole('button', { name: 'Reveal votes', exact: true })
      .click();
    await expect(
      page.getByRole('table', { name: 'Card distribution' }),
    ).toBeVisible();
    await expect(
      peer.getByRole('table', { name: 'Card distribution' }),
    ).toBeVisible();
    await expect(
      peer.getByRole('button', { name: 'Start another round' }),
    ).toHaveCount(0);
    const revealed = await peerApi.json<Session>(sessionPath);
    expect(revealed.rounds[0]?.votes.map((vote) => vote.card).sort()).toEqual([
      '3',
      '8',
    ]);
    await expectAccessible(page, 'poker-revealed-owner');
    await testInfo.attach('poker-revealed', {
      body: await page.screenshot(),
      contentType: 'image/png',
    });
    await page
      .getByRole('button', { name: 'Start another round', exact: true })
      .click();
    await expect(
      peer.getByText('Round 2 · 0 people have voted', { exact: true }),
    ).toBeVisible();
    await expect(
      peer.getByRole('button', { name: 'Vote 8', exact: true }),
    ).toHaveAttribute('aria-pressed', 'false');
    expect(
      await peerApi.status(`${sessionPath}/vote`, 'PUT', {
        round: 1,
        card: '13',
      }),
    ).toBe(409);
    await page.getByRole('button', { name: 'Vote 5', exact: true }).click();
    await peer.getByRole('button', { name: 'Vote 5', exact: true }).click();
    await expect(
      page.getByText('Round 2 · 2 people have voted', { exact: true }),
    ).toBeVisible();
    await page
      .getByRole('button', { name: 'Reveal votes', exact: true })
      .click();
    await expect(
      page.getByText('Estimating cards agree', { exact: true }),
    ).toBeVisible();
    await page
      .getByRole('button', { name: 'Save agreed estimate', exact: true })
      .click();
    await expect(
      page.getByText('Agreed estimate saved: 5 story points.', { exact: true }),
    ).toBeVisible();
    await expect(
      peer.getByText('Agreed estimate saved: 5 story points.', { exact: true }),
    ).toBeVisible();
    expect(
      (await api.json<{ storyPoints: number }>(`/stories/${story.id}`))
        .storyPoints,
    ).toBe(5);
    const activity = await api.json<{ eventType: string; summary: string }[]>(
      `${base}/activity`,
    );
    expect(
      activity.some(
        (entry) =>
          entry.eventType === 'STORY_ESTIMATED' &&
          entry.summary.includes('at 5 points'),
      ),
    ).toBe(true);
    await page.reload();
    await expect(
      page.getByText('Agreed estimate saved: 5 story points.', { exact: true }),
    ).toBeVisible();
    await page.getByText('Earlier rounds (1)', { exact: true }).click();
    await expect(
      page.getByRole('table', { name: 'Card distribution' }),
    ).toHaveCount(2);
    await expectAccessible(page, 'poker-saved-history');
    await api.json(`${base}/members`, 'POST', {
      email: teammate.email,
      role: 'Viewer',
    });
    const watched = await api.json<Session>(`${base}/poker-sessions`, 'POST', {
      storyId: story.id,
      deck: 'TSHIRT',
    });
    await peer.goto(`/poker?session=${watched.id}`);
    await expect(
      peer.getByText(
        'Other votes stay hidden until the facilitator reveals this round.',
      ),
    ).toBeVisible();
    await expect(
      peer.getByRole('button', { name: 'Start a session', exact: true }),
    ).toHaveCount(0);
    await expect(
      peer.getByRole('button', { name: 'Vote M', exact: true }),
    ).toHaveCount(0);
    expect(
      await peerApi.status(`${base}/poker-sessions/${watched.id}/vote`, 'PUT', {
        round: 1,
        card: 'M',
      }),
    ).toBe(403);
    await expectAccessible(peer, 'poker-viewer-mobile');
    expect(errors).toEqual([]);
  } finally {
    if (registered) await removeAccount(peerApi, teammate);
    await peerContext.close();
  }
});

test('supports custom cards, read-only viewers and archived sessions on a Swedish dark mobile screen', async ({
  page,
  api,
  account,
}, testInfo) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Kortlek och arkiv',
    description: account.email,
  });
  const story = await api.json<{ id: number }>('/stories', 'POST', {
    title:
      'En lång berättelsetitel som fortfarande ska rymmas på en liten mobilskärm',
    projectId: project.id,
    acceptanceCriteria: 'Klart',
    storyPoints: 3,
    priorityId: 2,
  });
  const base = `/projects/${project.id}`;
  await page.addInitScript(() => {
    localStorage.setItem('sprintflow-language', 'SV');
    localStorage.setItem('sprintflow-theme', 'dark');
  });
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/poker');
  await page
    .getByRole('button', { name: 'Starta en session', exact: true })
    .click();
  await page
    .getByRole('combobox', { name: 'Berättelse', exact: true })
    .selectOption(String(story.id));
  await page
    .getByRole('combobox', { name: 'Kortlek', exact: true })
    .selectOption('CUSTOM');
  await page.getByLabel('Anpassade kort', { exact: true }).fill('XS, M, XL, ?');
  await expectAccessible(page, 'poker-custom-mobile-form');
  await page
    .getByRole('button', { name: 'Starta röstning', exact: true })
    .click();
  await page.getByRole('button', { name: 'Rösta M', exact: true }).click();
  await expect(page.getByText('Din röst: M', { exact: true })).toBeVisible();
  const id = Number(new URL(page.url()).searchParams.get('session'));
  const path = `${base}/poker-sessions/${id}`;
  await api.json(`${base}/archive`, 'PUT', { archived: true });
  await page.reload();
  await expect(
    page.getByText('Andras röster är dolda tills spelledaren avslöjar rundan.'),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Rösta M', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: 'Avslöja röster', exact: true }),
  ).toHaveCount(0);
  expect((await api.json<Session>(path)).canVote).toBe(false);
  expect(
    await api.status(`${path}/vote`, 'PUT', { round: 1, card: 'XL' }),
  ).toBe(409);
  await expectAccessible(page, 'poker-archived-swedish-dark');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await testInfo.attach('poker-mobile-archive', {
    body: await page.screenshot(),
    contentType: 'image/png',
  });
});
