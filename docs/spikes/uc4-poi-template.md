# Spike: UC4 Apache POI against the client's forecast workbook (S5)

## Question
- Can Apache POI open the client's template, write values into cells, preserve all formulas and styles, rename the forecast sheet, set "recalculate on open", and evaluate the template's functions (`IF`, `OR`, `SUM`, `SUMIFS`, `MAX`, `DATE`, `YEAR`, `MONTH`, `EDATE`, `INDEX`, `MATCH`, `&`)? (`docs/plans/UC4.md` §4 S5; requirements FR-UC04-11, 12, 21.)

## Method
- Hands-on test, 10 November 2026, on the developer machine with Temurin OpenJDK 25.0.5 and Apache POI `org.apache.poi:poi-ooxml:5.4.1` (resolved through the project's Maven wrapper into the local repository; transitive set: poi 5.4.1, poi-ooxml-lite 5.4.1, xmlbeans 5.3.0, commons-compress 1.27.1, commons-io 2.18.0, commons-codec 1.18.0, commons-collections4 4.4, commons-math3 3.6.1, SparseBitSet 1.3, curvesapi 1.08, log4j-api 2.24.3).
- Input: a copy of `docs/ACSL Cashflow Forecast Template - Cleaned.xlsx` placed in a session scratch directory. Two throwaway single-file Java programs (populate-and-evaluate; before/after formula diff) were run with `java -cp … Spike.java`. Nothing was added to the repository, no dependency was added to `pom.xml`, and the scratch copy still contains the sample's staff names, so it is **not** the fixture for gate G5.
- Time box: 60 minutes, proposed by the agent.

## Findings
All observed, not inferred.

| Check | Observed |
| --- | --- |
| Open from bytes with zip guards | `ZipSecureFile.setMinInflateRatio(0.01)` and `setMaxEntrySize(50 MiB)` exist and the workbook opens from a `ByteArrayInputStream` |
| Structure | 5 sheets, 0 defined names, 1,417 formula cells, 0 VBA parts, 0 external links (`getExternalLinksTable()` empty) |
| Functions used anywhere | exactly `DATE, EDATE, IF, INDEX, MATCH, MAX, MONTH, OR, SUM, SUMIFS, YEAR` plus `&` concatenation, matching template map §6.7 |
| Key formulas read back | `Forecast!B26 = SUM(B8:B25)`, `B68 = B5+B26-B67`, `Xero_Debtors!G5 = IF(OR(D5="",E5=""),"",MAX(DATE(YEAR(IF(F5<>"",F5,D5+Assumptions!$B$6)),MONTH(IF(F5<>"",F5,D5+Assumptions!$B$6)),1),Assumptions!$B$4))` |
| Writes | strings, numbers and `LocalDate` values written to `Assumptions`, both Xero sheets, `Forecast` and `Staffing`; `Staffing!P19:BN29` cleared with `setBlank()` |
| Rename | `setSheetName(1, "Forecast 2026-2027")` succeeded; `Forecast!A3` recalculated to "Year 2026-2027" |
| Recalculate on open | `setForceFormulaRecalculation(true)` persisted; after save and reopen `getForceFormulaRecalculation()` is `true` |
| Evaluation | `XSSFFormulaEvaluator.evaluateAllFormulaCells` completed without `NotImplementedException` over all 1,417 formulas |
| Styles | `Assumptions!B5` data format identical before and after; `Forecast!B26` format `#,##0.00;(#,##0.00);-` unchanged; 156 cell styles in the saved file |
| Formula preservation | before/after diff: 0 formulas changed, 0 new, 256 removed, **all 256 in `Staffing` rows 23–28**, which the test overwrote or cleared on purpose (template map §5 says these become values). Every other formula in every sheet is byte-identical. |
| Output | 40,157-byte `.xlsx` written to memory, reopened successfully |

Numerical check with the synthetic dataset used (start month November 2026, opening balance 1,000.00; two debtor rows 250.50 and 100.25 with bucket date 1 Nov 2026 in column F, one of them overdue by due date; one creditor row 75.00 bucketed to December; manual receipt 10.00 in `B25`; manual payment "Rent" 500.00 in `B56`; staffing line 2,000.00 in `Staffing!D23`):

| Cell | Evaluated | Expected by the plan's rules |
| --- | --- | --- |
| `Forecast!B5` | 1,000.00 | opening balance |
| `Forecast!B26` | 360.75 | 250.50 + 100.25 + 10.00 |
| `Forecast!C26` | 0.00 | nothing dated December |
| `Forecast!C50` | 75.00 | creditor bucketed to December by column F |
| `Forecast!B66` | 500.00 | manual payment |
| `Forecast!B53` | **2,120.00** | 2,000.00 written **plus 120.00** from the sample's untouched formula rows `Staffing!D24:D29`, which still reference the sample staff table |
| `Forecast!B68` | −1,259.25 | 1,000 + 360.75 − (0 + 2,120 + 500) |
| `Xero_Debtors!G5`, `G6` | 2026-11-01 | overdue row lands in the current month because column F carries the bucket date |

The `B53` line is the one surprise: the populator must write a value or a blank into **every** cell of `Staffing!D23:O29`, not only the rows it uses, or the sample's hourly-rate formulas leak into the total. Template map §5 already requires this; the test confirms why.

Environment note: POI logs through log4j-api. Inside Spring Boot the `log4j-to-slf4j` bridge is already on the classpath; the bare `java` run printed "Log4j API could not find a logging provider", which is harmless and will not occur in the application.

## Decision
- **confirmed**. POI 5.4.1 can do everything FR-UC04-11/12/21 need on this template: open, validate structure, write values, preserve untouched formulas and formats byte-for-byte, rename the sheet, force recalculation on open, and evaluate every formula in the workbook so the reconciliation check in template map §7 is possible server-side.
- This allows slice 9 (validator and populator) and slice 10 (export API) to proceed once gate G4 (POI dependency approval, version 5.4.1) and gate G5 (scrubbed fixture) are met.
- Implementation rules confirmed by the test: write the bucket date into column F of every Xero row; write values into all seven staffing grid rows; clear `P19:BN29`; compare `B26:M26`, `B67:M67`, `B68:M68` and `B5` after `evaluateAllFormulaCells`.

## Date
- 2026-11-10

## Evidence
- Local run output (sanitised; no client data beyond the public template layout):

```
sheets=5 names=0 formulas=1417 functions=[DATE, EDATE, IF, INDEX, MATCH, MAX, MONTH, OR, SUM, SUMIFS, YEAR]
vba=0 extLinks=0
B5=1000.00 B26=360.75 C26=0.00 B50=0.00 C50=75.00 B53=2120.00 B66=500.00 B67=2620.00 B68=-1259.25
Xero_Debtors!G5 -> 2026-11-01  G6 -> 2026-11-01  Forecast!B3 -> 2026-11-01  A3="Year 2026-2027"
reopened: formulas=1161 (before 1417) forceRecalc=true sheet1=Forecast 2026-2027 B5 fmt same=true
before=1417 after=1161 lost=256 changed=0 {Staffing lost=256}  lost rows=[23, 24, 25, 26, 27, 28]
```

- Apache POI 5.4.1 artefacts resolved from Maven Central via `./mvnw dependency:get -Dartifact=org.apache.poi:poi-ooxml:5.4.1` on 10 Nov 2026.
- Template facts cross-checked against [`docs/specs/UC4-template-map.md`](../specs/UC4-template-map.md) §1 and §6.
