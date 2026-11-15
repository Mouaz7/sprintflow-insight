import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PresenceAvatars } from './PresenceAvatars';

const viewers = [
  { userId: 1, name: 'Leo' },
  { userId: 2, name: 'Ada Lovelace' },
  { userId: 3, name: '' },
  { userId: 4, name: 'Bo' },
  { userId: 5, name: 'Cy' },
  { userId: 6, name: 'Di' },
];

describe('PresenceAvatars', () => {
  it('shows the other viewers with initials and a count', () => {
    const { container } = render(
      <PresenceAvatars
        viewers={viewers}
        selfId={1}
        status="live"
        language="EN"
      />,
    );
    expect(
      screen.getByRole('group', {
        name: 'Viewing now: Ada Lovelace, Team member, Bo, Cy, Di',
      }),
    ).toBeInTheDocument();
    const avatars = [...container.querySelectorAll('.presence-avatar')].map(
      (node) => node.textContent,
    );
    expect(avatars).toEqual(['AL', 'TM', 'B', 'C', '+1']);
  });

  it('says when updates are live or reconnecting', () => {
    const { rerender } = render(
      <PresenceAvatars viewers={[]} selfId={1} status="live" language="SV" />,
    );
    expect(screen.getByRole('group')).toHaveAccessibleName(
      'Liveuppdateringar på',
    );
    rerender(
      <PresenceAvatars
        viewers={[]}
        selfId={1}
        status="offline"
        language="EN"
      />,
    );
    expect(screen.getByRole('group')).toHaveAccessibleName(
      'Live updates reconnecting',
    );
  });
});
