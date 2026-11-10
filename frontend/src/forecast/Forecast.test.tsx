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
