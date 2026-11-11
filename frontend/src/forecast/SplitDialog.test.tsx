import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { AdjustmentDialog } from "./AdjustmentDialog";
it("FR-UC04-15 blocks a split schedule whose amounts differ from the outstanding balance", () => {
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
    <AdjustmentDialog
      api={api}
      entity={{ id: "10000000-0000-4000-8000-000000000001", displayName: "Synthetic" }}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject="synthetic"
      onChanged={vi.fn()}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
      onClose={vi.fn()}
      mode="split"
      document={{
        id: "10000000-0000-4000-8000-000000000002",
        type: "ACCREC",
        number: "INV-1",
        contact: "Synthetic",
        status: "AUTHORISED",
        outstanding: true,
        dueDate: "2026-11-15",
        xeroExpectedDate: null,
        amountDue: "100.00",
        adjustment: {
          documentId: "10000000-0000-4000-8000-000000000002",
          expectedDateOverride: null,
          excluded: false,
          exclusionReason: null,
          dateConflictSince: null,
          previousXeroDate: null,
          newXeroDate: null,
          lines: [],
        },
      }}
    />,
  );
  fireEvent.change(screen.getByLabelText("Split 1 amount (NZD)"), { target: { value: "99.00" } });
  expect(screen.getByRole("button", { name: "Save schedule" })).toBeDisabled();
  expect(screen.getByText(/Split amounts must equal/)).toBeInTheDocument();
});
