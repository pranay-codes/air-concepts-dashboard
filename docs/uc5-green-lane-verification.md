# UC5 green-lane verification

## Scope and completion contract

The current user-authorised scope is OTP sign-in, the dynamic entity workspace, Xero connection administration, sales invoice and supplier bill reading, sales ownership, and Director user administration. The attachment panel/viewer is deferred to the [project-linked task](https://app.notion.com/p/4115eb94886481a19e1cc1c649558a58); its existing secure backend remains. The user will create the commit.

Success requires the integrated application to exercise these journeys with role and entity boundaries, preserve exact recorded financial values, recover from failures, and satisfy the UI specification. Local browser mocks establish browser behaviour only. Database, hosted Auth/Xero, independent review, CI and human Test acceptance remain distinct gates.

## Current local evidence — 8 November 2026

- Director settings, entity administration, Xero authorisation start/callback/organisation confirmation, user administration and sales assignment are implemented and wired into the application.
- The application loads profile and authorised entities before financial data, supports user-scoped entity selection, and clears protected views on session changes. The workspace shares typed API, cancellation, financial formatting and feedback components.
- With `E2E_LOCAL_API=true` and `E2E_LOCAL_API_PORT=18085`, `npm.cmd run e2e -- --project=chromium` executed the final expanded Chromium suite: **20 passed, zero skipped**, exit 0. A separately started packaged backend used the `ci` profile, synthetic settings and an isolated local port; its actual unauthenticated `/api/v1/me` request returned 401. No database or hosted Auth/Xero request was needed. The owned backend was stopped after the run. CI and `scripts/test-all.sh` enable the API case. The human accepted Chrome/Chromium-only browser testing on 8 November; Firefox/WebKit execution is outside this task's acceptance scope.
- These production-build browser checks cover real browser OTP adapter behaviour with synthetic Auth responses; bearer requests; sign-out clearing; sales list/detail/payments; assign/unassign; assignment persistence across reload; entity isolation; bounded second-page navigation and search reset; failure clearing and explicit retry; bill restrictions; entity creation; Xero redirect/callback confirmation; user registration/deactivation; and mobile keyboard/detail focus restoration.
- Responsive checks passed at **320, 390, 768, 1024 and 1440 CSS px**, including long entity names, visible records and no page-wide horizontal overflow. Screenshots are generated at `output/playwright/workspace-<width>.png` (ignored artifacts). The 320 and 768 px screenshots were visually inspected. This is not a complete accessibility or real-device assessment.
- API-client and Settings regression checks: `npm.cmd test --silent -- --maxWorkers=2 src/api/client.test.ts src/settings/Settings.test.tsx`: **15 passed**, exit 0. Tests first reproduced denied OAuth attempt read/confirmation failing to revalidate access, and native browser `fetch` receiving the API client as its receiver; fixes pass. A browser reproduction independently confirmed the native Illegal invocation error before the fix.
- Final `npm.cmd test --silent -- --maxWorkers=2`: **73 tests passed** across 14 files, exit 0, including the unchanged-session regression and prior session-clearing tests.
- Final offline backend `mvn -o -q -B '-Dmaven.repo.local=C:/Users/Lenovo Thinkbook/.m2/repository' '-Dmaven.compiler.fork=true' verify`: **150 tests, zero failures/errors**, exit 0. Four Docker-dependent PostgreSQL classes report zero tests and provide no database runtime proof.
- `npm.cmd run lint --silent`, `npm.cmd run typecheck --silent` and `npm.cmd run api:check --silent` passed. The production Vite build also passed as part of the final browser setup. The latest browser-only test additions are covered by a final lint/typecheck rerun.

The browser harness owns Vite build/preview directly in Playwright global setup, uses public synthetic settings, and closes its own HTTP server during teardown. It does not read credentials or contact hosted Auth/Xero. Synthetic Xero navigation tests the browser start/return/confirmation flow; it does not establish provider consent or token exchange.

## Fixes established by the browser checks

1. Invoke the stored fetch function without binding the API-client instance as its receiver. Native `Window.fetch` otherwise fails before reaching the API, despite injected mock transports succeeding.
2. Position the invoice scroll container so absolutely positioned accessible owner labels remain contained. This removes tablet page overflow while retaining keyboard-accessible table scrolling and labels.
3. Revalidate dashboard access immediately after 401/403 during OAuth attempt recovery and confirmation, just as other protected operations do.
4. Preserve the existing workspace when Auth re-emits an unchanged session token. The regression first reproduced an unsaved Settings draft disappearing; changed-token and sign-out events still clear protected state, and focus/access denial revalidation remains in place.
5. Request `unitdp=4` for invoice/bill details to retain recorded unit-price precision. Independent review identified the missing parameter; its transport regression failed before correction. The existing frontend formatter already preserves supplied four-decimal strings.

## Review and measured browser evidence

The independent fresh-context whole-worktree review covered entity/staff/security/OAuth/grants/read health/invoices/assignments/attachments, migrations, frontend lifecycle/settings/invoices/client/formatting and the browser harness. It identified no Critical defect and two Important defects (unchanged-session state loss and missing provider unit precision); both entered the regression-and-fix pass above. Runtime database and hosted acceptance limitations were explicitly excluded from code-completion claims.

The Chromium accessibility checks passed for doubled root text size at a 390 px viewport without page-wide overflow, one main landmark, heading hierarchy, visible labelled controls, minimum 24 px control targets, skip-link/main focus and seven shared text/background contrast pairs meeting 4.5:1. Browser role checks cover Director, Financial Admin and Standard user, including removal of bill/Settings/assignment controls after an API denial revalidates a demoted role. These targeted checks are not a full automated accessibility audit or screen-reader test.

Production-build lab samples used Chromium 156.0.8078.4, unthrottled local synthetic APIs, a 26-record result set with a 25-record page, and a 900 px viewport height. One sample at 390 px measured LCP 360 ms, accumulated layout shift 0, maximum observed interaction duration 40 ms and OTP-to-workspace journey 1159 ms. At 1440 px the corresponding values were 384 ms, 0.00187, 24 ms and 1129 ms. These are single local samples; interaction duration is not a certified INP result and the samples do not establish field p75 compliance. Repeated runs generate fresh measurements in Playwright JSON report attachments.

## Still required

- Broader accessibility/actual browser zoom, screen-reader and real-device checks; representative field performance measurements. Existing responsive/focus/contrast/text-resize checks and lab samples are useful evidence, not WCAG certification or Core Web Vitals field proof.
- Disposable PostgreSQL execution of schema, provisioning, concurrency, grant rotation and assignment repository tests. A fresh Docker check confirms that the local docker_engine named pipe is absent. Zero executed database tests cannot prove their behaviour.
- CI migration execution and read-only Test schema verification; actual Test OTP delivery/expiry/replay/rate limiting and access revocation; multi-organisation Xero consent, scopes, refresh/revocation, complete-result query semantics and financial field accuracy; human acceptance on Test.
- Update task status only after the corresponding evidence exists. No hosted migration, deployment, commit, PR or Live operation is claimed.

## Reproduce

In `frontend`, run `npm.cmd exec playwright -- install chromium`, then `npm.cmd run e2e`. Use `--project=chromium` for a Chromium-only run. Start the local backend separately and set `E2E_LOCAL_API=true` to include its unauthenticated-request check (`E2E_LOCAL_API_PORT` defaults to 8080). Public synthetic browser settings are supplied by the test setup, independently of deployment configuration. The default reporters write `output/playwright/report.json` with performance attachments and print concise progress.
