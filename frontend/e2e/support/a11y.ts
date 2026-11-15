import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const blockingImpacts = new Set(['serious', 'critical']);

/**
 * Runs axe-core against the current page state and fails on serious or
 * critical WCAG 2.2 A/AA findings. Minor and moderate findings are attached to
 * the report for review but do not fail the build.
 */
export async function expectAccessible(
  page: Page,
  label: string,
): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  // Guards against a scan that silently did not run, for example if a CSP change blocks axe.
  expect(
    results.passes.length,
    'axe evaluated rules on the page',
  ).toBeGreaterThan(0);
  await test.info().attach(`axe-${label}.json`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: 'application/json',
  });
  const blocking = results.violations
    .filter((violation) => blockingImpacts.has(violation.impact ?? ''))
    .map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      targets: violation.nodes.map((node) => node.target.join(' ')),
    }));
  expect(blocking, `Serious or critical axe findings on ${label}`).toEqual([]);
}
