# UC4 addendum: current-month updates and month-end overdue carry-forward

Requirement: FR-UC04-15. Decisions made by the user on 27 November 2026.

## Scope

The invoice and bill forecast grid's **Create forecast** action. The financial year summary, its Generate forecast action and the Excel export are unchanged.

For the grid, this supersedes AC-14 in [UC4.md](UC4.md) (overdue documents land in the current month), the current-month rules in the [24 November grid design](../superpowers/specs/2026-11-24-forecast-grid-design.md), and the no-double-counting rule for carried overdue documents.

## Rules

Days are calendar days in Pacific/Auckland. The forecast date is the dashboard override, Xero expected date, due date or invoice date, in that order; split lines use their own dates. A document is overdue when its forecast date is before the day of the action. The grid is a forecast of expected payments: only outstanding documents are ever added, and excluded documents contribute nothing.

### Current month

- **Days 1 to 10:** the current month is updated by adding to it. Saved lines stay as they are, including lines for documents since paid, part-paid or rescheduled. Outstanding invoices and supplier bills due in the month, or overdue, that the month does not list are added. A missing current month is created. A generated month not yet in the grid is copied with its lines unchanged, then added to.
- **Day 11 onwards:** the current month is not created, copied or changed.

A saved unsplit line covers every split of that document, and a saved split covers a later unsplit amount, so one document is not added twice to the same month.

### Month end

In the last 10 calendar days (for example 21 to 30 November, 22 to 31 December, 19 to 28 February), the next month receives its normal contributions plus every overdue outstanding invoice and bill at the current amount due. This includes documents already saved in an earlier month and part-paid documents.

An unpaid document therefore moves forward at each month end: overdue at October month end it is added to November, and if still unpaid at November month end it is also added to December. Earlier snapshots are not edited.

### Later months

Each action saves new versions of the next six months, without repeating documents already listed in the current or an earlier month (apart from the month-end carry).

## Balances

Balance arithmetic is unchanged: a month opens with the preceding closing balance unless an opening balance has been entered. A document shown in two months counts in both. Entering the next month's actual opening bank balance avoids counting it twice.

## Acceptance

- At month end, overdue invoices and bills appear once in the next month, including previously saved and part-paid documents, and the current month has no new revision.
- Documents not yet overdue, including those due on the day of the action, are not carried.
- On days 1 to 10, saved current-month lines are unchanged and unlisted outstanding documents due or overdue are added; with nothing to add to a saved grid month, no new revision is written.
- From day 11, a missing or generated-only current month is not created or copied.
- Excluded documents are not added; a date override into the future prevents carrying.
- Overdue split lines are carried; other lines of a changed schedule remain excluded and are reported.
- Day boundaries hold for 28, 30 and 31 day months.

## Known limits

- If the current month was never saved by day 10, its not-yet-overdue documents due later that month are shown in no month until a later action finds them overdue.
- A document raised after day 10 and not yet overdue is likewise not added to the current month.
- The mirror is only as current as the last Xero sync; Create forecast does not sync.
