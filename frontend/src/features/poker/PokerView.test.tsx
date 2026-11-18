import {
  act,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Language } from '../../api/contracts';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { ConfirmProvider } from '../../shared/ui/ui';
import type { PokerRound, PokerSession } from './contracts';
import { PokerView } from './PokerView';
import { pokerKey } from './queries';

const project = { id: 1, name: 'Poker', ownerEmail: 'owner@test.se' };
const result: PokerRound = {
  number: 1,
  votes: [{ userId: 2, name: 'Sam', card: '5' }],
  statistics: {
    votes: 1,
    abstentions: 0,
    distribution: [{ card: '5', count: 1 }],
    min: '5',
    max: '5',
    median: '5',
    modes: ['5'],
    consensus: true,
    agreement: 1,
    mean: 5,
    spread: 0,
    suggestion: '5',
    suggestedStoryPoints: 5,
    revoteRecommended: false,
  },
};
const initial: PokerSession = {
  id: 7,
  projectId: 1,
  storyId: 10,
  storyTitle: 'Accessible checkout',
  storyPoints: 3,
  storyVersion: 0,
  deck: 'FIBONACCI',
  cards: ['1', '2', '3', '5', '8', '13', '?', '☕'],
  status: 'VOTING',
  round: 1,
  facilitatorUserId: 1,
  voters: [{ userId: 2, name: 'Sam' }],
  myCard: null,
  rounds: [],
  finalValue: null,
  finalStoryPoints: null,
  canVote: true,
  canFacilitate: true,
  createdAt: '2026-11-18T12:00:00Z',
  closedAt: null,
  version: 0,
};

function mount({
  data = initial,
  role = 'Owner',
  language = 'EN' as Language,
  archived = false,
  empty = false,
  reject = false,
} = {}) {
  let snapshot = structuredClone(data);
  let failedReads = false;
  let rejectWrite = reject;
  const writes: {
    path: string;
    method: string;
    body: Record<string, unknown>;
  }[] = [];
  mockApi((path, request) => {
    if (path.endsWith('/auth/csrf'))
      return { token: 'test', headerName: 'X-CSRF-TOKEN' };
    if (path.endsWith('/role')) return { role };
    if (path.includes('/stories/project/'))
      return [
        { id: 10, title: 'Accessible checkout', storyPoints: 3, projectId: 1 },
      ];
    if (request.method !== 'GET') {
      const body = JSON.parse(request.body ?? '{}') as Record<string, unknown>;
      writes.push({ path, method: request.method, body });
      if (rejectWrite) {
        rejectWrite = false;
        return HttpResponse.json(
          { detail: 'Change rejected' },
          { status: 409 },
        );
      }
      if (path.endsWith('/poker-sessions')) return snapshot;
      if (path.endsWith('/vote'))
        snapshot = {
          ...snapshot,
          myCard: request.method === 'DELETE' ? null : String(body.card),
        };
      if (path.endsWith('/reveal'))
        snapshot = {
          ...snapshot,
          status: 'REVEALED',
          canVote: false,
          rounds: [result],
        };
      if (path.endsWith('/revote'))
        snapshot = {
          ...snapshot,
          status: 'VOTING',
          round: 2,
          myCard: null,
          voters: [],
          canVote: true,
        };
      if (path.endsWith('/accept'))
        snapshot = {
          ...snapshot,
          status: 'ACCEPTED',
          finalStoryPoints: Number(body.storyPoints),
          canVote: false,
          canFacilitate: false,
        };
      return snapshot;
    }
    if (failedReads) return HttpResponse.json({}, { status: 403 });
    if (path.includes('/poker-sessions?')) {
      const closed = path.endsWith('true');
      return empty ||
        closed !== ['ACCEPTED', 'CANCELLED'].includes(snapshot.status)
        ? []
        : [
            {
              id: snapshot.id,
              storyId: snapshot.storyId,
              storyTitle: snapshot.storyTitle,
              status: snapshot.status,
              round: snapshot.round,
              finalStoryPoints: snapshot.finalStoryPoints,
            },
          ];
    }
    if (path.endsWith('/poker-sessions/7')) return snapshot;
    throw new Error(`Unexpected API ${path}`);
  });
  const view = renderWithProviders(
    <ConfirmProvider>
      <PokerView
        project={{
          ...project,
          ...(archived ? { archivedAt: '2026-11-18' } : {}),
        }}
      />
    </ConfirmProvider>,
    { language, path: '/poker?session=7' },
  );
  return {
    ...view,
    writes,
    failReads: () => {
      failedReads = true;
    },
    changeStory: () => {
      snapshot = { ...snapshot, storyPoints: 8, storyVersion: 1 };
    },
  };
}

describe('Planning poker', () => {
  it('shows only participation before reveal and sends votes for the displayed round', async () => {
    const user = userEvent.setup();
    const view = mount();
    await screen.findByText(
      'Other votes stay hidden until the facilitator reveals this round.',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('Sam')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Vote 3' }));
    expect(await screen.findByText('Your vote: 3')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Vote 3' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(view.writes[0]).toEqual({
      path: '/api/projects/1/poker-sessions/7/vote',
      method: 'PUT',
      body: { round: 1, card: '3' },
    });
    await user.click(screen.getByRole('button', { name: 'Retract my vote' }));
    await waitFor(() =>
      expect(screen.queryByText('Your vote: 3')).not.toBeInTheDocument(),
    );
    expect(view.writes[1]?.method).toBe('DELETE');
    expect(view.writes[1]?.body).toEqual({ round: 1 });
    await expectNoSeriousA11yViolations(view.container);
  });

  it('reveals results, preserves the revealed history on revote and clears your old card', async () => {
    const user = userEvent.setup();
    const view = mount();
    await user.click(
      await screen.findByRole('button', { name: 'Reveal votes' }),
    );
    expect(
      await screen.findByRole('table', { name: 'Card distribution' }),
    ).toBeVisible();
    expect(screen.getByText('Estimating cards agree')).toBeVisible();
    expect(screen.getByLabelText('Agreed story points')).toHaveValue('5');
    await expectNoSeriousA11yViolations(view.container);
    await user.click(
      screen.getByRole('button', { name: 'Start another round' }),
    );
    await screen.findByRole('button', { name: 'Vote 5' });
    expect(screen.getByRole('button', { name: 'Vote 5' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByText('Waiting for the team to vote.')).toBeVisible();
    fireEvent.click(screen.getByText('Earlier rounds (1)'));
    expect(
      screen.getByRole('table', { name: 'Card distribution' }),
    ).toBeVisible();
    expect(view.writes.at(-1)?.body).toEqual({ round: 1 });
  });

  it('keeps an estimate draft after a remote story change and requires an explicit reload', async () => {
    const user = userEvent.setup();
    const view = mount({
      data: {
        ...initial,
        status: 'REVEALED',
        canVote: false,
        rounds: [result],
      },
    });
    await user.selectOptions(
      await screen.findByLabelText('Agreed story points'),
      '2',
    );
    view.changeStory();
    await act(async () => {
      await view.client.invalidateQueries({ queryKey: pokerKey(1) });
    });
    expect(screen.getByLabelText('Agreed story points')).toHaveValue('2');
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Save agreed estimate' }),
      ).toBeDisabled(),
    );
    await user.click(
      screen.getByRole('button', { name: 'Reload story estimate' }),
    );
    expect(screen.getByLabelText('Agreed story points')).toHaveValue('8');
    await user.selectOptions(screen.getByLabelText('Agreed story points'), '5');
    await user.click(
      screen.getByRole('button', { name: 'Save agreed estimate' }),
    );
    expect(
      await screen.findByText('Agreed estimate saved: 5 story points.'),
    ).toBeVisible();
    expect(view.writes.at(-1)?.body).toEqual({
      round: 1,
      storyPoints: 5,
      storyVersion: 1,
    });
  });

  it.each([
    { role: 'Viewer', archived: false, language: 'EN' as const },
    { role: 'Owner', archived: true, language: 'SV' as const },
  ])('keeps watcher controls read-only in $language', async (options) => {
    const view = mount({
      ...options,
      data: { ...initial, canVote: false, canFacilitate: false },
    });
    await screen.findByText('Sam');
    expect(
      screen.queryByRole('button', { name: /^(Vote|Rösta) / }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Reveal votes' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Start a session' }),
    ).not.toBeInTheDocument();
    if (options.language === 'SV')
      expect(
        screen.getByText(
          'Andras röster är dolda tills spelledaren avslöjar rundan.',
        ),
      ).toBeVisible();
    await expectNoSeriousA11yViolations(view.container);
  });

  it('validates custom decks, preserves a failed draft and starts from a successful response', async () => {
    const user = userEvent.setup();
    const view = mount({ empty: true, reject: true });
    await user.click(
      await screen.findByRole('button', { name: 'Start a session' }),
    );
    await user.selectOptions(await screen.findByLabelText('Story'), '10');
    await user.selectOptions(screen.getByLabelText('Card deck'), 'CUSTOM');
    await user.clear(screen.getByLabelText('Custom cards'));
    await user.type(screen.getByLabelText('Custom cards'), '?, ☕');
    await user.click(screen.getByRole('button', { name: 'Start voting' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Enter 2–20 different cards',
    );
    expect(view.writes).toHaveLength(0);
    await user.clear(screen.getByLabelText('Custom cards'));
    await user.type(screen.getByLabelText('Custom cards'), 'XS, M, XL, ?');
    await user.click(screen.getByRole('button', { name: 'Start voting' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not save',
    );
    expect(screen.getByLabelText('Custom cards')).toHaveValue('XS, M, XL, ?');
    await user.click(screen.getByRole('button', { name: 'Start voting' }));
    await waitFor(() =>
      expect(screen.queryByLabelText('Custom cards')).not.toBeInTheDocument(),
    );
    expect(view.writes.at(-1)?.body).toEqual({
      storyId: 10,
      deck: 'CUSTOM',
      cards: ['XS', 'M', 'XL', '?'],
    });
  });

  it('hides cached results and mutation controls when access is revoked', async () => {
    const view = mount({
      data: {
        ...initial,
        status: 'REVEALED',
        canVote: false,
        rounds: [result],
      },
    });
    await screen.findByRole('table');
    view.failReads();
    await act(async () => {
      await view.client.invalidateQueries({ queryKey: pokerKey(1) });
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load planning poker',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Save agreed estimate' }),
    ).not.toBeInTheDocument();
  });

  it('renders the no-project state without loading a session', async () => {
    const { container } = renderWithProviders(
      <PokerView project={undefined} />,
    );
    expect(
      screen.getByRole('heading', {
        name: 'Choose or create a project to estimate stories.',
      }),
    ).toBeVisible();
    expect(
      within(container).queryByRole('button', { name: 'Start a session' }),
    ).not.toBeInTheDocument();
    await expectNoSeriousA11yViolations(container);
  });
});
