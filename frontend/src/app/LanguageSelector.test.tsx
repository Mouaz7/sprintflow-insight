import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LanguageSelector } from './LanguageSelector';

describe('Language selector', () => {
  it('retains native select semantics and passes the selected language', () => {
    const onChange = vi.fn();
    render(<LanguageSelector language="EN" onChange={onChange} />);
    const select = screen.getByRole('combobox', { name: 'Language' });
    expect(select).toHaveValue('EN');
    fireEvent.change(select, { target: { value: 'SV' } });
    expect(onChange).toHaveBeenCalledWith('SV');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
  it('updates its name and tooltip when Swedish is selected', () => {
    const props = { onChange: vi.fn() };
    const view = render(<LanguageSelector {...props} language="EN" />);
    view.rerender(<LanguageSelector {...props} language="SV" />);
    const select = screen.getByRole('combobox', { name: 'Språk' });
    expect(select).toHaveValue('SV');
    expect(select).toHaveAttribute('title', 'Byt språk: Svenska');
  });
});
