import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { ForecastGrid, stepMonth } from "./ForecastGrid";

describe("forecast grid month navigation", () => {
  it("moves through year boundaries in either direction", () => {
    expect(stepMonth("2026-12", 1)).toBe("2027-01");
    expect(stepMonth("2026-01", -1)).toBe("2025-12");
    expect(stepMonth("2026-11", 6)).toBe("2027-05");
  });
});

it("shows customer and supplier rows and refreshes only after creation succeeds", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  const grid = vi.spyOn(api, "forecastGrid").mockImplementation(async (entityId, start) => ({
    entityId,
    months: Array.from({ length: 8 }, (_, i) => ({
      month: stepMonth(start, i),
      available: i > 0,
      generatedAt: null,
      moneyIn: i > 0 ? "125.00" : null,
      moneyOut: i > 0 ? "25.00" : null,
      opening: i > 0 ? "100.00" : null,
      closing: i === 2 ? "-10.00" : i > 0 ? "200.00" : null,
      openingSource: i > 0 ? "CARRIED_FORWARD" : null,
      openingNote: null,
    })),
    rows: [
      { direction: "IN", contact: "Customer A", amounts: { [stepMonth(start, 1)]: "125.00" } },
      { direction: "OUT", contact: "Supplier B", amounts: { [stepMonth(start, 1)]: "25.00" } },
    ],
  }));
  vi.spyOn(api, "generateForecastGrid").mockResolvedValue({
    created: [],
    refreshed: [],
    preserved: [],
    omittedCurrentItems: 0,
    changedProtectedSchedules: 0,
  });
  const onOpening = vi.fn();
  render(
    <ForecastGrid
      api={api}
      entityId="10000000-0000-4000-8000-000000000001"
      subject="user"
      revision={0}
      onOpening={onOpening}
      onAccessDenied={vi.fn()}
    />,
  );
  expect(await screen.findByText("Customer A")).toBeInTheDocument();
  expect(screen.getByText("Supplier B")).toBeInTheDocument();
  expect(screen.getAllByText(/125\.00/).length).toBeGreaterThan(0);
  expect(screen.getByText("Total Money In")).toBeInTheDocument();
  expect(screen.getByText("Total Money Out")).toBeInTheDocument();
  expect(screen.getByText("Opening balance")).toBeInTheDocument();
  expect(screen.getByText("Closing balance")).toBeInTheDocument();
  expect(screen.getByText("-NZD 10.00")).toHaveClass("amount-negative");
  expect(screen.getAllByText("NZD 200.00")[0]).toHaveClass("amount-positive");
  const openingButtons = screen.getAllByRole("button", { name: /Edit opening balance for/ });
  fireEvent.click(openingButtons[1]!);
  expect(onOpening).toHaveBeenCalledWith(expect.any(String), "100.00", null, true);
  expect(screen.getAllByText(/Not created/).length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole("button", { name: "Create forecast" }));
  await screen.findByText(/Future months have been refreshed/);
  await waitFor(() => expect(grid).toHaveBeenCalledTimes(2));
});
