import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import Forecast from "./Forecast";
import { MonthDetail } from "./MonthDetail";
import type { ForecastView } from "./contracts";
const a = { id: "10000000-0000-4000-8000-000000000001", displayName: "Alpha" };
const b = { id: "10000000-0000-4000-8000-000000000002", displayName: "Bravo" };
function data(entityId: string, year: number | null, amount: string): ForecastView {
  const startYear = year ?? 2026;
  const startMonth = `${startYear}-04`;
  return {
    entityId,
    entityName: entityId === a.id ? a.displayName : b.displayName,
    financialYearStart: startMonth,
    financialYearLabel: `${startYear}-${startYear + 1} ${amount}`,
    months: Array.from({ length: 12 }, (_, i) => ({
      month: new Date(Date.UTC(startYear, 3 + i, 1)).toISOString().slice(0, 7),
      state: "GENERATED" as const,
      generatable: false,
      generatedAt: null,
      pendingNewItems: 0,
      openingSource: null,
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
    beyondWindow: { receipts: amount, payments: "0.00" },
    exclusions: [],
    lastSuccessfulSyncAt: "2026-11-10T00:00:00Z",
    lastRun: { status: "SUCCEEDED" },
    syncStale: false,
    openingBalanceSource: "Statement",
  };
}
it("FR-UC04-05 shows only the rolling grid and keeps forecast tabs", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => data(entity, year, "0.00"));
  render(
    <Forecast
      api={api}
      profile={{ subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" }}
      entities={[a]}
      combined={false}
      entity={a}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  await screen.findByRole("button", { name: "Create forecast" });
  for (const name of ["Forecast", "Xero items", "Manual items", "Staffing", "History"])
    expect(screen.getByRole("tab", { name })).toBeTruthy();
  for (const text of [
    /Monthly forecast actions/,
    /Beyond this financial year/,
    /How this is calculated/,
    /Excluded documents/,
  ])
    expect(screen.queryByText(text)).toBeNull();
});

it("FR-UC04-13 asks for an entity instead of displaying combined forecasts", () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  render(
    <Forecast
      api={api}
      profile={{ subject: a.id, displayName: "Admin", role: "FINANCIAL_ADMIN" }}
      entities={[a]}
      combined
      entity={null}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  expect(screen.getByText(/Select an entity to view its forecast/)).toBeTruthy();
});

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
  let bYear: number | null = null;
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => {
    if (entity === a.id) return data(entity, year, "111.00");
    bYear = year;
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
    resolveB?.(data(b.id, bYear, "222.00"));
  });
  expect(await screen.findAllByText(/222\.00/)).not.toHaveLength(0);
});
it("FR-UC04-22 exposes section navigation as tabs with a labelled panel and arrow-key switching", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) =>
    data(entity, year, "111.00"),
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
  await screen.findAllByText(/111\.00/);
  const tablist = screen.getByRole("tablist", { name: "Forecast sections" });
  const forecastTab = within(tablist).getByRole("tab", { name: "Forecast" });
  const xeroTab = within(tablist).getByRole("tab", { name: "Xero items" });
  const historyTab = within(tablist).getByRole("tab", { name: "History" });
  expect(forecastTab.getAttribute("aria-selected")).toBe("true");
  expect(forecastTab.getAttribute("tabindex")).toBe("0");
  expect(xeroTab.getAttribute("aria-selected")).toBe("false");
  expect(xeroTab.getAttribute("tabindex")).toBe("-1");
  expect(screen.getByRole("tabpanel", { name: "Forecast" })).toBeTruthy();

  fireEvent.keyDown(forecastTab, { key: "ArrowRight" });
  expect(xeroTab.getAttribute("aria-selected")).toBe("true");
  expect(xeroTab).toBe(window.document.activeElement);
  expect(screen.getByRole("tabpanel", { name: "Xero items" })).toBeTruthy();

  fireEvent.keyDown(xeroTab, { key: "End" });
  expect(historyTab.getAttribute("aria-selected")).toBe("true");
  fireEvent.keyDown(historyTab, { key: "ArrowRight" });
  expect(forecastTab.getAttribute("aria-selected")).toBe("true");
  fireEvent.keyDown(forecastTab, { key: "ArrowLeft" });
  expect(historyTab.getAttribute("aria-selected")).toBe("true");
  fireEvent.keyDown(historyTab, { key: "Home" });
  expect(forecastTab.getAttribute("aria-selected")).toBe("true");
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
    let oldYear: number | null = null;
    let oldSignal: AbortSignal | undefined;
    let calls = 0;
    // The superseded request is the first call for an entity switch, and the second call (after the first load) for a period switch.
    const pendingCall = switchContext === "entity" ? 1 : 2;
    vi.spyOn(api, "forecast").mockImplementation(async (entity, year, signal) => {
      calls += 1;
      if (calls === pendingCall) {
        oldYear = year;
        oldSignal = signal;
        return new Promise<ForecastView>((resolve) => {
          finishOld = resolve;
        });
      }
      return data(entity, year, calls === 1 ? "111.00" : "222.00");
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
    if (switchContext === "entity") {
      await waitFor(() => expect(finishOld).toBeDefined());
      view.rerender(<Forecast {...common} entity={b} />);
    } else {
      await screen.findAllByText(/111\.00/);
      fireEvent.click(screen.getByRole("button", { name: "Previous financial year" }));
      await waitFor(() => expect(finishOld).toBeDefined());
      fireEvent.click(screen.getByRole("button", { name: "Next financial year" }));
    }
    await screen.findAllByText(/222\.00/);
    expect(oldSignal?.aborted).toBe(true);
    if (switchContext === "period") expect(oldYear).toBe(2025);
    await act(async () => {
      finishOld?.(data(a.id, oldYear, "333.00"));
    });
    expect(screen.queryAllByText(/333\.00/)).toHaveLength(0);
    expect(screen.getAllByText(/222\.00/)).not.toHaveLength(0);
  });
}
it("FR-UC04-20 opens a month as a full page listing Xero invoices with due dates and a total", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => {
    const view = data(entity, year, "111.00");
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
        state: "PREVIEW" as const,
        generatable: true,
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
        notes: [],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  const onBack = vi.fn();
  render(
    <MonthDetail
      api={api}
      entity={a}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject={a.id}
      onDirty={vi.fn()}
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
      selectedMonth="2026-11"
      summary={null}
      onBack={onBack}
    />,
  );
  expect(await screen.findByRole("heading", { name: /cash forecast$/ })).toBeTruthy();
  expect(screen.queryByText("12-month cash forecast · NZD, GST-inclusive")).toBeNull();
  const moneyIn = screen.getByRole("table", { name: /^Money In/ });
  expect(moneyIn.textContent).toContain("INV-0028 · City Limousines");
  expect(moneyIn.textContent).toContain("Was due 25 Nov 2026 (overdue)");
  expect(moneyIn.textContent).toContain("Total Money In");
  expect(screen.getByText("No expected money out for this month.")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Back to forecast" }));
  expect(onBack).toHaveBeenCalledOnce();
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
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => data(entity, year, "0.00"));
  vi.spyOn(api, "forecastItems").mockImplementation(
    async (_entity, month) =>
      ({
        month,
        state: "PREVIEW" as const,
        generatable: true,
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
        notes: [],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  const onBack = vi.fn();
  render(
    <MonthDetail
      api={api}
      entity={a}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject={a.id}
      onDirty={vi.fn()}
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
      selectedMonth="2026-11"
      summary={null}
      onBack={onBack}
    />,
  );
  const moneyIn = await screen.findByRole("table", { name: /^Money In/ });
  expect((moneyIn.textContent?.match(/City Limousines/g) ?? []).length).toBe(2);
  expect(moneyIn.textContent).toContain("INV-0028");
  expect(moneyIn.textContent).toContain("INV-0040");
  expect(moneyIn.textContent).toContain("120.00");
  const moneyOut = screen.getByRole("table", { name: /^Money Out/ });
  expect(moneyOut.textContent).not.toContain("Wages");
  expect(screen.getByText("No expected money out for this month.")).toBeTruthy();
});
it("states NZD once in each ledger caption, keeps two decimals and signs without repeating the currency per row, and marks overdue due dates by text and colour", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => data(entity, year, "0.00"));
  vi.spyOn(api, "forecastItems").mockImplementation(
    async (_entity, month) =>
      ({
        month,
        state: "PREVIEW" as const,
        generatable: true,
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
          {
            sourceId: "20000000-0000-4000-8000-000000000002",
            source: "DOCUMENT",
            direction: "OUT",
            category: "ACCPAY",
            number: "BILL-0007",
            label: "Basket Case",
            date: `${month}-28`,
            bucket: month,
            amount: "-40.00",
            rule: "DUE_DATE",
            overdue: false,
            splitSequence: null,
            splitCount: null,
          },
        ],
        notes: [],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  const onBack = vi.fn();
  render(
    <MonthDetail
      api={api}
      entity={a}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject={a.id}
      onDirty={vi.fn()}
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
      selectedMonth="2026-11"
      summary={null}
      onBack={onBack}
    />,
  );
  const moneyIn = await screen.findByRole("table", { name: /^Money In/ });
  const moneyOut = screen.getByRole("table", { name: /^Money Out/ });
  expect(moneyIn.querySelector("caption")?.textContent).toBe("Money In · NZD");
  expect(moneyOut.querySelector("caption")?.textContent).toBe("Money Out · NZD");
  expect(moneyIn.querySelector("tbody")?.textContent).not.toContain("NZD");
  const inRows = within(moneyIn.querySelector("tbody") as HTMLElement);
  const outRows = within(moneyOut.querySelector("tbody") as HTMLElement);
  expect(inRows.getByRole("cell", { name: "19.95" })).toHaveClass("money");
  expect(outRows.getByRole("cell", { name: "-40.00" })).toHaveClass("money");
  expect(within(moneyIn).getByText("Was due 25 Nov 2026 (overdue)")).toHaveClass(
    "forecast-overdue",
  );
  expect(within(moneyOut).getByText("Bill due 28 Nov 2026")).not.toHaveClass("forecast-overdue");
});
it("adds a transaction note from the Money In table and shows the saved preview", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  const invoiceId = "20000000-0000-4000-8000-000000000001";
  vi.spyOn(api, "forecast").mockImplementation(async (entity, year) => data(entity, year, "0.00"));
  vi.spyOn(api, "forecastItems").mockImplementation(
    async (_entity, month) =>
      ({
        month,
        state: "PREVIEW" as const,
        generatable: true,
        items: [
          {
            sourceId: invoiceId,
            source: "DOCUMENT",
            direction: "IN",
            category: "ACCREC",
            number: "INV-0017",
            label: "City Limousines",
            date: `${month}-19`,
            bucket: month,
            amount: "250.00",
            rule: "DUE_DATE",
            overdue: true,
            splitSequence: null,
            splitCount: null,
          },
        ],
        notes: [],
      }) as unknown as Awaited<ReturnType<DashboardApi["forecastItems"]>>,
  );
  const save = vi.spyOn(api, "saveForecastTransactionNote").mockImplementation(
    async (_entity, source, sourceId, note) =>
      ({
        source,
        sourceId,
        note: note || null,
        updatedAt: "2026-11-11T00:00:00Z",
      }) as unknown as Awaited<ReturnType<DashboardApi["saveForecastTransactionNote"]>>,
  );
  const onBack = vi.fn();
  render(
    <MonthDetail
      api={api}
      entity={a}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject={a.id}
      onDirty={vi.fn()}
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
      selectedMonth="2026-11"
      summary={null}
      onBack={onBack}
    />,
  );
  const moneyIn = await screen.findByRole("table", { name: /^Money In/ });
  expect(
    within(moneyIn)
      .getAllByRole("columnheader")
      .map((h) => h.textContent),
  ).toEqual(["Transaction", "Notes", "Amount"]);
  const label = "INV-0017 · City Limousines";
  fireEvent.click(within(moneyIn).getByRole("button", { name: `Add note for ${label}` }));
  fireEvent.change(within(moneyIn).getByRole("textbox", { name: `Note for ${label}` }), {
    target: { value: "  Chase by phone  " },
  });
  fireEvent.click(within(moneyIn).getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(a.id, "DOCUMENT", invoiceId, "Chase by phone"),
  );
  expect(
    await within(moneyIn).findByRole("button", { name: `Edit note for ${label}: Chase by phone` }),
  ).toBeTruthy();
});
