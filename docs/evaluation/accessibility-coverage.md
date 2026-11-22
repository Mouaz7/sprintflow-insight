# RQ5 accessibility coverage

## Scope

This review covers the packaged SprintFlow revision `a05e41b` and four study tasks in disposable projects, with follow-up browser and screen-reader checks on `0362f83`, `cdf9a16` and `a993c83`. The sample includes creating and moving a task, finding Metrics, and resolving a queued edit after reconnecting. The browser suite also exercises sign-in, a populated Flow analysis report, a read-only project, offline collaborative text and selected administrative states. Prior Chromium and Orca observations and their revisions are recorded in the evaluation report. The assessment target is WCAG 2.2 A and AA. [Evaluation report](report.md) · [WCAG 2.2](https://www.w3.org/TR/WCAG22/) · [WCAG-EM 2.0](https://www.w3.org/TR/wcag-em-2/).

The matrix records evidence for the inspected states. A product-wide AA assessment requires additional coverage for areas such as installation, external identity providers, other export formats, and administrative states outside the sample.

## Browser results

The six existing Playwright cases exercised sign-in and sign-out, a project with a new task, a populated report, a read-only project, replay of queued edits, and collaborative text after offline use. The serial comparison used the packaged app with PostgreSQL 16.15 on a loopback HTTP origin and the local Compose cookie setting. The WebKit build is Playwright's Linux build.

| Engine | Serial comparison | Axe scans from the successful cases |
| --- | --- | --- |
| Chromium 159 | 6 passed | 15 |
| Firefox 159 | 6 passed | 15 |
| WebKit 27.2 | 5 passed initially. The read-only-project case completed on an isolated rerun. | 15 |

The 45 completed scans reported zero violations for the project's selected WCAG axe rules. The initial run combined the secure-cookie default with HTTP and WebKit stayed at sign-in. The Firefox report case reported a browser-process crash in that run and completed in the serial comparison. The WebKit read-only-project trace hit the test deadline while opening a new viewer page. [Browser suite and axe rules](../testing.md).

WebKit also completed sign-in, the project workflow and both offline cases on a loopback HTTPS instance with the secure-session setting. The queued-edits case reached the test deadline on the group run and passed on an isolated rerun. This comparison used an isolated self-signed test certificate and the same packaged application.

### Authentication, organization and data management follow-up

Chromium 159 ran five existing cases on `0362f83`. Nine axe scans reported zero violations for the selected rules. These observations extend the sample to two additional authentication methods, two organization views and the account export.

| Case | Observed result | Axe states |
| --- | --- | --- |
| Browser passkey | Registered a virtual passkey, signed out and signed back in with authenticator user verification. | Passkey settings |
| Two-factor authentication | Enrolled with an authenticator and completed sign-in with a recovery code. | Security settings and verification challenge |
| Organization | Created an organization, accepted an invitation and applied a scoped project template. | Organization projects and team roster |
| CSV import and account export | Mapped statuses, confirmed the import, verified the audit history and downloaded a ZIP whose manifest listed the imported tasks. | Import preview, audit history and account export |
| Jira import | Mapped historical data in the Swedish dark mobile interface. | Import preview |

To repeat these cases, set the local app's `SPRINTFLOW_PUBLIC_URL` and the browser suite's `E2E_BASE_URL` to the same `http://localhost:<port>` origin. Run `passkeys.e2e.ts two-factor.e2e.ts organizations.e2e.ts import-export.e2e.ts` with `--project=chromium`. [Browser verification](../testing.md).

### Reproduce the browser comparison

Start a disposable local app using the Compose cookie setting for HTTP or the regular secure-cookie setting on HTTPS. Install the test project's locked browser dependencies from `frontend/e2e` and add this temporary configuration beside `playwright.config.ts` as `playwright.rq5.local.config.ts`. [Browser verification](../testing.md).

```typescript
import { defineConfig, devices } from '@playwright/test';
import base from './playwright.config';

export default defineConfig({
  ...base,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  use: {
    ...base.use,
    ignoreHTTPSErrors: process.env.RQ5_LOCAL_TEST_CERT === '1',
  },
  projects: [
    { name: 'chromium', use: devices['Desktop Chrome'] },
    { name: 'firefox', use: devices['Desktop Firefox'] },
    { name: 'webkit', use: devices['Desktop Safari'] },
  ].map((project) => ({
    ...project,
    use: { ...project.use, viewport: { width: 1440, height: 1000 } },
  })),
});
```

```bash
npm ci
npx playwright install chromium firefox webkit
E2E_BASE_URL=http://127.0.0.1:8081 npx playwright test \
  --config playwright.rq5.local.config.ts auth.e2e.ts workspace.e2e.ts \
  offline.e2e.ts flow.e2e.ts
```

For the additional loopback HTTPS check, set `E2E_BASE_URL` to the disposable TLS origin and use only `auth.e2e.ts`, `workspace.e2e.ts` and `offline.e2e.ts` with `--project=webkit` at the end. Use `RQ5_LOCAL_TEST_CERT=1` with the generated test certificate.

## Character shortcuts (2.1.4)

In the baseline revision `a05e41b`, pressing `c` with the workspace main landmark focused opened the New task dialog. The document-level listener used a fixed map for `c`, `/`, `?` and sequences beginning with `g`. The shortcut dialog offered a reference list. [W3C guidance on character shortcuts](https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts.html).

Revision `0362f83` adds a labelled checkbox in the Keyboard shortcuts dialog. The packaged-app regression used Ctrl+K, Enter, Tab and Space to turn the shortcuts off and Escape to close the dialog. After a reload the checkbox was still unchecked. Pressing `c`, `/`, `?` and the `g` then `m` sequence kept the task view open. After re-enabling, `g` then `m` opened Metrics. The three search-suite cases passed on each of Chromium 159, Firefox 159 and WebKit 27.2. The nine axe scans from those cases reported zero violations for the selected rules. Add `search.e2e.ts` to the browser comparison command to repeat the test. [Code change](https://github.com/Mouaz7/sprintflow-insight/pull/94).

Orca 46.1 on Chromium read the checkbox label, role and checked state when Tab moved focus to it. Chromium emitted an AT-SPI checked-state event when Space cleared the checkbox. The targeted check kept keyboard focus on the checkbox until Escape closed the dialog.

In a follow-up using a 320 CSS-pixel viewport, the Swedish dialog fit within the page width with forced colors active. Its checkbox had a 2 CSS-pixel focus outline, and clicking the associated 254 × 42 CSS-pixel label changed the checkbox. Orca announced the unchecked state after Tab returned focus to the checkbox.

## Control contrast (1.4.11)

The Custom field select inside Search inherited the dark header colors. Its border and fill contrast was low in both themes. In dark mode the border also had low contrast with the surrounding dialog. The Project and Language selects in the header had low border contrast with both adjacent colors, and the light-theme focus outline had low contrast with the header. The follow-up uses palette theme colors for the Custom field select, strengthens the shared light-theme input border and adjusts the header borders and focus outline. [Contrast fix](https://github.com/Mouaz7/sprintflow-insight/pull/95) · [Non-text contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html).

Chromium 159, Firefox 159 and WebKit 27.2 agreed on the computed colors for each of the packaged revisions `cdf9a16` and `a993c83`. With a project selected, open Search and inspect the Custom field select in both themes. Compare its `border-top-color` with its `background-color` and the dialog background. For Project and Language, compare the border with the field fill and header background. Move focus to each header field and compare its `outline-color` with the header background. The table reports the WCAG sRGB ratios, rounded for display. The search regression asserts the exact ratio before rounding for both sides of the filter border. All three search cases passed in each engine and the nine axe scans reported zero findings for their selected rules. [Browser verification](../testing.md).

| Component and comparison | Theme | Baseline | Follow-up |
| --- | --- | ---: | ---: |
| Custom field border vs dialog | Light | 5.33:1 | 3.29:1 |
| Custom field border vs fill | Light | 1.83:1 | 3.74:1 |
| Custom field border vs dialog | Dark | 2.02:1 | 3.22:1 |
| Custom field border vs fill | Dark | 1.83:1 | 4.11:1 |
| Project and Language borders vs adjacent colors, lower ratio | Both | 1.83:1 | 6.79:1 |
| Project and Language focus outlines vs header | Light | 2.68:1 | 4.71:1 |
| Project and Language focus outlines vs header | Dark | 4.71:1 | 4.71:1 |

## Session timeout (2.2.1)

The application configures a 30-minute inactivity timeout. A disposable Chromium run of `cdf9a16` set both `SPRING_SESSION_TIMEOUT` and `SERVER_SERVLET_SESSION_TIMEOUT` to `12s` to exercise the expiry path. With an unsaved task title in the editor, the page showed no expiry warning during 16 seconds of observation. The session endpoint then returned 401. Submitting the form returned 403 and prompted a refresh. The title stayed in the open editor. Reloading, signing in again and reopening the editor produced an empty title. This test records the editor's expiry and recovery behavior at the shortened interval. The ordinary inactivity interval and other timed paths need separate checks. [Session configuration](../../backend/src/main/resources/application.properties) · [W3C guidance on timing](https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html).

## Automatic updates and shared help

On `cdf9a16`, two Chromium contexts opened the same project with separate owner and member accounts. The owner focused the task search field. The member moved a task from To Do to In Progress and then to Review. Both changes appeared on the owner's board within one second of the member's action. The task search field retained focus throughout. The inspected board and Profile → Settings exposed no control to pause or set the rate of automatic board updates. This is a remaining 2.2.2 finding for the sampled board. Notification polling and collaborative-text updates require assessment in their own states. [W3C guidance on automatic updates](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html).

Tasks, Metrics, Flow and Profile placed Search in the same header position in document order, after Project and before Language. The browser opened Keyboard shortcuts from that command on each route. Hovering and focusing Search kept the command dialog closed until Enter. The introduction is available from the empty Overview and from the profile Settings tab. The help check records the shared Search placement and those two introduction entry points. [W3C guidance on hover and focus](https://www.w3.org/WAI/WCAG22/Understanding/content-on-hover-or-focus.html) · [W3C guidance on consistent help](https://www.w3.org/WAI/WCAG22/Understanding/consistent-help.html).

## Criterion matrix

| Criterion | Level | Observation or remaining check |
| --- | --- | --- |
| 1.1.1 | A | The populated Cumulative flow figure has a name and a table offering the measured values. Other graphics need individual review. |
| 1.2.1, 1.2.2, 1.2.3, 1.2.4, 1.2.5 | A, A, A, AA, AA | The inspected task, report and authentication states contain text, controls and charts. The sample has no prerecorded or live media. |
| 1.3.1 | A | The task editor exposes a named dialog and labelled inputs. The chart alternate table exposes column headers and data rows. |
| 1.3.2 | A | Review the complete reading sequence in the board and the populated report, including their narrower layouts. |
| 1.3.3 | A | The selected instructions use visible task and control names to identify each action. Inspect secondary instructions and help text for reliance on shape or location. |
| 1.3.4 | AA | The Metrics scope control was usable in portrait and at 780 × 360 in landscape. Broader interaction coverage in landscape remains open. |
| 1.3.5 | AA | The password sign-in inputs expose `email` and `current-password` autocomplete purposes. |
| 1.4.1 | A | Board columns identify statuses in text and conflict rows describe the issue and each choice in text. Review chart series and secondary indicators separately. |
| 1.4.2 | A | The inspected views produced no page-owned autoplay audio. Orca speech came from assistive technology. |
| 1.4.3 | AA | The targeted axe scans cover text contrast in the inspected light and dark states. Text over backgrounds that need visual inspection remains open. |
| 1.4.4 | AA | All four study tasks completed with Chromium set to 200% browser zoom. |
| 1.4.5 | AA | Inspected controls and report values are rendered as text or supplied in a text table. Visual assets elsewhere need review. |
| 1.4.10 | AA | Tasks, Organizations, expanded outgoing webhooks and Metrics fit the measured 320 CSS-pixel viewport. Complex content and individual controls need additional reflow checks. |
| 1.4.11 | AA | The follow-up measured at least 3:1 for the sampled Project, Language and Custom field boundaries and header select focus outlines in both themes. Chart marks and other controls or states need individual review. |
| 1.4.12 | AA | All four study tasks completed with the spacing overrides maintained on dynamically rendered content. |
| 1.4.13 | AA | Hovering or focusing Search kept the dialog closed on four sampled routes. The dialog opened by activation. Review other custom hover and focus disclosures for dismissal, pointer access and persistence. |
| 2.1.1 | A | Keyboard operation reached task creation, task movement, Metrics and conflict resolution. |
| 2.1.2 | A | Escape closed the Comments panel and restored focus to its opening control. |
| 2.1.4 | A | The baseline allowed `c` to open New task from the main landmark. In the follow-up, the workspace shortcut map could be turned off with the keyboard and stayed off after a reload in all three engines. |
| 2.2.1 | A | With a 12-second test session the task editor showed no observed expiry warning or extension action. The draft was unavailable after reload and sign-in. Check the default 30-minute interval, other time limits and offline states separately. |
| 2.2.2 | A | Two peer task moves updated the owner's board automatically. The sampled board and settings offered no pause or update frequency control. Other automatic content still needs review. |
| 2.3.1 | A | Review the motion and feedback states for flashing. The inspected studies used reduced-motion settings in the browser suite. |
| 2.4.1 | A | Tab reached Skip to content. Enter moved focus to the workspace main landmark. |
| 2.4.2 | A | Chromium verified localized tab titles for the 21 canonical workspace views in English and Swedish. The cached Tasks view retained its title after reloading offline. |
| 2.4.3 | A | Focus arrived at the Metrics heading after navigation and moved to the next conflict action or the main landmark after resolution. |
| 2.4.4 | A | The sampled navigation uses destination labels such as Tasks and Metrics. Review secondary links and their surrounding context. |
| 2.4.5 | AA | Metrics opened both from the primary navigation and from Go to metrics in the command palette. |
| 2.4.6 | AA | Tasks and Metrics expose descriptive headings, and the title and move controls expose descriptive names. |
| 2.4.7 | AA | A visible focus indicator was observed on the skip link and conflict choice. In Chromium forced colors, New task had a 3 CSS-pixel focus outline and the character shortcut checkbox had a 2 CSS-pixel focus outline. |
| 2.4.11 | AA | The center of each of the five measured controls remained visible when focused at 360 CSS pixels. Other viewport and overlay combinations remain open. |
| 2.5.1 | A | The selected task flow uses ordinary buttons and lists. Review custom chart interactions and any path gestures separately. |
| 2.5.2 | A | The New task button opened the dialog on pointer release over the control. Moving away before release cancelled the action. |
| 2.5.3 | A | The exposed names of the selected task and navigation controls include their visible labels. Expand checks to icon-text combinations and secondary actions. |
| 2.5.4 | A | The sampled flow uses on-screen controls without device-motion or camera gestures. |
| 2.5.7 | AA | The Move to select offers an alternative to dragging a task card. The browser verified the persisted task status after the selection. |
| 2.5.8 | AA | The five measured mobile controls were each 44 CSS pixels high and at least 44 pixels wide. The shortcut checkbox's clickable Swedish label measured 254 × 42 CSS pixels at a 320 CSS-pixel viewport. Other targets and spacing exceptions need review. |
| 3.1.1 | A | Switching to Swedish updated visible workspace headings and the document's `lang` attribute to `sv`. |
| 3.1.2 | AA | Review passages that differ from the page language and distinguish ordinary technical terms from complete passages. |
| 3.2.1 | A | Focusing the Metrics link kept the task route. Enter opened Metrics. |
| 3.2.2 | A | Selecting a new status updated the saved task while keeping the user on Tasks. Other inputs need review. |
| 3.2.3 | AA | The 19 primary navigation links appeared in the same sequence in the sampled Tasks and Metrics views. |
| 3.2.4 | AA | The same task and metrics destinations use the same names in the sampled navigation. Broader control identification remains open. |
| 3.2.6 | A | Search opened Keyboard shortcuts from the same header position on Tasks, Metrics, Flow and Profile. The introduction had entry points in the empty Overview and in profile Settings. Additional shared help needs review. |
| 3.3.1 | A | Submitting an empty task title focused the invalid field and exposed the browser's validation message. |
| 3.3.2 | A | The sampled title, email, password and move controls have visible labels. |
| 3.3.3 | AA | The required-title message identifies the value to supply. Additional validation and error paths need review. |
| 3.3.4 | AA | The conflict flow pauses before applying the stale operation and offers a choice. Review other relevant changes and deletions separately. |
| 3.3.7 | A | The queued edit can be submitted again from Redo my change using its stored value. |
| 3.3.8 | AA | Clipboard pasting completed password sign-in. Follow-up cases completed passkey sign-in and used a recovery code to answer the two-factor challenge. Remaining authentication steps need separate coverage. |
| 4.1.2 | A | The sampled editor, status selector and conflict buttons expose roles and names. Orca read the name, role and checked state of the shortcut checkbox. Chromium exposed the state change through AT-SPI. |
| 4.1.3 | AA | Orca announced the offline state and the number of conflicts needing review without moving focus to the message. |
