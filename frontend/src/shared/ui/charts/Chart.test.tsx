import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import {
  LineChart,
  BarChart,
  ScatterChart,
  CumulativeFlowChart,
} from './Chart';
import { Histogram } from './Histogram';
import { histogram } from './chartMath';
import { LanguageScope } from '../../i18n/LanguageProvider';
const data = [
  { label: 'Monday', values: [4, 1] },
  { label: 'Tuesday', values: [2, 3] },
];
describe('accessible chart data', () => {
  it.each([LineChart, BarChart, ScatterChart, CumulativeFlowChart])(
    'renders SVG, complete table data and keyboard data selector',
    (Component) => {
      render(
        <Component title="Delivery" data={data} series={['Open', 'Done']} />,
      );
      expect(screen.getByRole('img', { name: 'Delivery' })).toBeInTheDocument();
      fireEvent.click(screen.getByText('View data as a table'));
      const table = screen.getByRole('table', { name: 'Delivery' });
      expect(
        within(table).getByRole('row', { name: 'Monday 4 1' }),
      ).toBeInTheDocument();
      expect(
        within(table).getByRole('row', { name: 'Tuesday 2 3' }),
      ).toBeInTheDocument();
      const slider = screen.getByRole('slider', { name: 'Explore chart data' });
      expect(slider).toHaveAttribute(
        'aria-valuetext',
        'Monday: Open 4; Done 1',
      );
      fireEvent.change(slider, { target: { value: '1' } });
      expect(slider).toHaveAttribute(
        'aria-valuetext',
        'Tuesday: Open 2; Done 3',
      );
    },
  );
  it('keeps empty/single/zero/negative observations valid and localizes data controls', () => {
    const { rerender } = render(
      <LineChart title="Trend" data={[]} series={['Points']} />,
    );
    expect(screen.getByText('No data to display')).toBeInTheDocument();
    rerender(
      <LanguageScope language="SV">
        <LineChart
          title="Trend"
          data={[
            { label: 'Idag', values: [0] },
            { label: 'Igår', values: [-2] },
          ]}
          series={['Poäng']}
        />
      </LanguageScope>,
    );
    expect(
      screen.getByRole('slider', { name: 'Utforska diagramdata' }),
    ).toHaveAttribute('aria-valuetext', 'Idag: Poäng 0');
    rerender(
      <LineChart
        title="Trend"
        data={[{ label: 'Only', values: [0] }]}
        series={['Points']}
      />,
    );
    expect(screen.getByRole('slider')).toHaveAttribute('max', '0');
  });
  it('aligns scatter labels with their numeric x positions when intervals are unequal', () => {
    render(
      <ScatterChart
        title="Uneven intervals"
        data={[
          { label: 'A', x: 0, values: [1] },
          { label: 'B', x: 1, values: [2] },
          { label: 'C', x: 10, values: [3] },
        ]}
        series={['Points']}
      />,
    );
    const plot = screen.getByRole('img', { name: 'Uneven intervals' });
    const label = within(plot).getByText('B');
    const point = plot.querySelectorAll('circle')[1];
    expect(Number(label.getAttribute('x'))).toBeCloseTo(120.8);
    expect(label.getAttribute('x')).toBe(point?.getAttribute('cx'));
  });
  it('histogram includes each finite sample exactly once, including max and equal observations', () => {
    for (const samples of [
      [1, 2, 3, 4, 5, 6],
      [7, 7, 7],
      [0, -2, 3, NaN, Infinity],
    ]) {
      expect(
        histogram(samples).reduce((sum, row) => sum + (row.values[0] ?? 0), 0),
      ).toBe(samples.filter(Number.isFinite).length);
    }
    render(<Histogram title="Cycle times" samples={[1, 1, 2, 3]} />);
    expect(
      screen.getByRole('img', { name: 'Cycle times' }),
    ).toBeInTheDocument();
  });
});
