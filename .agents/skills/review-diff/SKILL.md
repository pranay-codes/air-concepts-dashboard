---
name: review-diff
description: Use when reviewing a branch against main and its spec pack, especially when cross-reviewing the other agent's code.
---

# review-diff

Applies the review checklist in AGENTS.md §11. Roles: AGENTS.md §3.

## Steps
1. Run `git diff main` and `git diff main --stat`.
2. Find the spec pack in `docs/specs/` from the PR or issue. If none exists, stop and ask.
3. Check the diff against the pack's acceptance criteria, out of scope and confirmed decisions.
4. Work through every AGENTS.md §11 item.
5. Run the extra checks below.
6. Report findings. Change nothing.

## Extra checks

### Migration safety (AGENTS.md §8)
- Edited or renamed existing migrations.
- Missing RLS, explicit grants or lock timeouts.
- DROP, TRUNCATE or unscoped DELETE without human approval noted.
- Breaking change without expand and contract.

### Secrets (AGENTS.md §7)
- Keys, tokens, connection strings or `.env` content in the diff.
- Sensitive values in logs, errors or fixtures.
- Live or production values anywhere.

### Tests (AGENTS.md §6)
- Removed, skipped or disabled tests.
- Loosened assertions or widened mocks.
- Acceptance criteria with no named test.
- Missing negative or security cases.

## Output
Findings only, grouped by severity. No praise, summary or fixes.

### Blocker
- `path/file:line`, requirement ID or AGENTS.md rule: finding.

### Major
- `path/file:line`, requirement ID or AGENTS.md rule: finding.

### Minor
- `path/file:line`, requirement ID or AGENTS.md rule: finding.

Write "None" under an empty severity.

Severity guide:
- Blocker: entity isolation, token or secret exposure, missing backend role check, unsafe migration, weakened test.
- Major: acceptance criterion unmet, non-idempotent external write, missing negative test.
- Minor: other departures from the spec pack or AGENTS.md.
