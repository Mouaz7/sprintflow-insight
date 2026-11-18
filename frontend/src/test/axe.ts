import axe from 'axe-core';
import { expect } from 'vitest';

/**
 * Runs axe against WCAG 2.0–2.2 A/AA rules and fails on serious or critical
 * findings. jsdom has no layout, so contrast and target-size rules that need
 * rendering are verified in the browser checks instead.
 */
export async function expectNoSeriousA11yViolations(container: Element) {
  const results = await axe.run(container, {
    runOnly: {
      type: 'tag',
      values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'],
    },
    rules: { 'color-contrast': { enabled: false } },
  });
  const serious = results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map(
      (v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
    );
  expect(serious).toEqual([]);
}
