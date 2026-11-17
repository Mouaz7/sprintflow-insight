# ADR 0018: Shared design foundations and accessible SVG charts

Status: accepted for A7.

The former 2988-line `design-system.css` is replaced by theme tokens, common controls, base rules and styles beside each feature. `src/styles.css` is a short import manifest: its order preserves the existing cascade. All application styles share the `design` layer, and the last `accessibility` layer supplies hidden and reduced-motion rules without `!important`. Equivalent duplicate selectors are merged. Responsive custom-media definitions live in `shared/styles/breakpoints.css`; the build resolves them with PostCSS Custom Media and Global Data, so browsers receive ordinary supported media queries.

Inter and Manrope variable fonts are served from pinned Fontsource packages with their OFL licenses. Only Latin/Latin Extended subsets are declared, with `font-display: swap`, Unicode ranges and local metric-adjusted fallbacks. Text no longer waits for a render-blocking Google Fonts stylesheet; the production font files share the application's cache policy.

`EmptyState`, `Skeleton` and `ErrorState` provide reusable semantic empty, loading and recoverable-error states. Metrics uses them directly and route loading uses Skeleton. Empty states retain `role=status`; a regression test caught the lost announcement during extraction.

The shared chart family implements line, grouped bar, scatter, histogram and cumulative flow as SVG without an external chart runtime. Every chart has a named figure, visible legend, native keyboard-operated data slider and the complete data as an HTML table. Arrow keys/Home/End select a data point and expose all series values through `aria-valuetext` and a live output; users need not distinguish colour alone. Burndown uses the same line component. Zero, negative, single-point and empty series have explicit handling; histogram bins include the maximum exactly once. Chart inputs must be finite and each row must match the declared series.

Storybook is a separate development catalogue, not part of the application bundle. Every chart/state story has Playwright PNG baselines in light and dark themes. Visual tests also exercise native keyboard navigation, table alternatives, page errors and axe WCAG 2.2 A/AA checks. Baseline updates require review of the images; CI compares existing snapshots and never updates them automatically. These component tests complement the application's real-backend E2E suite rather than replacing it.

The docgen plugin's transitive `glob` is pinned to 14.0.6 to address GHSA-3g3m-2fjr-6pw4 and its vulnerable minimatch dependency. Storybook production build verifies compatibility with the patched API; no scanner ignore is introduced.
