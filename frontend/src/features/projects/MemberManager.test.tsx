import { screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import { MemberManager } from './MemberManager';

vi.mock('../../api/client', () => ({ api: vi.fn() }));
const project = { id: 1, name: 'Test', ownerEmail: 'owner@example.com' };
beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockResolvedValue([
    { id: 1, email: 'owner@example.com', role: 'Owner' },
    { id: 2, email: 'admin@example.com', role: 'Admin' },
  ]);
});
it('allows an admin to manage membership while reserving ownership transfer for the owner', async () => {
  renderWithProviders(
    <MemberManager
      project={project}
      userEmail="admin@example.com"
      onChanged={vi.fn()}
    />,
  );
  expect(
    await screen.findByRole('button', { name: 'Add member' }),
  ).toBeEnabled();
  expect(screen.getByRole('button', { name: 'Remove' })).toBeEnabled();
  expect(
    screen.queryByRole('button', { name: 'Make owner' }),
  ).not.toBeInTheDocument();
});
it('shows ownership transfer to the owner', async () => {
  renderWithProviders(
    <MemberManager
      project={project}
      userEmail="owner@example.com"
      onChanged={vi.fn()}
    />,
  );
  expect(
    await screen.findByRole('button', { name: 'Make owner' }),
  ).toBeEnabled();
});
