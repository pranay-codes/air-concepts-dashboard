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
  let companyNotes: Record<string, string> = {};
  const grid = vi.spyOn(api, "forecastGrid").mockImplementation(async (entityId, start) => ({
    entityId,
    companyNotes,
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
        company: "Customer A",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "11111111-1111-4111-8111-111111111111",
        note: null,
        amounts: { [stepMonth(start, 1)]: "125.00" },
        companyAmounts: { [stepMonth(start, 1)]: "150.00" },
      },
      {
        direction: "OUT",
        contact: "Supplier B - BILL-456",
        company: "Supplier B",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "22222222-2222-4222-8222-222222222222",
        note: "Pay Friday",
        amounts: { [stepMonth(start, 1)]: "25.00" },
        companyAmounts: { [stepMonth(start, 1)]: "40.00" },
      },
      {
        direction: "IN",
        contact: "Customer A - INV-124",
        company: "Customer A",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "33333333-3333-4333-8333-333333333333",
        note: null,
        amounts: { [stepMonth(start, 1)]: "25.00" },
        companyAmounts: { [stepMonth(start, 1)]: "150.00" },
      },
      {
        direction: "OUT",
        contact: "Supplier B - BILL-457",
        company: "Supplier B",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "88888888-8888-4888-8888-888888888888",
        note: null,
        amounts: { [stepMonth(start, 1)]: "15.00" },
        companyAmounts: { [stepMonth(start, 1)]: "40.00" },
      },
      {
        direction: "IN",
        contact: "Existing manual",
        company: "Existing manual",
        source: "MANUAL",
        positionBefore: "company:Customer A",
        documentId: "44444444-4444-4444-8444-444444444444",
        note: null,
        amounts: { [stepMonth(start, 1)]: "10.00" },
        companyAmounts: {},
      },
      {
        direction: "IN",
        contact: "Earlier manual",
        company: "Earlier manual",
        source: "MANUAL",
        positionBefore: "manual:44444444-4444-4444-8444-444444444444:Existing manual",
        documentId: "55555555-5555-4555-8555-555555555555",
        note: null,
        amounts: { [stepMonth(start, 1)]: "5.00" },
        companyAmounts: {},
      },
      {
        direction: "IN",
        contact: "Old manual name",
        company: "Old manual name",
        source: "MANUAL",
        positionBefore: "company:Customer A",
        documentId: "44444444-4444-4444-8444-444444444444",
        note: null,
        amounts: { [stepMonth(start, 2)]: "8.00" },
        companyAmounts: {},
      },
      {
        direction: "IN",
        contact: "Unknown contact - INV-901",
        company: "Unknown contact",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "66666666-6666-4666-8666-666666666666",
        note: null,
        amounts: { [stepMonth(start, 1)]: "1.00" },
        companyAmounts: { [stepMonth(start, 1)]: "3.00" },
      },
      {
        direction: "IN",
        contact: "Unknown contact - INV-902",
        company: "Unknown contact",
        source: "DOCUMENT",
        positionBefore: null,
        documentId: "77777777-7777-4777-8777-777777777777",
        note: null,
        amounts: { [stepMonth(start, 1)]: "2.00" },
        companyAmounts: { [stepMonth(start, 1)]: "3.00" },
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
  const createManual = vi.spyOn(api, "createGridManual").mockResolvedValue({} as never);
  const saveNote = vi.spyOn(api, "saveForecastTransactionNote").mockResolvedValue({
    source: "DOCUMENT",
    sourceId: "11111111-1111-4111-8111-111111111111",
    note: "Call Monday",
    updatedAt: null,
  });
  const saveCompanyNote = vi
    .spyOn(api, "saveForecastCompanyNote")
    .mockImplementation(async (_entity, company, note) => {
      companyNotes = note ? { [company]: note } : {};
      return { company, note: note || null };
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
  const company = await screen.findByRole("button", { name: /Customer A \(2\)/ });
  expect(screen.queryByRole("button", { name: /Unknown contact \(2\)/ })).not.toBeInTheDocument();
  expect(screen.getByText("Unknown contact - INV-901")).toBeInTheDocument();
  expect(screen.getByText("Unknown contact - INV-902")).toBeInTheDocument();
  expect(screen.getByRole("row", { name: /Old manual name/ })).toHaveTextContent("8.00");
  expect(screen.getByRole("row", { name: /Existing manual/ }).nextElementSibling).toBe(
    company.closest("tr"),
  );
  expect(screen.getByRole("row", { name: /Earlier manual/ }).nextElementSibling).toBe(
    screen.getByRole("row", { name: /Existing manual/ }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Add note for Customer A company" }));
  fireEvent.change(screen.getByRole("textbox", { name: "Note for Customer A company" }), {
    target: { value: "Group note" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(saveCompanyNote).toHaveBeenCalledWith(
      "10000000-0000-4000-8000-000000000001",
      "Customer A",
      "Group note",
    ),
  );
  expect(
    await screen.findByRole("button", { name: "Edit note for Customer A company: Group note" }),
  ).toBeInTheDocument();
  onChanged.mockClear();
  expect(company).toHaveAttribute("aria-expanded", "false");
  expect(screen.queryByText("Customer A - INV-123")).not.toBeInTheDocument();
  expect(company.closest("tr")).toHaveTextContent("150.00");
  fireEvent.click(company);
  expect(company).toHaveAttribute("aria-expanded", "true");
  expect(await screen.findByText("Customer A - INV-123")).toBeInTheDocument();
  const supplier = screen.getByRole("button", { name: /Supplier B \(2\)/ });
  expect(supplier).toHaveAttribute("aria-expanded", "false");
  expect(supplier.closest("tr")).toHaveTextContent("40.00");
  expect(screen.queryByText("Supplier B - BILL-456")).not.toBeInTheDocument();
  fireEvent.click(supplier);
  expect(supplier).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByText("Supplier B - BILL-457")).toBeInTheDocument();
  expect(screen.getByText("Supplier B - BILL-456")).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: /Edit note for Supplier B.*Pay Friday/ }),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Add note for Customer A - INV-123" }));
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
  await waitFor(() => expect(grid).toHaveBeenCalledTimes(3));
  fireEvent.click(screen.getByRole("button", { name: "Add manual line to Money In" }));
  fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Deposit" } });
  fireEvent.change(screen.getByLabelText("Amount (NZD)"), { target: { value: "75.00" } });
  fireEvent.change(screen.getByLabelText("Place before"), {
    target: { value: "company:Customer A" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save line" }));
  await waitFor(() =>
    expect(createManual).toHaveBeenCalledWith(
      "10000000-0000-4000-8000-000000000001",
      expect.objectContaining({ description: "Deposit", amount: "75.00", direction: "IN" }),
      "company:Customer A",
      expect.any(AbortSignal),
    ),
  );
}, 15000);
