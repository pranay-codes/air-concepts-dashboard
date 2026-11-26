# Proposed contract: automatic Service Jobs sync

Status: proposed for human review, 26 November 2026. This contract implements GL-AUTO-SYNC-01 in `GL-automatic-sync.md`.

## Database

Add to `public.green_line_entity_settings` in a new forward-only migration:

```sql
set lock_timeout = '5s';
alter table public.green_line_entity_settings
  add column automatic_sync_enabled boolean not null default false,
  add column automatic_sync_interval_minutes integer not null default 10
    check (automatic_sync_interval_minutes between 10 and 1440),
  add column automatic_sync_next_at timestamptz;
```

Existing rows remain disabled. An entity is eligible only when workflow and automatic sync are enabled, the entity is active, and a Xero connection exists. The scheduler atomically claims due work per entity in the database and advances its next due time before provider calls; overlapping instances and the existing manual/scheduled run locks must not produce concurrent provider refreshes. Save/enable or an interval change sets the next run to the current time plus the interval. Disable clears the due time. Restart does not replay missed intervals. Per-entity failure must not stop other entities. The global switch defaults on so the Settings control works without server reconfiguration; setting it explicitly to false disables the scheduler, and Settings shows when it is off.

This replaces the old fixed business-hours and off-hours cron schedule. Existing installations that opted into that schedule must enable the new per-entity setting after upgrade. If a due tick collides with an already-running manual sync, the tick is skipped and the next attempt is scheduled one interval later.

The 10-minute minimum follows the current business-hours cadence; it is a proposed operational guard, **not** a provider-verified safe rate. The vWork rate limit and actual call volumes are unconfirmed. Provider rate-limit responses and backoff remain authoritative. Daily provider budgets and shared traffic can require a longer interval.

An additional `vwork_sync_rate_limit` table stores a retry deadline against the current shared vWork connection revision. It has RLS enabled, no public, anon or authenticated grants, and service-role select/insert/update grants. Concurrent rate-limit responses retain the latest deadline; a replacement connection does not inherit an old connection's deadline.

## HTTP contract

Extend the existing `GET` and `PUT /api/v1/entities/{entityId}/green-line/settings` Director settings with:

```json
{
  "automaticSyncEnabled": false,
  "automaticSyncIntervalMinutes": 10
}
```

The existing fields and their permissions remain unchanged. On updates, both new fields are optional for compatibility with older clients; when omitted, each preserves its existing stored value. If provided, `automaticSyncEnabled` must be boolean and `automaticSyncIntervalMinutes` must be a whole number from 10 to 1440. Director responses include both; non-Director responses omit them, consistent with existing settings disclosure. The server authorises the Director and entity and validates values independently of the frontend. Invalid requests do not change settings.

Expose the global scheduler availability as a read-only Director-visible field if the existing deployment switch is retained. The settings UI must distinguish disabled for the entity from globally unavailable.

## Verification

Tests must cover saving and reloading per entity, invalid values, permission and entity isolation, changed intervals, disabling, missed runs, multi-instance claims, manual overlap, provider failure and rate limits. Regenerate and check OpenAPI and frontend types. Exercise the migration on disposable PostgreSQL through CI; no hosted database access. No destructive statements are proposed.
