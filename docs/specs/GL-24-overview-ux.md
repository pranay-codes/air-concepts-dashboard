# Spec pack: GL-24 Director overview, decision-first layout

- Status: approved
- Approved by / date: the human, in conversation (plan approval), 26 November 2026
- Notion page URL: none; the requirements come from the 26 November 2026 conversation
- Notion last-edited date: not applicable

## Goal
The Director can see what needs a decision, understand each report and open the job concerned. This changes the presentation of [GL-24-director-dashboard](GL-24-director-dashboard.md); its report definitions and counting rules are unchanged.

## Requirement IDs
GL-24, GL-DIR-01 to GL-DIR-06 (existing). Presentation standard: [UI/UX Design Specification](../ui-ux-design-specification.md).

## Acceptance criteria
- AC-1 (GL-24): Headline figures are grouped as open now and in the selected period, show server-provided values and link to their report.
- AC-2 (GL-DIR-01, GL-DIR-02): "Needs your decision" lists only the rows that apply, in this order: completed not invoiced, invoices not sent, flagged for attention, late, unassigned. Each has a count, a next step and a link to the records. With none, it says so.
- AC-3 (GL-DIR-06): When the open list is cut off at 500, counts taken from it read "At least N" and the panel says what was checked.
- AC-4 (GL-DIR-01): Late or flagged open jobs are listed with the reason. People are ordered by late or flagged jobs, then by open jobs.
- AC-5 (GL-DIR-03): The slowest single step by median is marked (the quote-to-scheduled span is excluded), steps with fewer than 5 measured jobs carry a caution, and one day reads "1 day".
- AC-6 (GL-DIR-03 to 05): Period presets apply immediately; the screen says the period changes the performance figures only.
- AC-7 (GL-24): Long lists show the first 10 rows (5 on narrow screens) and a control to show all.
- AC-8 (GL-DIR-01, GL-DIR-06): Opening a job selects its entity, shows the existing job detail and returns to the Overview and the previous entity selection. Changing the entity closes the job. A job of an unauthorised entity cannot be opened.

## Confirmed decisions
- Frontend only: no API, migration, Xero or vWork change (human, 26 November 2026).
- Job drill-down reuses the My Work pattern, including the entity switch (human, 26 November 2026).
- No money is calculated in the browser. The "invoices not sent" headline value is the quoted value and is labelled so.
- Bars compare job counts or days only. "Fewer than 5 jobs" is a reading caution, not a business rule.

## Out of scope
Previous-period comparison, trends, targets, overdue amounts, win rate, owner assignment.

## Open questions
- None blocking.

## External dependencies needing a spike
None.

## Test plan
| Acceptance criterion | Named test | Level (unit, integration, e2e) |
|---|---|---|
| AC-2 to AC-6 | `overviewModel.test.ts` | unit |
| AC-1 to AC-8 | `DirectorOverview.test.tsx` | unit |
| AC-1, AC-2, AC-8 | `director-overview.spec.ts` | e2e |
