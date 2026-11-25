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
      staffingExpenses: i > 0 ? "300.00" : null,
      opening: i > 0 ? "100.00" : null,
      closing: i === 2 ? "-10.00" : i > 0 ? "200.00" : null,
      openingSource: i > 0 ? "CARRIED_FORWARD" : null,
      openingNote: null,
    })),
    rows: [
      {
        direction: "IN",
        contact: "Customer A - INV-123",
        documentId: "11111111-1111-4111-8111-111111111111",
        note: null,
        amounts: { [stepMonth(start, 1)]: "125.00" },
      },
      {
        direction: "OUT",
        contact: "Supplier B - BILL-456",
        documentId: "22222222-2222-4222-8222-222222222222",
        note: "Pay Friday",
        amounts: { [stepMonth(start, 1)]: "25.00" },
      },
    ],
  }));
  vi.spyOn(api, "generateForecastGrid").mockResolvedValue({
    created: [],
    refreshed: [],
    preserved: [],
    omittedCurrentItems: 0,
    changedProtectedSchedules: 0,
  });
  const save = vi.spyOn(api, "saveForecastOpening").mockResolvedValue({} as never);
  const saveNote = vi.spyOn(api, "saveForecastTransactionNote").mockResolvedValue({
    source: "DOCUMENT",
    sourceId: "11111111-1111-4111-8111-111111111111",
    note: "Call Monday",
    updatedAt: null,
  });
  const onChanged = vi.fn();
  render(
    <ForecastGrid
      api={api}
      entityId="10000000-0000-4000-8000-000000000001"
      subject="user"
      revision={0}
      onAccessDenied={vi.fn()}
      onChanged={onChanged}
    />,
  );
  expect(await screen.findByText("Customer A - INV-123")).toBeInTheDocument();
  expect(screen.getByText("Supplier B - BILL-456")).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: /Edit note for Supplier B.*Pay Friday/ }),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: /Add note for Customer A/ }));
  fireEvent.change(screen.getByRole("textbox", { name: /Note for Customer A/ }), {
    target: { value: "Call Monday" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(saveNote).toHaveBeenCalledWith(
      "10000000-0000-4000-8000-000000000001",
      "DOCUMENT",
      "11111111-1111-4111-8111-111111111111",
      "Call Monday",
    ),
  );
  expect(
    await screen.findByRole("button", { name: /Edit note for Customer A.*Call Monday/ }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: /Edit note for Supplier B.*Pay Friday/ }),
  ).toBeInTheDocument();
  expect(screen.getAllByText(/125\.00/).length).toBeGreaterThan(0);
  expect(screen.getByText("Total Money In")).toBeInTheDocument();
  expect(screen.getByText("Total Money Out")).toBeInTheDocument();
  const staffing = screen.getByRole("row", { name: /Staffing Expenses/ });
  for (const name of [/Customer A - INV-123/, /Supplier B - BILL-456/]) {
    const cells = screen.getByRole("row", { name }).querySelectorAll("td");
    expect(cells[0]).toHaveTextContent(/^—$/);
    expect(cells[2]).toHaveTextContent(/^—$/);
  }
  expect(staffing.querySelectorAll("td")[0]).toHaveTextContent(/^—$/);
  expect(staffing).toHaveTextContent("300.00");
  expect(staffing.nextElementSibling).toHaveTextContent("Total Money Out");
  expect(screen.getByText("Opening balance")).toBeInTheDocument();
  expect(screen.getByText("Closing balance")).toBeInTheDocument();
  expect(screen.getByText("-10.00")).toHaveClass("amount-negative");
  expect(screen.getAllByText("200.00")[0]).toHaveClass("amount-positive");
  const openingButtons = screen.getAllByRole("button", { name: /Edit opening balance for/ });
  fireEvent.click(openingButtons[1]!);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  const input = screen.getByRole("textbox", { name: /Opening balance for/ });
  expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  fireEvent.change(input, { target: { value: "invalid" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Enter a cash balance");
  expect(save).not.toHaveBeenCalled();
  fireEvent.change(input, { target: { value: "150.00" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      "10000000-0000-4000-8000-000000000001",
      stepMonth(grid.mock.calls[0]![1], 1),
      "150.00",
      "",
      expect.any(AbortSignal),
    ),
  );
  expect(onChanged).toHaveBeenCalledOnce();
  expect(screen.queryByRole("columnheader", { name: "Contact" })).not.toBeInTheDocument();
  expect(screen.queryByText("Carried forward")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Create forecast" }));
  await screen.findByText(/Future months have been refreshed/);
  await waitFor(() => expect(grid).toHaveBeenCalledTimes(2));
});
