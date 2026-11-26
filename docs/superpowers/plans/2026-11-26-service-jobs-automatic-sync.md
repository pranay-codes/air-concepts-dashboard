# Service Jobs automatic sync implementation plan

**Goal:** Implement [GL-automatic-sync](../../specs/GL-automatic-sync.md) using a server-side schedule configured per entity in Settings.

**Architecture:** Extend the existing Director-only Green Line settings and database table with automatic-sync configuration. Replace the fixed cron behaviour with a server scheduler that reads persisted per-entity settings, uses existing provider sync services, and coordinates scheduled runs across instances. The Service Jobs page continues to read stored work items.

**Tech stack:** Spring Boot, PostgreSQL, React and TypeScript.

## Constraints and review focus

- Preserve entity isolation, existing manual refresh, provider rate-limit handling and honest sync status.
- Prevent overlapping manual and scheduled runs, including across application instances; restarting must not create a burst of catch-up runs.
- An inactive or archived entity, disconnected provider, failed run or rate limit must not produce false success or block other entities.
- Keep tokens and tenant selection server-side; do not perform hosted database or Live operations.
- The existing scheduler is globally opt-in. Define the deployment switch and migration defaults so installation does not silently start provider traffic.
- Provider frequency limits and quote pagination are not fully verified; do not represent an interval or completeness as provider-confirmed.

## Tasks

1. **Provider evidence and design:** Review existing spikes and official provider evidence for scheduled reads, rate limits and Retry-After. Record any gaps and choose a bounded interval and default with the human where evidence cannot establish a safe value.
2. **Contract and migration:** Specify an additive forward-only migration for per-entity enabled state and interval, the Director settings request/response, defaults and bounds. Obtain human contract review before application implementation. Cover validation, role checks, unauthorised entities and preservation of existing settings.
3. **Scheduler:** Write failing tests for due/not-due, disabled and archived entities, interval changes, restart, concurrent instances, manual overlap, provider failures and rate limits. Implement database-backed scheduling using the existing sync service and durable coordination; avoid unbounded catch-up. Test each entity independently.
4. **Settings:** Write failing frontend tests for per-entity loading, editing, validation, save, failure and permissions. Implement accessible controls in `frontend/src/settings/WorkflowSettings.tsx`, preserving unsaved changes and existing settings. Regenerate the OpenAPI snapshot and frontend types together.
5. **Verify and hand over:** Run appropriate backend and frontend checks, API contract check and relevant browser/accessibility checks; record missing disposable PostgreSQL, provider and Test evidence. Obtain independent review where available, commit the scoped changes and open a PR against main. Do not merge or deploy.

## Definition of done

Meet the acceptance criteria and evidence requirements in the spec, with provider uncertainty and unrun checks explicitly disclosed in the PR.

## Review decisions

- The independent review identified that applying a new retry gate in `systemAccess()` could make a successful job-creation result look uncertain during its subsequent connection check. The gate therefore applies to sync reads through `syncAccess()`; `systemAccess()` retains its pre-change behaviour. The follow-up review suggested the old method had already enforced the deadline, but the original implementation did not. Coordinating backoff for job creation is outside this sync change.
- PostgreSQL integration tests are CI-only. The due-claim SQL explicitly casts its timestamp parameter and the migration grants only the required table privileges.
