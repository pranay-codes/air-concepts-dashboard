import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { it, expect, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { ManualGridActions } from "./ManualGridActions";

it("FR-UC04-03 edits and confirms removal of a manual line", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  const item = {
    id: "item",
    description: "Airtime",
    amount: "40.00",
    direction: "OUT",
    category: "OTHER_PAYMENT",
    recurrence: "MONTHLY",
    startMonth: "2026-11",
    endMonth: null,
    dayOfMonth: 1,
    active: true,
  };
  vi.spyOn(api, "forecastManualItems").mockResolvedValue([item]);
  const save = vi.spyOn(api, "saveForecastManual").mockResolvedValue(item);
  const remove = vi
    .spyOn(api, "deactivateForecastManual")
    .mockResolvedValue({ ...item, active: false });
  const onSaved = vi.fn();
  render(
    <ManualGridActions
      api={api}
      entityId="entity"
      itemId="item"
      label="Airtime"
      onSaved={onSaved}
      onAccessDenied={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Edit Airtime" }));
  fireEvent.change(await screen.findByLabelText("Description"), { target: { value: "Phone" } });
  fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      "entity",
      expect.objectContaining({ description: "Phone", amount: "40.00" }),
      "item",
      expect.any(AbortSignal),
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "Remove Airtime" }));
  expect(await screen.findByText(/Past months will be kept/)).toBeInTheDocument();
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Remove Airtime" }));
  await screen.findByText(/Past months will be kept/);
  fireEvent.click(screen.getByRole("button", { name: "Remove line" }));
  await waitFor(() =>
    expect(remove).toHaveBeenCalledWith("entity", "item", expect.any(AbortSignal)),
  );
  expect(onSaved).toHaveBeenCalledTimes(2);
});

it("FR-UC04-03 does not edit or remove an inactive manual line", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "forecastManualItems").mockResolvedValue([]);
  const remove = vi.spyOn(api, "deactivateForecastManual");
  render(
    <ManualGridActions
      api={api}
      entityId="entity"
      itemId="item"
      label="Old line"
      onSaved={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Remove Old line" }));
  expect(await screen.findByText(/no longer active/)).toBeInTheDocument();
  expect(remove).not.toHaveBeenCalled();
});
