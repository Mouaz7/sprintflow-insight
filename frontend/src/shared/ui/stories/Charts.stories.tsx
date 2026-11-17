import type { Meta, StoryObj } from '@storybook/react-vite';
import {
  LineChart,
  BarChart,
  ScatterChart,
  CumulativeFlowChart,
} from '../charts/Chart';
import { Histogram } from '../charts/Histogram';
const rows = [
  { label: 'Nov 01', values: [8, 2] },
  { label: 'Nov 02', values: [6, 4] },
  { label: 'Nov 03', values: [4, 6] },
  { label: 'Nov 04', values: [2, 8] },
];
const meta = {
  title: 'Design system/Charts',
  component: LineChart,
  args: { title: 'Delivery trend', data: rows, series: ['Open', 'Done'] },
} satisfies Meta<typeof LineChart>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Line: Story = {};
export const Bar: Story = { render: (args) => <BarChart {...args} /> };
export const Scatter: Story = {
  render: (args) => (
    <ScatterChart
      {...args}
      data={rows.map((row, i) => ({ ...row, x: i * 2 + 1 }))}
    />
  ),
};
export const CumulativeFlow: Story = {
  render: (args) => <CumulativeFlowChart {...args} />,
};
export const Distribution: Story = {
  render: () => (
    <Histogram
      title="Cycle time distribution"
      samples={[1, 1, 2, 2, 3, 4, 4, 4, 5, 6, 8, 9, 12]}
    />
  ),
};
export const Empty: Story = { args: { data: [] } };
export const SinglePoint: Story = {
  args: { data: [rows[0] ?? { label: 'Only', values: [0, 0] }] },
};
