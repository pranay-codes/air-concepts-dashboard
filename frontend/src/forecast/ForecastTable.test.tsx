import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ForecastMonth } from "./contracts";
import { ForecastTable } from "./ForecastTable";

const base = (overrides: Partial<ForecastMonth>): ForecastMonth => ({
  month: "2026-04",
  opening: null,
  receipts: null,
  payments: null,
  net: null,
  closing: null,
  receiptCount: 0,
  paymentCount: 0,
  containsOverdue: false,
  state: "NOT_GENERATED",
  generatable: false,
  generatedAt: null,
  pendingNewItems: 0,
  openingSource: null,
  ...overrides,
});

describe("FR-UC04-04 / FR-UC04-05 opening balance column", () => {
  it("shows Not set with an Edit control when no opening balance exists", () => {
    const onOpening = vi.fn();
    render(<ForecastTable months={[base({})]} onOpening={onOpening} />);
    expect(screen.getAllByText("Not set").length).toBeGreaterThan(0);
    fireEvent.click(
      screen.getAllByRole("button", { name: "Edit opening balance for April 2026" })[0]!,
    );
    expect(onOpening).toHaveBeenCalledWith(expect.objectContaining({ month: "2026-04" }));
  });

  it("always shows Edit beside a recorded amount, labelled by its source", () => {
    const onOpening = vi.fn();
    render(
      <ForecastTable
        months={[
          base({
            month: "2026-05",
            opening: "25000.00",
            openingSource: "CARRIED_FORWARD",
            state: "GENERATED",
            generatable: false,
          }),
          base({ month: "2026-11", opening: "12000.00", openingSource: "OVERRIDE" }),
        ]}
        onOpening={onOpening}
      />,
    );
    expect(screen.getAllByText("NZD 25,000.00").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Carried forward").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Override").length).toBeGreaterThan(0);
    fireEvent.click(
      screen.getAllByRole("button", { name: "Edit opening balance for November 2026" })[0]!,
    );
    expect(onOpening).toHaveBeenLastCalledWith(expect.objectContaining({ month: "2026-11" }));
  });

  it("never shows Edit for a month when no edit handler is supplied", () => {
    render(<ForecastTable months={[base({ opening: "1.00" })]} />);
    expect(screen.queryByRole("button", { name: /Edit opening balance/ })).toBeNull();
  });
});

it("FR-UC04-05 flags negative cash, keeps figures unset for ungenerated months and opens a month", () => {
  const onOpen = vi.fn();
  render(
    <ForecastTable
      months={[
        base({
          month: "2026-11",
          opening: "10.00",
          receipts: "100.00",
          payments: "200.00",
          net: "-100.00",
          closing: "-90.00",
          receiptCount: 1,
          paymentCount: 1,
          containsOverdue: true,
          state: "GENERATED",
          generatable: false,
          openingSource: "MANUAL",
        }),
        base({ month: "2026-12" }),
      ]}
      onOpen={onOpen}
      onOpening={vi.fn()}
    />,
  );
  expect(screen.getAllByText("Negative closing balance").length).toBeGreaterThan(0);
  expect(screen.getAllByText("Not generated").length).toBeGreaterThan(0);
  fireEvent.click(screen.getAllByRole("button", { name: /View .*2026/ })[0]!);
  expect(onOpen).toHaveBeenCalledWith("2026-11");
});

it("keeps every row's opening and Edit control within its own row", () => {
  render(
    <ForecastTable
      months={[base({ month: "2026-04", opening: "5.00" }), base({ month: "2026-05" })]}
      onOpening={vi.fn()}
    />,
  );
  const rows = screen.getAllByRole("row").slice(1);
  expect(within(rows[0]!).getByText("NZD 5.00")).toBeInTheDocument();
  expect(within(rows[1]!).getByText("Not set")).toBeInTheDocument();
});

it("labels a carried opening that follows a preview month as Provisional", () => {
  render(
    <ForecastTable
      months={[
        base({
          month: "2026-11",
          opening: "1000.00",
          openingSource: "MANUAL",
          state: "PREVIEW",
          generatable: true,
          closing: "1999.00",
        }),
        base({ month: "2026-12", opening: "1999.00", openingSource: "CARRIED_FORWARD" }),
      ]}
      onOpening={vi.fn()}
    />,
  );
  expect(screen.getAllByText("Provisional").length).toBeGreaterThan(0);
  expect(screen.queryByText("Carried forward")).toBeNull();
});

describe("FR-UC04-23 compact forecast table", () => {
  it("shows a dash for each figure of an ungenerated month, read as Not generated", () => {
    render(<ForecastTable months={[base({ month: "2026-04" })]} />);
    const row = screen.getAllByRole("row")[1]!;
    expect(within(row).getAllByText("—")).toHaveLength(4);
    // Status badge plus one visually hidden description per financial cell.
    expect(within(row).getAllByText("Not generated")).toHaveLength(5);
  });

  it("explains the dash in a note beneath the table", () => {
    render(<ForecastTable months={[base({})]} />);
    expect(
      screen.getByText("— Not generated: figures appear once the month is generated."),
    ).toBeInTheDocument();
  });

  it("offers a compact Generate action that keeps its full name for assistive technology", () => {
    const onGenerate = vi.fn();
    render(
      <ForecastTable
        months={[
          base({
            month: "2026-11",
            opening: "1.00",
            openingSource: "MANUAL",
            state: "PREVIEW",
            generatable: true,
            closing: "2.00",
          }),
        ]}
        onGenerate={onGenerate}
      />,
    );
    const button = screen.getByRole("button", { name: "Generate forecast for November 2026" });
    expect(button).toHaveTextContent(/^Generate$/);
    fireEvent.click(button);
    expect(onGenerate).toHaveBeenCalledWith(expect.objectContaining({ month: "2026-11" }));
  });

  it("keeps the overdue flag visible in the table", () => {
    render(<ForecastTable months={[base({ month: "2026-11", containsOverdue: true })]} />);
    expect(screen.getByText("Includes overdue cash")).toBeInTheDocument();
  });
});
