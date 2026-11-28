import type { Page } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { INVOICE_A } from "./green-lane";

/** Browser-only synthetic boundary. Backend calculation/export correctness has separate tests. */
export async function forecastBoundary(page: Page) {
  const opening = new Map<string, string>();
  const excluded = new Map<string, string>();
  const assumptions = new Map<string, Record<string, unknown>[]>();
  let exports = 0;
  const fixture = await readFile(
    new URL("../../../backend/src/test/resources/forecast/template-v1.xlsx", import.meta.url),
  );
  const headers = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Expose-Headers":
      "Content-Disposition,X-Forecast-Reconciled,X-Forecast-Generated-At,X-Forecast-Warnings",
  };
  await page.route(
    /https:\/\/synthetic-api\.example\.invalid\/api\/v1\/.*forecast.*/,
    async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname;
      const entity = path.split("/")[4] ?? "";
      const method = request.method();
      if (method === "OPTIONS") {
        await route.fulfill({
          status: 204,
          headers: {
            ...headers,
            "Access-Control-Allow-Headers": "authorization,content-type",
            "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,OPTIONS",
          },
        });
        return;
      }
      const parts = new Intl.DateTimeFormat("en-NZ", {
        timeZone: "Pacific/Auckland",
        year: "numeric",
        month: "2-digit",
      }).formatToParts(new Date());
      const current = `${parts.find((p) => p.type === "year")?.value}-${parts.find((p) => p.type === "month")?.value}`;
      // The backend's financial year starts in April; the query carries the four-digit start year.
      const [currentYear = 2026, currentMonth = 11] = current.split("-").map(Number);
      const requestedYear = url.searchParams.get("financialYearStart");
      const fyYear = requestedYear ?? String(currentMonth >= 4 ? currentYear : currentYear - 1);
      const start = `${fyYear}-04`;
      const [year = "2026", month = "04"] = start.split("-");
      const monthKey = (i: number) =>
        new Date(Date.UTC(Number(year), Number(month) - 1 + i, 1)).toISOString().slice(0, 7);
      // Fields every month row carries in the current contract.
      const monthState = {
        state: "PREVIEW",
        generatable: false,
        generatedAt: null,
        pendingNewItems: 0,
        openingSource: null,
      };
      const adjustment = {
        documentId: INVOICE_A,
        expectedDateOverride: null,
        excluded: excluded.has(entity),
        exclusionReason: excluded.get(entity) ?? null,
        dateConflictSince: null,
        previousXeroDate: null,
        newXeroDate: null,
        lines: [],
      };
      const document = {
        id: INVOICE_A,
        type: "ACCREC",
        number: "INV-001",
        contact: "Forecast customer",
        status: "AUTHORISED",
        outstanding: true,
        dueDate: `${start}-15`,
        xeroExpectedDate: null,
        amountDue: "115.00",
        adjustment,
      };
      let result: unknown;
      if (path.endsWith("/grid/generate")) {
        result = {
          created: [],
          refreshed: [],
          preserved: [],
          omittedCurrentItems: 0,
          changedProtectedSchedules: 0,
        };
      } else if (path.endsWith("/grid")) {
        const requested = url.searchParams.get("startMonth") ?? current;
        const [y = 2026, m = 1] = requested.split("-").map(Number);
        const months = Array.from({ length: 8 }, (_, i) => ({
          month: new Date(Date.UTC(y, m - 1 + i, 1)).toISOString().slice(0, 7),
          available: true,
          generatedAt: "2026-11-10T00:00:00Z",
          moneyIn: "115.00",
          moneyOut: "0.00",
          staffingExpenses: "0.00",
          opening: opening.get(entity) ?? null,
          closing: opening.has(entity) ? (Number(opening.get(entity)) + 115).toFixed(2) : null,
          openingSource: opening.has(entity) ? "ENTERED" : null,
          openingNote: opening.has(entity) ? "Synthetic statement" : null,
        }));
        result = {
          entityId: entity,
          companyNotes: {},
          months,
          rows: [
            {
              direction: "IN",
              contact: "Forecast customer",
              company: "Forecast customer",
              source: "DOCUMENT",
              positionBefore: null,
              documentId: INVOICE_A,
              note: null,
              amounts: { [months[1]!.month]: "115.00" },
              companyAmounts: { [months[1]!.month]: "115.00" },
            },
          ],
        };
      } else if (path.endsWith("/opening-balance")) {
        if (method === "PUT") opening.set(entity, String(request.postDataJSON().openingBalance));
        result = { openingBalance: opening.get(entity) ?? null, source: "Synthetic statement" };
      } else if (path.endsWith("/adjustment")) {
        const change = request.postDataJSON();
        if (change.excluded === true) excluded.set(entity, String(change.exclusionReason));
        if (change.excluded === false) excluded.delete(entity);
        result = {
          ...adjustment,
          excluded: excluded.has(entity),
          exclusionReason: excluded.get(entity) ?? null,
        };
      } else if (path.endsWith("/export")) {
        exports++;
        await route.fulfill({
          headers: {
            ...headers,
            "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "Content-Disposition": `attachment; filename="forecast-synthetic-${start}-20261110-1000.xlsx"`,
            "X-Forecast-Reconciled": "true",
            "X-Forecast-Generated-At": new Date().toISOString(),
            "X-Forecast-Warnings": "%5B%5D",
          },
          body: fixture,
        });
        return;
      } else if (path.includes("/months/")) {
        const selectedMonth = path.split("/").at(-2) ?? start;
        result = {
          month: selectedMonth,
          state: "PREVIEW",
          generatable: false,
          notes: [],
          items: excluded.has(entity)
            ? []
            : [
                {
                  sourceId: INVOICE_A,
                  source: "DOCUMENT",
                  direction: "IN",
                  category: "ACCREC",
                  number: "INV-001",
                  label: "Forecast customer",
                  date: `${selectedMonth}-15`,
                  bucket: selectedMonth,
                  amount: "115.00",
                  rule: "DUE_DATE",
                  overdue: false,
                  splitSequence: null,
                  splitCount: null,
                },
              ],
        };
      } else if (path.endsWith(`/documents/${INVOICE_A}`)) result = document;
      else if (path.endsWith("/documents"))
        result = {
          items: url.searchParams.get("type") === "ACCPAY" ? [] : [document],
          page: 1,
          pageSize: 25,
          itemCount: url.searchParams.get("type") === "ACCPAY" ? 0 : 1,
        };
      else if (path.includes("/manual-items") || path.includes("/staffing")) {
        const kind = path.includes("/manual-items") ? "manual-items" : "staffing";
        const key = `${entity}:${kind}`;
        const rows = assumptions.get(key) ?? [];
        assumptions.set(key, rows);
        const suffix = path.split(`/${kind}`)[1] ?? "";
        if (method === "POST" && !suffix) {
          const input = request.postDataJSON();
          const row = { ...input, id: crypto.randomUUID(), entityId: entity, active: true };
          rows.push(row);
          result = row;
        } else if (method === "PATCH" || suffix.endsWith("/deactivate")) {
          const row = rows.find((r) => r.id === suffix.split("/")[1]);
          if (!row) throw new Error("Synthetic assumption not found");
          Object.assign(
            row,
            suffix.endsWith("/deactivate") ? { active: false } : request.postDataJSON(),
          );
          result = row;
        } else
          result = rows.filter(
            (r) => r.active || url.searchParams.get("includeInactive") === "true",
          );
      } else if (path.endsWith("/combined")) {
        const months = Array.from({ length: 12 }, (_, i) => ({
          month: monthKey(i),
          opening: null,
          closing: null,
          receipts: "0.00",
          payments: "0.00",
          net: "0.00",
          receiptCount: 0,
          paymentCount: 0,
          containsOverdue: false,
          ...monthState,
        }));
        const ids = (url.searchParams.get("entityIds") ?? "").split(",");
        result = {
          financialYearStart: start,
          financialYearLabel: `${fyYear}-${Number(fyYear) + 1}`,
          months,
          complete: false,
          entities: ids.map((id) => ({
            entityId: id,
            entityName: `Synthetic ${id.slice(-1)}`,
            months,
            complete: false,
            exceptions: ["Opening balance not recorded"],
          })),
        };
      } else if (path.endsWith("/sync")) result = [];
      else if (path.endsWith("/audit")) result = { items: [], page: 1, pageSize: 25, itemCount: 0 };
      else {
        let balance = opening.has(entity) ? Number(opening.get(entity)) : null;
        const receipt = excluded.has(entity) ? 0 : 115;
        const months = Array.from({ length: 12 }, (_, i) => {
          const initial = balance;
          if (balance !== null) balance += i === 0 ? receipt : 0;
          return {
            month: monthKey(i),
            opening: initial === null ? null : initial.toFixed(2),
            receipts: (i === 0 ? receipt : 0).toFixed(2),
            payments: "0.00",
            net: (i === 0 ? receipt : 0).toFixed(2),
            closing: balance === null ? null : balance.toFixed(2),
            receiptCount: i === 0 && !excluded.has(entity) ? 1 : 0,
            paymentCount: 0,
            containsOverdue: false,
            ...monthState,
          };
        });
        result = {
          entityId: entity,
          entityName: "Synthetic",
          financialYearStart: start,
          financialYearLabel: `${fyYear}-${Number(fyYear) + 1}`,
          months,
          totals: {
            receipts: receipt.toFixed(2),
            payments: "0.00",
            closing: balance === null ? null : balance.toFixed(2),
          },
          exceptions: opening.has(entity)
            ? []
            : [
                {
                  kind: "NO_OPENING_BALANCE",
                  documentId: null,
                  message: "Enter an opening balance for this forecast period.",
                },
              ],
          beyondWindow: { receipts: "0.00", payments: "0.00" },
          exclusions: excluded.has(entity)
            ? [{ documentId: INVOICE_A, number: "INV-001", reason: excluded.get(entity) }]
            : [],
          lastSuccessfulSyncAt: new Date().toISOString(),
          lastRun: { status: "SUCCEEDED", finishedAt: new Date().toISOString(), errorCode: null },
          syncStale: false,
          openingBalanceSource: opening.has(entity) ? "Synthetic statement" : null,
        };
      }
      await route.fulfill({ headers, json: result });
    },
  );
  return {
    opening,
    excluded,
    get exports() {
      return exports;
    },
  };
}
