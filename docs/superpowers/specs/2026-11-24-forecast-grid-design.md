# Forecast grid design

Status: approved by the human on 24 November 2026.

## Goal and scope

Display an entity's forecast as a spreadsheet-style grid: customer contacts for invoices and suppliers for bills, with months as columns. Defer employee wages. Keep the combined-entity view within its existing aggregate-only authorisation boundary.

## Behaviour

- On entry, show the previous month, current month and six subsequent months; labelled arrows navigate to earlier and later months, including across financial years.
- A Create forecast action creates the current month's snapshot if absent. Existing snapshots for the current and earlier months are preserved, including their invoice and bill amounts. Later Xero changes do not alter them.
- The action creates or refreshes future-month forecasts from the latest available authorised source data. Repeating it replaces future snapshots, while leaving current and earlier snapshots unchanged.
- Distinguish unavailable or uncreated months from zero. Preserve entity isolation, access checks, freshness information and accessible loading and error states.
- Do not write invoices or bills to Xero. Retain existing financial functionality outside the requested grid unless changes are required to support the agreed behaviour.

## Approach

Save month-specific snapshots, extending the existing forecast storage where appropriate. This supports stable past and current forecasts while permitting future forecasts to refresh. Recalculating all months on every display was rejected because it would change protected months. Review the existing immutable snapshot model and use a safe, forward-only storage design if it cannot support replacement of future months.

## Prompt contract

Deliver the described grid and generation behaviour using the approved UC4 pack and this addendum. Do not alter historical or current snapshots on refresh, mix organisations, expose protected data, or present missing information as zero. Surface missing source data and failed generation rather than claiming success.

## Definition of done

The grid and month navigation match the requested window; customer and supplier totals reconcile with their source contributions; initial generation and repeated refresh preserve protected months and replace future months; access and entity isolation are verified; relevant component, backend and contract tests pass. Document unrun CI, database and browser checks, obtain independent review where available, and create a PR against main without merging or deployment.

## Implementation planning

Inspect the forecast resolver, month storage and API, frontend grid and navigation, existing UC4 specifications and associated tests. Prepare an explicit file-and-test plan and seek approval for any changes involving entity isolation, Xero integration or migrations before implementation, as required by the repository rules.
