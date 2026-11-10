import { expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { DashboardApi } from "./dashboard";
const a = "10000000-0000-4000-8000-000000000001",
  b = "10000000-0000-4000-8000-000000000002";
const months = () =>
  Array.from({ length: 12 }, (_, i) => ({
    month: new Date(Date.UTC(2026, 10 + i, 1)).toISOString().slice(0, 7),
    opening: "100.00",
    closing: "100.00",
    receipts: "0.00",
    payments: "0.00",
    net: "0.00",
    receiptCount: 0,
    paymentCount: 0,
    containsOverdue: false,
  }));
function api(body: unknown) {
  return new DashboardApi(
    new ApiClient(
      "",
      {
        getAccessToken: async () => "synthetic",
        requestCode: vi.fn(),
        verifyCode: vi.fn(),
        signOut: vi.fn(),
        onSessionChange: () => () => {},
      },
      async () =>
        new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }),
    ),
  );
}
it("FR-UC04-13 rejects a duplicate entity summary instead of silently omitting an authorised entity", async () => {
  const summary = {
    entityId: a,
    entityName: "Synthetic",
    months: months(),
    exceptions: [],
    complete: true,
  };
  await expect(
    api({
      startMonth: "2026-11",
      months: months(),
      entities: [summary, summary],
      complete: true,
    }).combinedForecast([a, b], "2026-11"),
  ).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
it("FR-UC04-05 rejects month rows that do not match the selected 12-month period", async () => {
  const rows = months();
  rows[0] = { ...rows[0]!, month: "2026-10" };
  const body = {
    entityId: a,
    entityName: "Synthetic",
    startMonth: "2026-11",
    months: rows,
    totals: { receipts: "0.00", payments: "0.00", closing: "100.00" },
    beyondWindow: { receipts: "0.00", payments: "0.00" },
    exceptions: [],
    exclusions: [],
    lastSuccessfulSyncAt: null,
    lastRun: null,
    syncStale: true,
    openingBalanceSource: null,
  };
  await expect(api(body).forecast(a, "2026-11")).rejects.toMatchObject({
    code: "INVALID_RESPONSE",
  });
});
