# ADR-001: Live release gate

## Status
Accepted. Date: 2026-11-03. Decision directed by the project owner.

## Context
- The repo is private and user-owned. Live needs a human gate (AGENTS.md §10).
- Docs read and API checked on 2026-11-03.
- The API does not expose the plan name (`plan: null`). GitHub Free is inferred from the 403 responses below.

| Capability, private repo | Finding | Source |
|---|---|---|
| Protected branches | Not available on Free. API: 403 "Upgrade to GitHub Pro or make this repository public". | [1], API `branches/main/protection` |
| Rulesets | Not available on Free. API: same 403. Docs name only Team and Enterprise, so docs and API differ on Pro. | [2], API `rulesets` |
| Environment required reviewers, wait timers | Public repos only on Free, Pro and Team. | [3] |
| Environment secrets | Public repos only on Free. Private needs Pro, Team or Enterprise. | [3] |
| Deployment branch and tag limits, environment variables | Private needs Pro or Team. Free not listed. | [3] |
| `workflow_dispatch` | Available. Needs write access. Workflow must be on the default branch. | [4] |
| Actions allowance | $12 of compute per month, not a stated minute count. About 2,000 Linux 2-core minutes at $0.006 (calculated). | [5] |

Sources:
1. https://docs.github.com/en/get-started/learning-about-github/githubs-plans
2. https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets
3. https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments
4. https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
5. https://docs.github.com/en/billing/concepts/product-billing/github-actions
6. https://docs.github.com/en/actions/reference/security/secure-use

## Decision
- Release Live through a manually triggered workflow (`workflow_dispatch`) with confirmation inputs.
- Human review of every migration is the compensating control for the missing server-side gates.

## Consequences
- No extra GitHub plan cost.
- Open risks, none enforced server-side:
  - Confirmation inputs prevent accidents. They are not an access control.
  - Nothing forces a PR into `main`, so migration review is procedural.
  - Without environment secrets, Live credentials would be repository secrets. "Any user with write access to your repository has read access to all secrets" [6].
  - Any session using the owner's `gh` login can technically trigger the workflow. Only AGENTS.md §10 prevents it.
- Options not chosen, for a human to revisit:
  - GitHub Pro: adds protected branches and environment secrets. Still no required reviewers on private repos [3].
  - AWS OIDC trust limited to `main`. Unverified here; confirm in a later task. Does not cover Supabase credentials.
- Workflow and Live secret layout are out of scope here and come in a later step.
