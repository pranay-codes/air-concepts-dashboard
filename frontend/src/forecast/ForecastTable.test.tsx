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
    render(<ForecastTable months={[base({ month: "2099-04" })]} onOpening={onOpening} />);
    expect(screen.getAllByText("Not set").length).toBeGreaterThan(0);
    fireEvent.click(
      screen.getAllByRole("button", { name: "Edit opening balance for April 2099" })[0]!,
    );
    expect(onOpening).toHaveBeenCalledWith(expect.objectContaining({ month: "2099-04" }));
  });

  it("always shows Edit beside a recorded amount, labelled by its source", () => {
    const onOpening = vi.fn();
    render(
      <ForecastTable
        months={[
          base({
            month: "2099-05",
            opening: "25000.00",
            openingSource: "CARRIED_FORWARD",
            state: "GENERATED",
            generatable: false,
          }),
          base({ month: "2099-11", opening: "12000.00", openingSource: "OVERRIDE" }),
        ]}
        onOpening={onOpening}
      />,
    );
    expect(screen.getAllByText("NZD 25,000.00").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Carried forward").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Override").length).toBeGreaterThan(0);
    fireEvent.click(
      screen.getAllByRole("button", { name: "Edit opening balance for November 2099" })[0]!,
    );
    expect(onOpening).toHaveBeenLastCalledWith(expect.objectContaining({ month: "2099-11" }));
  });

  it("never shows Edit for a month when no edit handler is supplied", () => {
    render(<ForecastTable months={[base({ opening: "1.00" })]} />);
    expect(screen.queryByRole("button", { name: /Edit opening balance/ })).toBeNull();
  });
  it("keeps past opening balances read-only even with an edit handler", () => {
    render(<ForecastTable months={[base({ month: "2000-04" })]} onOpening={vi.fn()} />);
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

it("shows a provisional carried opening without a Provisional or Carried forward label", () => {
  render(
    <ForecastTable
      months={[
        base({
          month: "2026-11",
          opening: "12000.00",
          openingSource: "MANUAL",
          state: "PREVIEW",
          generatable: true,
          closing: "21705.32",
        }),
        base({ month: "2026-12", opening: "21705.32", openingSource: "CARRIED_FORWARD" }),
      ]}
      onOpening={vi.fn()}
    />,
  );
  expect(screen.queryByText("Provisional")).toBeNull();
  expect(screen.queryByText("Carried forward")).toBeNull();
  const decemberRow = screen.getAllByRole("row")[2]!;
  expect(within(decemberRow).getByText("NZD 21,705.32")).toHaveClass("amount-positive");
  expect(
    within(decemberRow).getByRole("button", { name: "Edit opening balance for December 2026" }),
  ).toBeInTheDocument();
});

it("does not label a manually entered opening as Entered", () => {
  render(
    <ForecastTable
      months={[base({ month: "2026-11", opening: "12000.00", openingSource: "MANUAL" })]}
      onOpening={vi.fn()}
    />,
  );
  expect(screen.getByText("NZD 12,000.00")).toBeInTheDocument();
  expect(screen.queryByText("Entered")).toBeNull();
});

describe("forecast financial colour coding", () => {
  it("colours opening, net and closing amounts by sign and leaves Money In and Money Out neutral", () => {
    render(
      <ForecastTable
        months={[
          base({
            month: "2026-11",
            opening: "12000.00",
            receipts: "13966.75",
            payments: "4261.43",
            net: "9705.32",
            closing: "21705.32",
            receiptCount: 1,
            paymentCount: 1,
            openingSource: "MANUAL",
            state: "GENERATED",
          }),
          base({
            month: "2026-12",
            opening: "-250.00",
            receipts: "100.00",
            payments: "-75.00",
            net: "-175.00",
            closing: "-425.00",
            receiptCount: 1,
            paymentCount: 1,
            openingSource: "CARRIED_FORWARD",
            state: "GENERATED",
          }),
        ]}
      />,
    );
    expect(screen.getByText("NZD 12,000.00")).toHaveClass("amount-positive");
    expect(screen.getByText("NZD 9,705.32")).toHaveClass("amount-positive");
    expect(screen.getByText("NZD 21,705.32")).toHaveClass("amount-positive");
    expect(screen.getByText("-NZD 250.00")).toHaveClass("amount-negative");
    expect(screen.getByText("-NZD 175.00")).toHaveClass("amount-negative");
    expect(screen.getByText("-NZD 425.00")).toHaveClass("amount-negative");
    expect(screen.getByText("NZD 13,966.75")).not.toHaveClass("amount-positive");
    expect(screen.getByText("NZD 4,261.43")).not.toHaveClass("amount-positive");
    expect(screen.getByText("NZD 100.00")).not.toHaveClass("amount-positive");
    expect(screen.getByText("-NZD 75.00")).not.toHaveClass("amount-negative");
  });

  it("leaves zero, missing and not-generated amounts in the default text colour", () => {
    render(
      <ForecastTable
        months={[
          base({
            month: "2026-11",
            opening: "0.00",
            net: "0.00",
            closing: "-0.00",
            openingSource: "MANUAL",
            state: "GENERATED",
          }),
          base({ month: "2026-12", opening: null, openingSource: null }),
        ]}
      />,
    );
    const zeros = screen.getAllByText("NZD 0.00");
    expect(zeros).toHaveLength(3);
    for (const zero of zeros) {
      expect(zero).not.toHaveClass("amount-positive");
      expect(zero).not.toHaveClass("amount-negative");
    }
    expect(screen.getByText("Not set")).not.toHaveClass("amount-negative");
    expect(screen.getByText("Not set")).not.toHaveClass("amount-positive");
  });
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
