import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { ExcelExportDialog } from "./ExcelExportDialog";
it("FR-UC04-19 prompts only after success and never automatically downloads", async () => {
  const api = new DashboardApi(
    new ApiClient("", {
      getAccessToken: async () => null,
      requestCode: vi.fn(),
      verifyCode: vi.fn(),
      signOut: vi.fn(),
      onSessionChange: () => () => {},
    }),
  );
  vi.spyOn(api, "exportForecast").mockResolvedValue({
    blob: new Blob(["synthetic"]),
    filename: "forecast-services-2026-11-20261110-1000.xlsx",
    reconciled: true,
    warnings: [],
    generatedAt: "2026-11-10T00:00:00Z",
  });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  render(
    <ExcelExportDialog
      api={api}
      entity={{ id: "10000000-0000-4000-8000-000000000001", displayName: "Synthetic" }}
      financialYear={2026}
      financialYearLabel="2026-2027"
      subject="synthetic"
      onChanged={vi.fn()}
      onDirty={vi.fn()}
      onAccessDenied={vi.fn()}
      onClose={vi.fn()}
    />,
  );
  expect(screen.queryByRole("button", { name: "Download" })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Forecast template (.xlsx)"), {
    target: { files: [new File(["PK"], "template.xlsx")] },
  });
  fireEvent.submit(screen.getByRole("button", { name: "Generate workbook" }).closest("form")!);
  expect(await screen.findByRole("button", { name: "Download" })).toBeInTheDocument();
  expect(click).not.toHaveBeenCalled();
  click.mockRestore();
});
