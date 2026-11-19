import { useState } from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { HttpResponse } from 'msw';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { MentionInput } from './MentionInput';
function Editor({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <MentionInput
      projectId={1}
      value={value}
      onChange={setValue}
      label="Comment"
    />
  );
}
function members(fail = false) {
  mockApi((path) => {
    if (path.endsWith('/members'))
      return fail
        ? HttpResponse.json({}, { status: 403 })
        : [
            { id: 1, email: 'anna@example.test', role: 'Member' },
            { id: 2, email: 'annika@example.test', role: 'Viewer' },
          ];
    throw new Error(path);
  });
}
it('offers project members and inserts an unambiguous address using only the keyboard', async () => {
  members();
  const keyboard = userEvent.setup();
  const view = renderWithProviders(<Editor />);
  const input = screen.getByRole('textbox', { name: 'Comment' });
  await keyboard.type(input, 'Thanks @ann');
  const option = await screen.findByRole('button', {
    name: 'anna@example.test',
  });
  await keyboard.keyboard('{ArrowDown}');
  expect(option).toHaveFocus();
  await keyboard.keyboard('{Enter}');
  await waitFor(() => expect(input).toHaveValue('Thanks @anna@example.test '));
  await waitFor(() => expect(input).toHaveFocus());
  expect(
    screen.queryByRole('group', { name: 'Mention suggestions' }),
  ).not.toBeInTheDocument();
  await expectNoSeriousA11yViolations(view.container);
});
it('replaces only the handle at the caret and preserves the rest of the draft', async () => {
  members();
  const keyboard = userEvent.setup();
  renderWithProviders(<Editor initial="Hi @ann, please check this." />);
  const input = screen.getByRole('textbox', {
    name: 'Comment',
  }) as HTMLTextAreaElement;
  input.focus();
  input.setSelectionRange(7, 7);
  fireEvent.select(input);
  await keyboard.click(
    await screen.findByRole('button', { name: 'anna@example.test' }),
  );
  expect(input).toHaveValue('Hi @anna@example.test , please check this.');
});
it('retains text after denied member reads', async () => {
  members(true);
  const keyboard = userEvent.setup();
  renderWithProviders(<Editor />);
  const input = screen.getByRole('textbox', { name: 'Comment' });
  await keyboard.type(input, 'Hello @ann');
  expect(
    await screen.findByText(
      'Could not load members. You can still type a full @email address.',
    ),
  ).toBeVisible();
  expect(input).toHaveValue('Hello @ann');
});
it('does not truncate a draft to insert a mention past the limit', async () => {
  members();
  const keyboard = userEvent.setup();
  const initial = 'x'.repeat(990) + ' @ann';
  renderWithProviders(<Editor initial={initial} />);
  await keyboard.click(
    await screen.findByRole('button', { name: 'anna@example.test' }),
  );
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'exceed the comment limit',
  );
  expect(screen.getByRole('textbox', { name: 'Comment' })).toHaveValue(initial);
});
