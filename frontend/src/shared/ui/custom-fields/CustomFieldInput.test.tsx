import { fireEvent, screen } from '@testing-library/react';
import { useState } from 'react';
import { expect, it } from 'vitest';
import type {
  CustomFieldDefinition,
  CustomFieldKind,
} from '../../../api/contracts';
import { renderWithProviders } from '../../../test/render';
import { CustomFieldInput } from './CustomFieldInput';

function Editor({
  kind,
  disabled = false,
}: {
  kind: CustomFieldKind;
  disabled?: boolean;
}) {
  const [value, setValue] = useState('');
  const field: CustomFieldDefinition = {
    id: 1,
    projectId: 7,
    name: 'Värde',
    kind,
    options: ['Redo', 'Väntar'],
    version: 0,
  };
  return (
    <CustomFieldInput
      field={field}
      value={value}
      onChange={setValue}
      members={[{ id: 42, email: 'member@example.test', role: 'Member' }]}
      disabled={disabled}
    />
  );
}

it('preserves large exact decimals and accepts a Swedish decimal separator', () => {
  renderWithProviders(<Editor kind="NUMBER" />, { language: 'SV' });
  const input = screen.getByRole('textbox', { name: 'Värde' });
  fireEvent.change(input, { target: { value: '9007199254740993,123456' } });
  expect(input).toHaveValue('9007199254740993.123456');
  expect(input).toHaveAccessibleDescription(/exakta precision/);
});
it('stores member identities rather than emails and offers an explicit unset choice', () => {
  renderWithProviders(<Editor kind="USER" />, { language: 'SV' });
  const input = screen.getByRole('combobox', { name: 'Värde' });
  fireEvent.change(input, { target: { value: '42' } });
  expect(input).toHaveValue('42');
  expect(
    screen.getByRole('option', { name: 'member@example.test' }),
  ).toHaveAttribute('value', '42');
  fireEvent.change(input, { target: { value: '' } });
  expect(input).toHaveValue('');
});
it('disables the date text field and calendar action together', () => {
  renderWithProviders(<Editor kind="DATE" disabled />, { language: 'EN' });
  expect(screen.getByRole('textbox', { name: 'Värde' })).toBeDisabled();
  expect(screen.getByRole('button')).toBeDisabled();
});
