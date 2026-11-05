# Spike: Can CI-002 build linux/arm64 on a GitHub-hosted runner for this private repo?

## Question
- Can the deploy build a `linux/arm64` backend image on a GitHub-hosted runner in this private repository, within the plan's minutes?

## Method
- Official GitHub changelog read with WebFetch on 2026-11-06: https://github.blog/changelog/2026-01-29-arm64-standard-runners-are-now-available-in-private-repositories
- Time box: one read. No workflow was run, so nothing was measured here.

## Findings
- Confirmed: the changelog lists `ubuntu-24.04-arm` as a supported arm64 label, alongside `ubuntu-22.04-arm` and `windows-11-arm`.
- Confirmed: the changelog says the arm64 runners can be used in all repositories, including private ones, on the free-tier-eligible list.
- Confirmed: the changelog says usage counts toward the free minutes included in the plan.
- Not confirmed: the minute allowance for this account's plan, and what happens beyond it. The changelog does not say whether usage beyond the allowance is billed.
- Not confirmed: the per-minute rate for arm64 runners in private repositories.

## Decision
- Partial.
- Allows: `runs-on: ubuntu-24.04-arm` for the backend build job, so the image is built natively on arm64 with no QEMU emulation.
- Does not confirm: the cost. The first Test run must record the minutes used in the PR (spec, "Usage and limits").

## Date
- 2026-11-06

## Evidence
- https://github.blog/changelog/2026-01-29-arm64-standard-runners-are-now-available-in-private-repositories (read 2026-11-06)
