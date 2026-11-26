# Green Line: automatic Service Jobs sync

- Status: approved
- Approved by / date: human, 26 November 2026, in conversation.
- Source: human request and clarification in conversation; no Notion source supplied.

## Goal

Keep Service Jobs data current through a server-side schedule that runs when the dashboard is closed. Configure the schedule in Settings, persist the configuration in the database, and display the synchronised database data.

## Requirement

- GL-AUTO-SYNC-01: Provide per-entity automatic refresh configuration in Settings. A Director can enable or disable it and choose an interval. The server reads the stored configuration and runs the existing Xero and vWork refresh in the background. Keep manual refresh available.

## Acceptance criteria

- AC-1 (GL-AUTO-SYNC-01): A Director can view, change and save each entity's automatic sync setting and interval; the saved setting survives browser and server restarts.
- AC-2 (GL-AUTO-SYNC-01): With automatic sync enabled for an eligible entity, the server initiates the existing provider refresh at the configured interval without an open browser. Service Jobs reads the resulting stored data.
- AC-3 (GL-AUTO-SYNC-01): Disabling automatic sync stops subsequent scheduled runs for that entity; manual refresh remains available under its existing permissions.
- AC-4 (GL-AUTO-SYNC-01): Validate intervals in both the frontend and backend, reject invalid values, and preserve unsaved input on failure. Provider limits determine a safe supported minimum.
- AC-5 (GL-AUTO-SYNC-01): Prevent overlapping sync runs, honour provider rate-limit responses and record failures without reporting them as successful.
- AC-6 (GL-AUTO-SYNC-01): Enforce Director authorisation for changes and entity isolation on the server; a user cannot configure an unauthorised entity.

## Confirmed decisions

- The schedule runs server-side even when the dashboard is closed. The configuration is entered in the frontend and stored where the server can read it. Human, 26 November 2026.
- The human approved the acceptance criteria and authorised a task ID based on this request, without a Notion source. Human, 26 November 2026.

## Boundaries and external evidence

- Existing provider refresh and its entity boundaries remain in use; no accounting writes or Live access are authorised.
- The existing scheduler is opt-in and uses separate business-hours and off-hours cron schedules. Its interaction with the new configuration must be resolved in the plan.
- `docs/spikes/gl-vwork-api.md` does not confirm vWork rate limits. `docs/spikes/gl-xero-quotes-contacts.md` does not confirm quote pagination completeness. Provider-specific completeness and safe frequency must not be asserted without evidence.

## Definition of done

Acceptance criteria have named tests, including authorisation, isolation, scheduling and failure cases. Applicable backend and frontend checks pass; migration and API contracts are reviewed, independent review and CI are recorded, and any unavailable Test or provider verification is reported separately. An open PR targets main; no merge or deployment is performed.
