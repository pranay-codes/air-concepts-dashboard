import { fireEvent, render, screen } from "@testing-library/react";
import { it, expect, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { OpeningBalanceForm } from "./OpeningBalanceForm";

it("does not turn an unchanged carried opening into an override", () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  render(
    <OpeningBalanceForm
      api={api}
      entity={{ id: "10000000-0000-4000-8000-000000000001", displayName: "Entity" }}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject="user"
      month="2026-12"
      initial="150.50"
      requireChange
      requireAmountChange
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
      onDirty={vi.fn()}
      onClose={vi.fn()}
    />,
  );
  const save = screen.getByRole("button", { name: "Save opening balance" });
  expect(save).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Balance source (optional)"), {
    target: { value: "Statement" },
  });
  expect(save).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Opening cash balance (NZD)"), {
    target: { value: "151.00" },
  });
  expect(save).toBeEnabled();
});
