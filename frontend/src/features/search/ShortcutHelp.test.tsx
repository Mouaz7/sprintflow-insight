import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ShortcutHelp } from './ShortcutHelp';
import { searchText } from './searchText';

describe('ShortcutHelp', () => {
  it('is a labelled modal dialog that traps focus and closes with Escape', () => {
    const onClose = vi.fn();
    render(
      <>
        <button type="button">Behind</button>
        <ShortcutHelp t={searchText('EN')} onClose={onClose} />
      </>,
    );
    const dialog = screen.getByRole('dialog', { name: 'Keyboard shortcuts' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription(/not typing in a field/);
    const close = screen.getByRole('button', { name: 'Close' });
    expect(close).toHaveFocus();
    // The only focusable control: Tab and Shift+Tab stay on it.
    fireEvent.keyDown(close, { key: 'Tab' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(close).toHaveFocus();
    expect(screen.getByText('Go to board')).toBeInTheDocument();
    expect(screen.getAllByText('then').length).toBeGreaterThan(0);
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('lists the shortcuts in Swedish', () => {
    render(<ShortcutHelp t={searchText('SV')} onClose={vi.fn()} />);
    expect(screen.getByRole('dialog', { name: 'Kortkommandon' })).toBeVisible();
    expect(screen.getByText('Gå till tavlan')).toBeInTheDocument();
    expect(screen.getByText('Visa den här hjälpen')).toBeInTheDocument();
  });
});
