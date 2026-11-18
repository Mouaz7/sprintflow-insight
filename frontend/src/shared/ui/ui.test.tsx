import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders as render } from '../../test/render';
import { ConfirmProvider, Drawer, EditorProjectContext } from './ui';

function Editor({ busy = false }: { busy?: boolean }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  return (
    <ConfirmProvider>
      <button onClick={() => setOpen(true)}>Open editor</button>
      {open && (
        <Drawer
          title="Editor"
          onClose={() => setOpen(false)}
          dirty={!!text}
          busy={busy}
        >
          <label>
            Title
            <input
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
          </label>
          <button>Save</button>
        </Drawer>
      )}
    </ConfirmProvider>
  );
}

describe('Form panels', () => {
  it('moves focus into the panel, traps Tab and restores focus after Escape', async () => {
    render(<Editor />);
    const trigger = screen.getByRole('button', { name: 'Open editor' });
    trigger.focus();
    fireEvent.click(trigger);
    const close = screen.getByRole('button', { name: 'Close panel' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(screen.getByRole('button', { name: 'Save' })).toHaveFocus();
    fireEvent.keyDown(document.activeElement!, { key: 'Tab' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(close, { key: 'Escape' });
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(trigger).toHaveFocus();
  });

  it('keeps unsaved contents when discard is cancelled and closes after confirmation', async () => {
    render(<Editor />);
    fireEvent.click(screen.getByRole('button', { name: 'Open editor' }));
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Keep this draft' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Close panel' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('unsaved changes');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() =>
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument(),
    );
    expect(screen.getByLabelText('Title')).toHaveValue('Keep this draft');
    fireEvent.keyDown(screen.getByLabelText('Title'), { key: 'Escape' });
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
  });

  it('prevents closing during a pending save', () => {
    render(<Editor busy />);
    fireEvent.click(screen.getByRole('button', { name: 'Open editor' }));
    expect(screen.getByRole('button', { name: 'Close panel' })).toBeDisabled();
    fireEvent.keyDown(screen.getByLabelText('Title'), { key: 'Escape' });
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-busy', 'true');
  });

  it('uses the saved dark theme for confirmation overlays', async () => {
    localStorage.setItem('sprintflow-theme', 'dark');
    render(<Editor />);
    fireEvent.click(screen.getByRole('button', { name: 'Open editor' }));
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Draft' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Close panel' }));
    expect(
      (await screen.findByRole('alertdialog')).parentElement,
    ).toHaveAttribute('data-theme', 'dark');
    localStorage.clear();
  });
});

it('labels the current project in Swedish inside a project-scoped panel', () => {
  render(
    <EditorProjectContext.Provider value={{ id: 2, name: 'Test' }}>
      <Drawer title="Ny berättelse" onClose={() => {}}>
        <p>Formulär</p>
      </Drawer>
    </EditorProjectContext.Provider>,
    { language: 'SV' },
  );
  expect(
    screen.getByRole('dialog').querySelector('.editor-project'),
  ).toHaveTextContent('Projekt:Test');
});
