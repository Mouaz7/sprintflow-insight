import type { Meta, StoryObj } from '@storybook/react-vite';
import { EmptyState, ErrorState, Skeleton } from '../States';
const meta = {
  title: 'Design system/States',
  component: EmptyState,
  args: {
    title: 'No work yet',
    description: 'Create a task to plan the next delivery.',
  },
} satisfies Meta<typeof EmptyState>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Empty: Story = {};
export const Loading: Story = {
  render: () => <Skeleton label="Loading tasks…" />,
};
export const Failure: Story = {
  render: () => (
    <ErrorState detail="The server could not be reached." onRetry={() => {}} />
  ),
};
