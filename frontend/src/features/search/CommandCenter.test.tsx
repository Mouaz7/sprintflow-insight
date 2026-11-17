import {
  act,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/render';
import { api } from '../../api/client';
import { CommandCenter } from './CommandCenter';
import type { SearchHit, SearchPage } from './searchApi';

vi.mock('../../api/client', () => ({ api: vi.fn() }));

function hit(overrides: Partial<SearchHit> = {}): SearchHit {
  return {
    type: 'task',
    id: 13,
    projectId: 7,
    projectKey: 'SF',
    projectName: 'Alpha',
    taskId: 13,
    key: 'SF-12',
    title: 'Fix login redirect',
    titleSegments: [
      { text: 'Fix ', match: false },
      { text: 'login', match: true },
      { text: ' redirect', match: false },
    ],
    snippet: [{ text: '<img src=x onerror=alert(1)>', match: false }],
    exactMatch: false,
    ...overrides,
  };
}

function page(
  items: SearchHit[],
  nextCursor: string | null = null,
): SearchPage {
  return { items, nextCursor };
}

function renderCenter(language: 'EN' | 'SV' = 'EN', canCreateTask = true) {
  const onNavigate = vi.fn();
  const onNewTask = vi.fn();
  const view = renderWithProviders(
    <>
      <input aria-label="Outside field" />
      <CommandCenter
        canCreateTask={canCreateTask}
        onNavigate={onNavigate}
        onNewTask={onNewTask}
      />
    </>,
    { language },
  );
  return { onNavigate, onNewTask, ...view };
}

async function openPalette() {
  fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
  return screen.findByRole('combobox', { name: 'Search or run a command' });
}

describe('CommandCenter', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
    vi.mocked(api).mockResolvedValue(page([]));
  });

  it('opens with Ctrl+K as a modal combobox, focuses the input and closes with Escape', async () => {
    renderCenter();
    const launcher = screen.getByRole('button', { name: 'Search' });
    expect(launcher).toHaveAttribute('title', 'Search and commands (Ctrl+K)');
    launcher.focus();
    const input = await openPalette();
    const dialog = screen.getByRole('dialog', { name: 'Search and commands' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(input).toHaveFocus();
    expect(input).toHaveAttribute('aria-expanded', 'true');
    const listbox = screen.getByRole('listbox');
    expect(input).toHaveAttribute('aria-controls', listbox.id);
    // Without a query the listbox offers the commands, the first one active.
    const options = within(listbox).getAllByRole('option');
    expect(options[0]).toHaveTextContent('Go to overview');
    expect(input).toHaveAttribute('aria-activedescendant', options[0]!.id);

    fireEvent.keyDown(input, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(launcher).toHaveFocus();
    // Ctrl+K toggles; the launcher button opens it too.
    fireEvent.click(launcher);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('searches after a pause, highlights matches as text and navigates to the chosen hit', async () => {
    vi.mocked(api).mockResolvedValue(
      page([
        hit(),
        hit({
          type: 'story',
          id: 4,
          taskId: null,
          key: null,
          title: 'Login story',
          titleSegments: [{ text: 'Login story', match: false }],
          snippet: [],
        }),
      ]),
    );
    const { onNavigate } = renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'login' } });
    expect(screen.getByRole('status')).toHaveTextContent('Searching…');

    const first = await screen.findByRole('option', {
      name: /Fix login redirect/,
    });
    expect(api).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api).mock.calls[0]![0]).toBe('/search?q=login&limit=20');
    expect(screen.getByRole('status')).toHaveTextContent('2 results');
    expect(
      within(first).getByText('login', { selector: 'mark' }),
    ).toBeVisible();
    // Stored text is rendered as text, never as markup.
    expect(first.querySelector('img')).toBeNull();
    expect(first).toHaveTextContent('<img src=x onerror=alert(1)>');
    expect(input).toHaveAttribute('aria-activedescendant', first.id);

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    const story = screen.getByRole('option', { name: /Login story/ });
    expect(story).toHaveAttribute('aria-selected', 'true');
    expect(input).toHaveAttribute('aria-activedescendant', story.id);
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onNavigate).toHaveBeenCalledWith({ view: 'backlog', projectId: 7 });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps read-only search available without offering task creation', async () => {
    const { onNewTask } = renderCenter('EN', false);
    fireEvent.keyDown(document.body, { key: 'c' });
    expect(onNewTask).not.toHaveBeenCalled();
    await openPalette();
    expect(
      screen.queryByRole('option', { name: 'New task' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Go to board' })).toBeVisible();
  });

  it('only sends the latest query while typing', async () => {
    vi.mocked(api).mockResolvedValue(page([hit()]));
    renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'l' } });
    fireEvent.change(input, { target: { value: 'lo' } });
    fireEvent.change(input, { target: { value: 'log' } });
    await screen.findByRole('option', { name: /Fix login redirect/ });
    expect(api).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api).mock.calls[0]![0]).toBe('/search?q=log&limit=20');
  });

  it('aborts an in-flight query and ignores its late response after the text changes', async () => {
    let previousSignal: AbortSignal | undefined;
    let finishPrevious: (value: SearchPage) => void = () => {};
    vi.mocked(api).mockImplementation(async (path, options) => {
      if (path.includes('q=old')) {
        previousSignal = options?.signal ?? undefined;
        return new Promise<SearchPage>((resolve) => {
          finishPrevious = resolve;
        });
      }
      return page([
        hit({
          id: 20,
          title: 'New result',
          titleSegments: [{ text: 'New result', match: true }],
        }),
      ]);
    });
    renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'old' } });
    await waitFor(() => expect(previousSignal).toBeDefined());
    fireEvent.change(input, { target: { value: 'new' } });
    await waitFor(() => expect(previousSignal?.aborted).toBe(true));
    expect(
      await screen.findByRole('option', { name: /New result/ }),
    ).toBeVisible();
    await act(async () =>
      finishPrevious(
        page([
          hit({
            title: 'Old result',
            titleSegments: [{ text: 'Old result', match: true }],
          }),
        ]),
      ),
    );
    expect(
      screen.queryByRole('option', { name: /Old result/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: /New result/ })).toBeVisible();
  });

  it('jumps straight to a task key on Enter, even before results arrive', async () => {
    vi.mocked(api).mockImplementation(async (path) => {
      if (path === '/tasks/by-key/SF-12')
        return { id: 13, projectId: 7, key: 'SF-12' };
      return page([]);
    });
    const { onNavigate } = renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'sf-12' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() =>
      expect(onNavigate).toHaveBeenCalledWith({
        view: 'tasks',
        projectId: 7,
        taskKey: 'SF-12',
      }),
    );
  });

  it('opens the exact match first once results are shown and reports unknown keys', async () => {
    vi.mocked(api).mockResolvedValue(page([hit({ exactMatch: true })]));
    const { onNavigate } = renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'SF-12' } });
    expect(await screen.findByText('Exact match')).toBeInTheDocument();
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onNavigate).toHaveBeenCalledWith({
      view: 'tasks',
      projectId: 7,
      taskKey: 'SF-12',
    });

    vi.mocked(api).mockRejectedValue(new Error('Task not found.'));
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    const again = screen.getByRole('combobox');
    fireEvent.change(again, { target: { value: 'XY-9' } });
    fireEvent.keyDown(again, { key: 'Enter' });
    expect(
      await screen.findByText('No task XY-9 in your projects.'),
    ).toBeInTheDocument();
  });

  it('loads the next page with the cursor and says when nothing matched', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce(page([hit()], 'next-page'))
      .mockResolvedValueOnce(
        page([hit({ id: 14, key: 'SF-13', title: 'Second' })]),
      );
    renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'login' } });
    fireEvent.click(
      await screen.findByRole('option', { name: 'Show more results' }),
    );
    expect(
      await screen.findByRole('option', { name: /SF-13/ }),
    ).toBeInTheDocument();
    expect(vi.mocked(api).mock.calls[1]![0]).toBe(
      '/search?q=login&limit=20&cursor=next-page',
    );
    expect(
      screen.queryByRole('option', { name: 'Show more results' }),
    ).toBeNull();

    vi.mocked(api).mockResolvedValue(page([]));
    fireEvent.change(input, { target: { value: 'nothing here' } });
    expect(
      await screen.findByText('No matches. Try fewer or different words.'),
    ).toBeInTheDocument();
  });

  it('runs commands from the palette and filters them by the query', async () => {
    const { onNavigate, onNewTask } = renderCenter();
    const input = await openPalette();
    fireEvent.change(input, { target: { value: 'board' } });
    fireEvent.click(screen.getByRole('option', { name: 'Go to board' }));
    expect(onNavigate).toHaveBeenCalledWith({ view: 'tasks' });

    openPalette();
    fireEvent.click(screen.getByRole('option', { name: 'New task' }));
    expect(onNewTask).toHaveBeenCalledTimes(1);

    openPalette();
    fireEvent.click(screen.getByRole('option', { name: 'Keyboard shortcuts' }));
    expect(
      await screen.findByRole('dialog', { name: 'Keyboard shortcuts' }),
    ).toBeInTheDocument();
  });

  it('handles g-sequences, c and ? but never while typing or behind a dialog', async () => {
    const { onNavigate, onNewTask } = renderCenter();
    fireEvent.keyDown(document.body, { key: 'g' });
    fireEvent.keyDown(document.body, { key: 'b' });
    expect(onNavigate).toHaveBeenLastCalledWith({ view: 'tasks' });
    fireEvent.keyDown(document.body, { key: 'g' });
    fireEvent.keyDown(document.body, { key: 's' });
    expect(onNavigate).toHaveBeenLastCalledWith({ view: 'sprints' });
    fireEvent.keyDown(document.body, { key: 'c' });
    expect(onNewTask).toHaveBeenCalledTimes(1);

    // Typing in a field is left alone.
    const field = screen.getByRole('textbox', { name: 'Outside field' });
    fireEvent.keyDown(field, { key: 'c' });
    fireEvent.keyDown(field, { key: '?' });
    expect(onNewTask).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).toBeNull();

    // A stale g does not combine with a later key.
    vi.useFakeTimers();
    try {
      fireEvent.keyDown(document.body, { key: 'g' });
      act(() => vi.advanceTimersByTime(1500));
      fireEvent.keyDown(document.body, { key: 'm' });
    } finally {
      vi.useRealTimers();
    }
    expect(onNavigate).toHaveBeenCalledTimes(2);

    fireEvent.keyDown(document.body, { key: '?' });
    const help = await screen.findByRole('dialog', {
      name: 'Keyboard shortcuts',
    });
    expect(help).toHaveAttribute('aria-modal', 'true');
    // Behind the open dialog, plain shortcuts do nothing.
    fireEvent.keyDown(document.body, { key: 'c' });
    expect(onNewTask).toHaveBeenCalledTimes(1);
  });

  it('opens with / and shows Swedish texts', async () => {
    renderCenter('SV');
    fireEvent.keyDown(document.body, { key: '/' });
    expect(
      await screen.findByRole('combobox', {
        name: 'Sök eller kör ett kommando',
      }),
    ).toHaveFocus();
    expect(
      screen.getByRole('option', { name: 'Gå till tavlan' }),
    ).toBeVisible();
  });
});
