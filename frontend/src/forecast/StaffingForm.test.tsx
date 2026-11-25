import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { AssumptionLines } from "./AssumptionLines";

it("FR-UC04-03 saves an entity-scoped fortnightly staffing schedule", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecastStaffing").mockResolvedValue([]);
  const save = vi.spyOn(api, "saveForecastStaffing").mockResolvedValue({} as never);
  const changed = vi.fn();
  const entity = { id: "10000000-0000-4000-8000-000000000001", displayName: "Alpha" };
  render(
    <AssumptionLines
      api={api}
      entity={entity}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject="user"
      kind="staffing"
      revision={0}
      onChanged={changed}
      onAccessDenied={vi.fn()}
      onDirty={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Add staffing line" }));
  fireEvent.change(screen.getByLabelText("Staffing label"), { target: { value: "Employee" } });
  fireEvent.change(screen.getByLabelText("Hourly rate (NZD)"), { target: { value: "25" } });
  fireEvent.change(screen.getByLabelText("Standard hours per week"), { target: { value: "40" } });
  fireEvent.change(screen.getByLabelText("Payment frequency"), {
    target: { value: "FORTNIGHTLY" },
  });
  fireEvent.change(screen.getByLabelText("First scheduled payday"), {
    target: { value: "2026-11-06" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save item" }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      entity.id,
      expect.objectContaining({
        label: "Employee",
        hourlyRate: "25.00",
        weeklyHours: "40.00",
        paymentFrequency: "FORTNIGHTLY",
        firstPayday: "2026-11-06",
      }),
      undefined,
      expect.any(AbortSignal),
    ),
  );
  expect(changed).toHaveBeenCalled();
});
