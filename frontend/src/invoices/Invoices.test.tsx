import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, invoice, profile } from "../test/fixtures";
import { Invoices } from "./Invoices";

function setup() {
  return testApi((url) =>
    url.pathname.endsWith("/staff")
      ? [{ id: profile.subject, displayName: profile.displayName }]
      : {
          items: [
            invoice({ type: url.searchParams.get("type") === "ACCPAY" ? "ACCPAY" : "ACCREC" }),
          ],
          page: 1,
          pageSize: 25,
          pageCount: 1,
          itemCount: 1,
        },
  );
}
describe("UC5 invoice controls and access", () => {
  it("FR-UC15-20_21 hides bills for Standard users and fetches the selected entity", async () => {
    const { api, fetcher } = setup();
    render(
      <Invoices
        api={api}
        entity={entities[0]!}
        profile={{ ...profile, role: "STANDARD_USER" }}
        onAccessDenied={vi.fn()}
        onSettings={vi.fn()}
      />,
    );
    expect(screen.queryByRole("tab", { name: "Supplier bills" })).not.toBeInTheDocument();
    await screen.findByRole("table", { name: "Sales invoices" });
    await waitFor(() => expect(fetcher).toHaveBeenCalled());
    expect(String(fetcher.mock.calls[0]?.[0])).toContain(`/entities/${entities[0]!.id}/invoices?`);
  });
  it("FR-UC15-15_26_27 combines debounced search, status, overdue and inclusive custom dates", async () => {
    const { api, fetcher } = setup();
    render(
      <Invoices
        api={api}
        entity={entities[0]!}
        profile={profile}
        onAccessDenied={vi.fn()}
        onSettings={vi.fn()}
      />,
    );
    await waitFor(() => expect(fetcher).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText("Search invoices"), { target: { value: "Acme" } });
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "AUTHORISED" } });
    fireEvent.click(screen.getByLabelText("Overdue only"));
    fireEvent.change(screen.getByLabelText("Date range"), { target: { value: "CUSTOM" } });
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-11-07" } });
    await waitFor(() => {
      const call = fetcher.mock.calls.at(-1);
      const url = new URL(String(call?.[0]), "https://api.example.invalid");
      expect(url.searchParams.get("search")).toBe("Acme");
      expect(url.searchParams.get("status")).toBe("AUTHORISED");
      expect(url.searchParams.get("overdue")).toBe("true");
      expect(url.searchParams.get("from")).toBe("2026-01-01");
      expect(url.searchParams.get("to")).toBe("2026-11-07");
      expect(url.searchParams.get("page")).toBe("1");
    });
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-12-01" } });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Start date must be on or before end date.",
    );
    expect(within(screen.getByRole("table")).queryByText("INV-001")).not.toBeInTheDocument();
  });
  it("FR-UC15-13_21 type switching fetches bills and permission changes remove bill data", async () => {
    const { api, fetcher } = setup();
    const view = render(
      <Invoices
        api={api}
        entity={entities[0]!}
        profile={profile}
        onAccessDenied={vi.fn()}
        onSettings={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("tab", { name: "Supplier bills" }));
    await waitFor(() =>
      expect(fetcher.mock.calls.some((call) => String(call[0]).includes("type=ACCPAY"))).toBe(true),
    );
    view.rerender(
      <Invoices
        api={api}
        entity={entities[0]!}
        profile={{ ...profile, role: "STANDARD_USER" }}
        onAccessDenied={vi.fn()}
        onSettings={vi.fn()}
      />,
    );
    expect(screen.queryByRole("tab", { name: "Supplier bills" })).not.toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Supplier bills" })).not.toBeInTheDocument();
  });
});
