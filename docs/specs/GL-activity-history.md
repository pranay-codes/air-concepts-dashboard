# Spec pack: Green Line job activity history

- Status: approved
- Approved by / date: human, 25 November 2026 (plan approval in conversation)
- Notion page URL: not used; requested directly by the human
- Notion last-edited date: not applicable

## Goal

On a job, staff can see what happened, what the value was before and after, who or what recorded it, and when.

## Requirement IDs

- GL-07: every stage change and action records the user, time, and previous and new values, shown on the timeline.

## Acceptance criteria

- AC-1: A stage change shows the previous and new stage. An owner change, manual or automatic, shows the previous and new owner by name, or "Unassigned".
- AC-2: Job scheduled, job completed and invoice created each appear as a named entry with the recorded time. The invoice number, scheduled time, technician and vWork completion time appear when recorded.
- AC-3: A vWork reschedule, technician change or status change that leaves the stage unchanged adds one entry. An unchanged job adds none.
- AC-4: Records written by one action (same recorded time) appear as one entry, newest first.
- AC-5: Entries name the person, "Xero sync", "vWork sync" or "System". Times are Pacific/Auckland, and the card states they are when the dashboard recorded the change.
- AC-6: Older records show only what they recorded; nothing is inferred or backfilled. Note text is not shown in the timeline.
- AC-7 (security): history is read only for the requested entity and work item, under the existing role check. Another entity's request returns nothing.
- AC-8: Change lines are readable without colour or the arrow symbol and wrap on narrow screens.

## Confirmed decisions

- Timeline layout, without a key-dates summary or table. Human, 25 November 2026.
- Include vWork reschedules and technician changes. Human, 25 November 2026.
- No migration: new values use the existing `before` and `after` JSON, and the new event type `JOB_UPDATED`.
- Owner and staff names are the current names, not the names at the time of the change.

## Out of scope

- Backfilling older records, filtering or paging the timeline, new provider calls, and new permissions.

## Open questions

- None.

## External dependencies needing a spike

| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| vWork job status, worker, planned start and completion time (already mirrored) | `gl-vwork-api.md` | Account-specific behaviour unverified; this change adds no provider capability |

## Test plan

| Acceptance criterion | Named test | Level |
|---|---|---|
| AC-1, AC-2, AC-4, AC-5, AC-6 | `activity.test.ts` | unit |
| AC-5, AC-8 | `ActivityTimeline.test.tsx` | component |
| AC-8 | `green-line-activity.spec.ts` (390 px and 1440 px, Chromium) | browser |
| AC-3 | `VworkMirrorServiceTest.gl07_*` | unit |
| AC-1, AC-2, AC-7 | `QuoteMirrorRepositoryTest.gl07_*` | integration (CI) |
