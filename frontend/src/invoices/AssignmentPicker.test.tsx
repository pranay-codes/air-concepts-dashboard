import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { invoice, STAFF_A } from "../test/fixtures";
import { AssignmentPicker } from "./AssignmentPicker";

describe("UC5 sales assignment picker", () => {
  it("FR-UC15-07_19 selects active staff or unassigned and preserves inactive history", () => {
    const assign = vi.fn();
    const record = invoice({
      owner: {
        id: "00000000-0000-4000-8000-000000000099",
        displayName: "Previous staff",
        active: false,
      },
    });
    render(
      <AssignmentPicker
        invoice={record}
        role="DIRECTOR"
        options={[{ id: STAFF_A, displayName: "Alex" }]}
        disabled={false}
        onAssign={assign}
      />,
    );
    expect(screen.getByRole("option", { name: "Previous staff (Inactive)" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Owner for INV-001"), { target: { value: STAFF_A } });
    expect(assign).toHaveBeenCalledWith(record.id, STAFF_A);
    fireEvent.change(screen.getByLabelText("Owner for INV-001"), { target: { value: "" } });
    expect(assign).toHaveBeenLastCalledWith(record.id, null);
  });
  it("FR-UC15-07_32 hides assignments for Standard users and all bills", () => {
    const view = render(
      <AssignmentPicker
        invoice={invoice()}
        role="STANDARD_USER"
        options={[]}
        disabled={false}
        onAssign={vi.fn()}
      />,
    );
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    view.rerender(
      <AssignmentPicker
        invoice={invoice({ type: "ACCPAY" })}
        role="DIRECTOR"
        options={[]}
        disabled={false}
        onAssign={vi.fn()}
      />,
    );
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });
  it("FR-UC15-19 ignores unavailable targets and pending interactions", () => {
    const assign = vi.fn();
    const view = render(
      <AssignmentPicker
        invoice={invoice()}
        role="FINANCIAL_ADMIN"
        options={[{ id: STAFF_A, displayName: "Alex" }]}
        disabled={false}
        onAssign={assign}
      />,
    );
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "not-active" } });
    expect(assign).not.toHaveBeenCalled();
    view.rerender(
      <AssignmentPicker
        invoice={invoice()}
        role="FINANCIAL_ADMIN"
        options={[{ id: STAFF_A, displayName: "Alex" }]}
        disabled
        onAssign={assign}
      />,
    );
    fireEvent.change(screen.getByRole("combobox"), { target: { value: STAFF_A } });
    expect(assign).not.toHaveBeenCalled();
  });
});
