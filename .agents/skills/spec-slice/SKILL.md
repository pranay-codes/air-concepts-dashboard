---
name: spec-slice
description: Use when a human gives a set of requirement IDs and source text to turn into a spec pack in docs/specs/.
---

# spec-slice

Drafts a spec pack from [docs/specs/TEMPLATE.md](../../../docs/specs/TEMPLATE.md). Source rules: AGENTS.md §2. Naming: [docs/specs/README.md](../../../docs/specs/README.md).

## Inputs, all from a human
- Requirement IDs.
- Requirement text for each ID. Never read Notion yourself (AGENTS.md §2).
- Notion page URL and last-edited date for each source page.

If any input is missing, stop and ask.

## Steps
1. Check `docs/requirements/README.md` for known conflicts touching these IDs.
2. Copy the template to `docs/specs/<use-case>.md`. If the file exists, stop and ask.
3. Fill the header and every section from the supplied text only.
4. Write acceptance criteria that each name a requirement ID. Include negative and security cases (AGENTS.md §6).
5. Map each criterion to one named test.
6. List every conflict under Open questions: known ones, and any new ones between sources.
7. List every external capability under External dependencies needing a spike. Check status in `docs/spikes/README.md`; anything unconfirmed stays listed.
8. Leave Status as draft and Approved by empty.

## Stop and ask a human when
- A requirement is ambiguous or incomplete.
- Sources conflict, or precedence does not settle it.
- An ID has no source text.
- The slice looks too large for one PR (AGENTS.md §9).

## Never
- Invent requirements, IDs, dates, URLs or decisions.
- Resolve a conflict yourself. Record it and ask.
- Mark a pack approved, or start planning from a draft.
- Fill a gap from Product Scope, older pages or assumptions.

## Output
- The draft pack path.
- Lists of: conflicts, spikes needed, open questions.
