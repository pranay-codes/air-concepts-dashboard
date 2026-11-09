import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { invoice } from "../test/fixtures";
import { InvoiceList } from "./InvoiceList";

describe("UC5 reusable financial list", () => {
  it("FR-UC15-06_28_29 preserves status, overdue, exact amounts and server sort controls", () => {
    const sort = vi.fn(),
      open = vi.fn();
    render(
      <InvoiceList
        items={[invoice({ total: "9007199254740993.01" })]}
        type="ACCREC"
        sort="DATE"
        direction="DESC"
        onSort={sort}
        onOpen={open}
        selected={null}
      />,
    );
    const table = screen.getByRole("table", { name: "Sales invoices" });
    expect(within(table).getByRole("columnheader", { name: /Invoice date/ })).toHaveAttribute(
      "aria-sort",
      "descending",
    );
    expect(within(table).getByText("Authorised")).toBeInTheDocument();
    expect(within(table).getByText("Overdue")).toBeInTheDocument();
    expect(within(table).getByText(/9,007,199,254,740,993.01/)).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: /Contact/ }));
    expect(sort).toHaveBeenCalledWith("CONTACT");
    fireEvent.click(within(table).getByRole("button", { name: /INV-001/ }));
    expect(open).toHaveBeenCalledWith(invoice().id, expect.any(HTMLButtonElement));
  });
  it("FR-UC15-19 distinguishes inactive historical ownership and unassigned values", () => {
    const record = invoice({
      owner: {
        id: "00000000-0000-4000-8000-000000000099",
        displayName: "Previous staff",
        active: false,
      },
    });
    const view = render(
      <InvoiceList
        items={[record]}
        type="ACCREC"
        sort="DATE"
        direction="DESC"
        onSort={vi.fn()}
        onOpen={vi.fn()}
        selected={null}
      />,
    );
    expect(
      within(screen.getByRole("table")).getByText(/Previous staff.*Inactive/),
    ).toBeInTheDocument();
    view.rerender(
      <InvoiceList
        items={[invoice()]}
        type="ACCREC"
        sort="DATE"
        direction="DESC"
        onSort={vi.fn()}
        onOpen={vi.fn()}
        selected={null}
      />,
    );
    expect(within(screen.getByRole("table")).getByLabelText("Unassigned")).toBeInTheDocument();
  });
  it("FR-UC15-22_32 bills have no ownership or assignment controls", () => {
    render(
      <InvoiceList
        items={[invoice({ type: "ACCPAY" })]}
        type="ACCPAY"
        sort="DATE"
        direction="DESC"
        onSort={vi.fn()}
        onOpen={vi.fn()}
        selected={null}
      />,
    );
    expect(screen.getByRole("table", { name: "Supplier bills" })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Owner" })).not.toBeInTheDocument();
    expect(screen.queryByText("Owner")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Assign/ })).not.toBeInTheDocument();
  });
});
