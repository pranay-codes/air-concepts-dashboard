---
name: spike
description: Use when investigating one open question from docs/spikes/README.md to confirm an external capability before it is relied on.
---

# spike

Answers one question and records the result. Basis: AGENTS.md §2 and §5.

## Steps
1. Take one question from [docs/spikes/README.md](../../../docs/spikes/README.md). If it is not listed, stop and ask.
2. Agree a time box with the human. If none is given, propose one and wait.
3. Copy [docs/spikes/TEMPLATE.md](../../../docs/spikes/TEMPLATE.md) to `docs/spikes/<short-question>.md`. If the file exists, stop and ask.
4. Set the question's status to `in progress` in the spike list.
5. Investigate using allowed sources only.
6. Record method, findings, evidence, decision and date in the note.
7. Set the final status in the spike list and link the note.

## Allowed sources
- Official provider documentation.
- Provider sandbox or demo environments.
- The Test environment, with synthetic data only (AGENTS.md §10).

## Not allowed
- Live systems, production data or customer Xero organisations.
- Forums, blogs or memory as proof. They can suggest what to test, nothing more.
- Production code. Throwaway commands stay out of the repo.
- Secrets, tokens or production values in the note (AGENTS.md §7).

## Recording results
- Docs say it, but not tested: say so. Mark `partial`.
- Tested: give the environment, the sanitised request and the observed response.
- Time box ended: record what is known and mark `partial` or `not confirmed`.
- Never guess. Unknown stays unknown.

## Stop and ask a human when
- Access, an account, a sandbox or a credential is missing.
- Permission is unclear, or a step could touch real data.
- The test would send real email or write to a real provider account.
- The answer changes scope or conflicts with a spec pack.

## Output
- The note path, the status change and the decision in one line.
