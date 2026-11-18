import { render, screen, fireEvent } from '@testing-library/react';
import { it, expect, vi } from 'vitest';
import { EmptyState, ErrorState, Skeleton } from './States';
it('communicates empty, loading and recoverable error states', () => {
  const { rerender } = render(
    <EmptyState title="No tasks" description="Start your plan." />,
  );
  expect(screen.getByRole('heading', { name: 'No tasks' })).toBeInTheDocument();
  rerender(<Skeleton label="Loading tasks…" />);
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true');
  const retry = vi.fn();
  rerender(<ErrorState detail="Connection lost" onRetry={retry} />);
  expect(screen.getByRole('alert')).toHaveTextContent('Connection lost');
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(retry).toHaveBeenCalledOnce();
});
