# UC4 implementation prompt

Paste the block below as the goal for the implementing agent (ChatGPT / Codex). Fill the two bracketed choices first; they are the human decisions the agent is not allowed to make. Lines starting with `>` are notes for you, not part of the prompt.

> Goal reference: the Notion page "Phase 1 - Build HotAS forecasting automation" (URL below, `?source=copy_link` removed). The agent must not read Notion (AGENTS.md §2), so the approved spec pack must already cover everything on that page. Check this before you start. This prompt assumes that page is delivered as Use Case 4; if it is not, change the pack first.
>
> The prompt assumes `docs/specs/UC4.md` has been approved and OQ-1 to OQ-6 in that pack have answers. If any is still open, the agent will stop at slice 0 and ask, which is the intended behaviour under AGENTS.md.
>
> The agent works from the committed repository. The untracked files it needs (`docs/specs/UC4*.md`, `docs/plans/UC4.md`, `docs/spikes/uc4-*.md` and `docs/ACSL Cashflow Forecast Template - Cleaned.xlsx`) must be committed to the base branch first, or the agent will not see them. Commit them yourself; nothing has been committed here. The ACSL template contains four staff names and an hourly rate, so decide whether it belongs in git at all. The slice 9 scrub only changes the test copy.
>
> Codex reads AGENTS.md. CLAUDE.md only imports it, so the prompt names AGENTS.md alone.
>
> The prompt asks for 16 stacked PRs in one run. AGENTS.md §1 and §12 normally call for one task per session. Expect a long run and check each PR body.

---

```
/goal develop everything described in the Notion page "Phase 1 - Build HotAS forecasting
automation" (https://app.notion.com/p/pranay-narotam/Phase-1-Build-HotAS-forecasting-automation-4005eb94886481fc976dc6753fe47480)
for the Air Concepts Financial Admin Dashboard. Deliver it as Use Case 4, "Financial forecast and
business cash position", end to end, following this repository's AGENTS.md.

Do not open or read the Notion page yourself; AGENTS.md §2 forbids reading Notion directly. The
approved spec pack is the only authoritative scope. If the work you need is not covered by
docs/specs/UC4.md, stop at slice 0 and report the gap.

Sources, in order of authority:
1. docs/specs/UC4.md — the approved spec pack (requirements FR-UC04-01..21, acceptance criteria
   AC-1..AC-29, confirmed decisions, open-question defaults, test plan).
2. docs/plans/UC4.md — the technical plan: gates (§2), data model (§5), sync algorithm (§6),
   calculation rules (§7), Excel pipeline (§8), API contract (§9), frontend (§10), test plan (§11),
   delivery sequence (§12), switchable defaults (§13), security checklist (§14), definition of done (§15).
3. docs/specs/UC4-template-map.md — the cell-by-cell workbook contract; the populator and
   validator follow it exactly.
4. docs/spikes/uc4-xero-forecast-fields.md, uc4-xero-rate-limits.md, uc4-poi-template.md,
   uc4-xero-expected-date-writeback.md — what is confirmed about Xero and POI. Treat anything
   not confirmed there as unavailable (AGENTS.md §2). In particular: the If-Modified-Since step
   is an optimisation only and the settlement re-read in plan §6.3 step 6 is mandatory; the
   sync must pace itself under 60 calls per minute per tenant and honour Retry-After; the
   populator must write a value or blank into every cell of Staffing!D23:O29.
5. docs/ui-ux-design-specification.md for every frontend slice (AGENTS.md §14 applies).
Existing code to reuse rather than duplicate: xero/ (XeroInvoiceClient, XeroReadErrors,
XeroReadHealth, TenantAccess, XeroConnectionService), the UC5 repository/service/controller
patterns, Uc5SchemaTest, frontend DashboardApi, useResource, useAction, Feedback, format, Modal,
and the generation/abort pattern in workspace/Workspace.tsx.

Human decisions already taken (do not re-ask):
- Requirement ID conflict FR-UC04-01..07: [the forecast use case keeps the IDs | the supplier
  bill use case keeps the IDs and UC4 uses the renumbered IDs listed in docs/specs/UC4.md].
- New dependency approved: org.apache.poi:poi-ooxml:5.4.1. No other new dependency.
- The scoped delete for split-line replacement (plan §5.1) is approved.
- Client-question defaults in plan §13 (Q2–Q12) are approved as stated; implement each as the
  named constant or configuration value so it can change without redesign.
- Authorisation level for this goal: [A: implement every slice without approval pauses,
  including auth, Xero, migration and entity-isolation slices, and open the PRs | B: stop and
  ask before editing any slice marked G6 or G7 in plan §12]. This never authorises Live
  operations, hosted-database connections, deploy commands or accounting writes to Xero.

Delivery rules:
- Work through plan §12 slices 0 to 16 in order, respecting the "Depends on" column. One
  slice = one branch feat/fr-uc04-<nn>-<short-name> = one PR, each near the 300-line guideline.
  Stack each branch on the previous open PR's branch; say so in the PR body. Stop at open PRs;
  never merge, release or approve your own PR.
- Before each slice, write a short plan (files and tests) in the PR body draft (AGENTS.md §1).
  Under authorisation level A, record the plan and continue; under level B, stop before G6/G7 slices.
- For every slice: write the failing tests named in docs/specs/UC4.md's test plan first, each
  named after its requirement ID; implement until green; run the commands in plan §11 and quote
  the command and result. Where the environment has the tools, also run ./scripts/check-all.sh
  before opening the PR. Report Docker-backed tests as "not run" when Docker is unavailable.
- Migrations: one forward-only file per schema slice, set lock_timeout = '5s', RLS on, explicit
  revokes and grants, numeric(18,2) money, first-of-month dates. Extend Uc4SchemaTest to prove
  browser roles have no privileges on every new table. Never connect to a hosted database.
- API: every endpoint in plan §9 with a stable operationId; regenerate the OpenAPI snapshot and
  frontend types together, using the commands in AGENTS.md §4 (./mvnw -q test -Dopenapi.update=true
  in /backend, then npm run api:generate in /frontend; check with npm run api:check). Money as
  decimal strings; months as YYYY-MM.
- Security checklist (plan §14) applies to every slice: role and entity checked server-side on
  every request; foreign IDs return 404; STANDARD_USER gets 403 on every forecast route;
  staffing data never reaches STANDARD_USER or any aggregate DTO; scheduled sync uses
  XeroConnectionService.systemAccess only and no controller path reaches it; tokens never leave
  TenantAccess/XeroAccess; run rows and audit rows carry codes and changed columns only, never
  provider bodies or tokens; uploads are size-capped, OOXML-only, zip-bomb guarded, parsed in
  memory and never persisted; export responses carry no-store, nosniff and an attachment
  disposition built from the validated entity code and dates only.
- Before slice 9: copy docs/ACSL Cashflow Forecast Template - Cleaned.xlsx to
  backend/src/test/resources/forecast/template-v1.xlsx after blanking the four staff names
  (Staffing rows 10–15 columns A–B and rows 23–28 columns A–B) and the hourly rate (column D),
  list exactly which cells you changed in the PR, and ask the human to confirm the scrub before
  that PR is marked ready. Do not change the original file.
- Each PR body lists: requirement IDs, acceptance criteria covered, test evidence (commands and
  results), security checklist, migration checklist where relevant, reviewing agent (the other
  agent reviews; Claude Code reviews Codex's work, so write "pending" and never self-review), and a
  rough note of usage consumed.
- Slice 16 produces docs/requirements/UC4-coverage.md (evidence per requirement and per
  acceptance criterion, "not run" stated explicitly), docs/uc4-forecast-operations.md (sync
  schedule 04:30 Pacific/Auckland, manual rerun, FORECAST_SYNC_TOO_MANY_CHANGES handling,
  rate-limit headers, the five-connection uncertified-app tier limit, rollback: migrations stay,
  app artefact rolls back) and the Playwright Chromium journey at 320, 390, 768, 1024 and 1440 px.

Stop and ask a human only when: a spec requirement is ambiguous or two sources conflict; a
gate in plan §2 has no recorded answer; the Notion goal contains work the spec pack does not
cover; a test would need a real Xero organisation, a hosted database or Live; or a change would
weaken or delete an existing test. When you stop, say so in your final report: name the blocker,
the slices finished, and the question. Otherwise make routine decisions yourself, record them in
the PR body, and keep going until all 16 slices have open PRs or you are blocked. Finish by
listing every open PR, every "not run" item and every decision you made that the human should confirm.
```

---

> **Where this prompt depends on things only you can do**
>
> - Confirm that the Notion page "Phase 1 - Build HotAS forecasting automation" is delivered by UC4. If it covers more, add that scope to `docs/specs/UC4.md` before the goal starts.
> - Approve `docs/specs/UC4.md` and record OQ-1 to OQ-6 under Confirmed decisions.
> - Commit the untracked UC4 files to the base branch. Decide whether the ACSL template with staff names belongs in git.
> - Choose the requirement ID option and authorisation level A or B in the prompt. Under AGENTS.md §1 the default is B. Level A matches the standing UC5 learned rule ("implementing backend functionality before UI/UX without additional approval pauses"), but AGENTS.md says that rule is for UC5, so you must say it again for UC4 if you want it.
> - Confirm the fixture scrub when the slice 9 PR asks for it.
> - Merge PRs in order; the agent stacks branches but cannot merge.
> - If you want the Test tenant check for spike S1 (one ACCREC with an expected date and one ACCPAY with a planned date visible in a paged summaryOnly list), create those two synthetic documents in the Test organisation before slice 3 reaches Test.
