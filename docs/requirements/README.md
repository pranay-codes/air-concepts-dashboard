# Requirements

Agents work from spec packs only. Access rules: AGENTS.md §2.

## Precedence
1. Notion use case pages.
2. Infrastructure Architecture page dated 3 November 2026.
3. Everything else.

## Known conflicts
Named in AGENTS.md §2. Agents stop and ask; a human decides.

| Conflict | Resolution when a human decides |
|---|---|
| FR-UC04-01 to 07 used by both the supplier bill and forecast use cases | Pick which use case keeps the IDs and renumber the other in Notion (proposed; AGENTS.md gives no outcome). |
| Product Scope v1.3 vs Use Case 1 (30 October) | Confirm Use Case 1 supersedes the listed v1.3 items and mark v1.3 accordingly in Notion. |
| UC4 architecture page vs 3 November infrastructure decision | Confirm the 3 November decision supersedes it and correct the UC4 page in Notion. |

Record each outcome in the affected spec pack under Confirmed decisions.

## Refreshing a spec pack
- Every pack header carries the Notion page URL and its last-edited date.
- A changed date means the pack may be stale.
- Agents cannot read Notion, so a human compares the dates.
- If stale: a human supplies the changed text; the pack is updated through a PR. Stop dependent work until it merges.

## Changing requirements
- A human makes the change in Notion.
- It reaches the repo as a reviewed spec pack PR.
- Agents never edit requirements to fit code.
