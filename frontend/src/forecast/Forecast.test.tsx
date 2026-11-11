import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import Forecast from "./Forecast";
import type { ForecastView } from "./contracts";
const a = { id: "10000000-0000-4000-8000-000000000001", displayName: "Alpha" };
const b = { id: "10000000-0000-4000-8000-000000000002", displayName: "Bravo" };
function data(entityId: string, startMonth: string, amount: string): ForecastView {
  const [year = "2026", month = "11"] = startMonth.split("-");
  return {
    entityId,
    entityName: entityId === a.id ? a.displayName : b.displayName,
    startMonth,
    months: Array.from({ length: 12 }, (_, i) => ({
      month: new Date(Date.UTC(Number(year), Number(month) - 1 + i, 1)).toISOString().slice(0, 7),
      opening: amount,
      receipts: "0.00",
      payments: "0.00",
      net: "0.00",
      closing: amount,
      receiptCount: 0,
      paymentCount: 0,
      containsOverdue: false,
    })),
    totals: { receipts: "0.00", payments: "0.00", closing: amount },
    exceptions: [],
    beyondWindow: { receipts: "0.00", payments: "0.00" },
    exclusions: [],
    lastSuccessfulSyncAt: "2026-11-10T00:00:00Z",
    lastRun: { status: "SUCCEEDED" },
    syncStale: false,
    openingBalanceSource: "Statement",
  };
}
it("FR-UC04-13 clears the previous entity synchronously while the next entity is loading", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  let resolveB: ((value: ForecastView) => void) | undefined;
  let bMonth = "";
  vi.spyOn(api, "forecast").mockImplementation(async (entity, month) => {
    if (entity === a.id) return data(entity, month, "111.00");
    bMonth = month;
    return new Promise<ForecastView>((resolve) => {
      resolveB = resolve;
    });
  });
  const common = {
    api,
    profile: { subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" as const },
    entities: [a, b],
    combined: false,
    onDirty: vi.fn(),
    onAccessDenied: vi.fn(),
  };
  const view = render(<Forecast {...common} entity={a} />);
  await screen.findAllByText(/111\.00/);
  view.rerender(<Forecast {...common} entity={b} />);
  expect(screen.queryAllByText(/111\.00/)).toHaveLength(0);
  await waitFor(() => expect(resolveB).toBeDefined());
  await act(async () => {
    resolveB?.(data(b.id, bMonth, "222.00"));
  });
  expect(await screen.findAllByText(/222\.00/)).not.toHaveLength(0);
});
for (const switchContext of ["entity", "period"] as const) {
  it(`FR-UC04-13_05 aborts ${switchContext} requests and discards their late responses`, async () => {
    const api = new DashboardApi(
      new ApiClient("", {
        getAccessToken: async () => null,
        requestCode: vi.fn(),
        verifyCode: vi.fn(),
        signOut: vi.fn(),
        onSessionChange: () => () => {},
      }),
    );
    let finishOld: ((value: ForecastView) => void) | undefined;
    let oldMonth = "";
    let oldSignal: AbortSignal | undefined;
    let calls = 0;
    vi.spyOn(api, "forecast").mockImplementation(async (entity, month, signal) => {
      if (calls++ === 0) {
        oldMonth = month;
        oldSignal = signal;
        return new Promise<ForecastView>((resolve) => {
          finishOld = resolve;
        });
      }
      return data(entity, month, "222.00");
    });
    const common = {
      api,
      profile: { subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" as const },
      entities: [a, b],
      combined: false,
      onDirty: vi.fn(),
      onAccessDenied: vi.fn(),
    };
    const view = render(<Forecast {...common} entity={a} />);
    await waitFor(() => expect(finishOld).toBeDefined());
    if (switchContext === "entity") view.rerender(<Forecast {...common} entity={b} />);
    else {
      const [year, month] = oldMonth.split("-").map(Number);
      const previous = new Date(Date.UTC(year!, month! - 2, 1)).toISOString().slice(0, 7);
      fireEvent.change(screen.getByLabelText("Forecast start month"), {
        target: { value: previous },
      });
    }
    await screen.findAllByText(/222\.00/);
    expect(oldSignal?.aborted).toBe(true);
    await act(async () => {
      finishOld?.(data(a.id, oldMonth, "111.00"));
    });
    expect(screen.queryAllByText(/111\.00/)).toHaveLength(0);
    expect(screen.getAllByText(/222\.00/)).not.toHaveLength(0);
  });
}
it("FR-UC04-20 opens a month as a full page listing Xero invoices with due dates and a Xero subtotal", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, month) => {
    const view = data(entity, month, "111.00");
    return {
      ...view,
      months: view.months.map((row, index) =>
        index === 0 ? { ...row, receipts: "19.95", closing: "130.95" } : row,
      ),
    };
  });
  vi.spyOn(api, "forecastItems").mockImplementation(
    async (_entity, month) =>
      ({
        month,
        items: [
          {
            sourceId: "20000000-0000-4000-8000-000000000001",
            source: "DOCUMENT",
            direction: "IN",
            category: "ACCREC",
            number: "INV-0028",
            label: "City Limousines",
            date: `${month}-25`,
            bucket: month,
            amount: "19.95",
            rule: "DUE_DATE",
            overdue: true,
            splitSequence: null,
            splitCount: null,
          },
        ],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  render(
    <Forecast
      api={api}
      profile={{ subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" as const }}
      entities={[a, b]}
      combined={false}
      entity={a}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  const [firstMonth] = await screen.findAllByRole("button", { name: /^View / });
  fireEvent.click(firstMonth!);
  expect(await screen.findByRole("heading", { name: /cash forecast$/ })).toBeTruthy();
  expect(screen.queryByText("12-month cash forecast · NZD, GST-inclusive")).toBeNull();
  const moneyIn = screen.getByRole("table", { name: /^Money in/ });
  expect(moneyIn.textContent).toContain("INV-0028 · City Limousines");
  expect(moneyIn.textContent).toContain("Was due 25 Nov 2026 (overdue)");
  expect(moneyIn.textContent).toContain("Total Xero money in");
  expect(screen.getByText("No expected payments for this month.")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Back to forecast" }));
  expect(await screen.findByText("12-month cash forecast · NZD, GST-inclusive")).toBeTruthy();
});
it("FR-UC04-20 keeps two invoices for the same debtor as separate rows, and excludes staffing from the ledger", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, month) =>
    data(entity, month, "0.00"),
  );
  vi.spyOn(api, "forecastItems").mockImplementation(
    async (_entity, month) =>
      ({
        month,
        items: [
          {
            sourceId: "20000000-0000-4000-8000-000000000001",
            source: "DOCUMENT",
            direction: "IN",
            category: "ACCREC",
            number: "INV-0028",
            label: "City Limousines",
            date: `${month}-10`,
            bucket: month,
            amount: "19.95",
            rule: "DUE_DATE",
            overdue: false,
            splitSequence: null,
            splitCount: null,
          },
          {
            sourceId: "20000000-0000-4000-8000-000000000002",
            source: "DOCUMENT",
            direction: "IN",
            category: "ACCREC",
            number: "INV-0040",
            label: "City Limousines",
            date: `${month}-20`,
            bucket: month,
            amount: "100.05",
            rule: "DUE_DATE",
            overdue: true,
            splitSequence: null,
            splitCount: null,
          },
          {
            sourceId: "30000000-0000-4000-8000-000000000003",
            source: "STAFFING",
            direction: "OUT",
            category: "STAFFING",
            number: null,
            label: "Wages, salaried staff",
            date: `${month}-15`,
            bucket: month,
            amount: "9800.00",
            rule: "STAFFING",
            overdue: false,
            splitSequence: null,
            splitCount: null,
          },
        ],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  render(
    <Forecast
      api={api}
      profile={{ subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" as const }}
      entities={[a, b]}
      combined={false}
      entity={a}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  const [firstMonth] = await screen.findAllByRole("button", { name: /^View / });
  fireEvent.click(firstMonth!);
  const moneyIn = await screen.findByRole("table", { name: /^Money in/ });
  expect((moneyIn.textContent?.match(/City Limousines/g) ?? []).length).toBe(2);
  expect(moneyIn.textContent).toContain("INV-0028");
  expect(moneyIn.textContent).toContain("INV-0040");
  expect(moneyIn.textContent).toContain("120.00");
  const moneyOut = screen.getByRole("table", { name: /^Money out/ });
  expect(moneyOut.textContent).not.toContain("Wages");
  expect(screen.getByText("No expected payments for this month.")).toBeTruthy();
});
