import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LanguageScope } from '../../shared/i18n/LanguageProvider';
import { renderWithProviders } from '../../test/render';
import { api } from '../../api/client';
import { ActivityTimeline } from './ActivityTimeline';
import { OverviewView } from './OverviewView';

vi.mock('../../api/client', () => ({ api: vi.fn() }));

const event = {
  id: 1,
  eventType: 'PROJECT_CREATED',
  summary: 'Created project “Mobilkontroll”.',
  actorEmail: 'owner@example.test',
  createdAt: '2026-11-15T00:00:00Z',
};

describe('Activity presentation', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  it('switches the timeline language without changing stored or custom summaries', () => {
    const custom = {
      ...event,
      id: 2,
      summary: 'Customer approved the release.',
    };
    const props = {
      projectId: 1,
      items: [event, custom],
      canManage: false,
      onChanged: vi.fn(),
    };
    const { rerender } = renderWithProviders(
      <LanguageScope language="SV">
        <ActivityTimeline {...props} />
      </LanguageScope>,
    );
    expect(
      screen.getByRole('heading', {
        name: 'Skapade projektet “Mobilkontroll”.',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: custom.summary }),
    ).toBeInTheDocument();

    rerender(
      <LanguageScope language="EN">
        <ActivityTimeline {...props} />
      </LanguageScope>,
    );
    expect(
      screen.getByRole('heading', { name: event.summary }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Skapade projektet “Mobilkontroll”.'),
    ).not.toBeInTheDocument();
  });

  it('uses the same translation for recent activity on the overview', async () => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path.endsWith('/activity')) return [event];
      if (path.endsWith('/metrics')) return {};
      return [];
    });
    const props = {
      project: {
        id: 1,
        name: 'Mobilkontroll',
        description: '',
        ownerEmail: 'owner@example.test',
      },
      onNavigate: vi.fn(),
      onNewTask: vi.fn(),
    };
    const { rerender } = renderWithProviders(
      <LanguageScope language="SV">
        <OverviewView {...props} />
      </LanguageScope>,
    );
    expect(
      await screen.findByText('Skapade projektet “Mobilkontroll”.'),
    ).toBeInTheDocument();

    rerender(
      <LanguageScope language="EN">
        <OverviewView {...props} />
      </LanguageScope>,
    );
    expect(await screen.findByText(event.summary)).toBeInTheDocument();
  });
});
