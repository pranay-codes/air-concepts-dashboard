import { expect, it, vi } from "vitest";
import type { AuthService } from "../auth/authService";
import { ApiClient } from "./client";
const auth: AuthService = {
  getAccessToken: async () => "synthetic",
  requestCode: vi.fn(),
  verifyCode: vi.fn(),
  signOut: vi.fn(),
  onSessionChange: () => () => {},
};
it("FR-UC04-19 uploads multipart without a JSON content type and retains download metadata", async () => {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
    new Response(new Uint8Array([80, 75]), {
      headers: {
        "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition":
          'attachment; filename="forecast-services-2026-11-20261110-1000.xlsx"',
        "X-Forecast-Reconciled": "false",
        "X-Forecast-Warnings": encodeURIComponent(JSON.stringify(["Review the closing balance."])),
        "X-Forecast-Generated-At": "2026-11-10T00:00:00Z",
      },
    }),
  );
  const client = new ApiClient("", auth, fetcher);
  const result = await client.forecastExport(
    "/api/v1/entities/example/forecast/export",
    new File(["PK"], "template.xlsx"),
  );
  expect(result.reconciled).toBe(false);
  expect(result.warnings).toEqual(["Review the closing balance."]);
  expect(fetcher.mock.calls[0]?.[1]?.body).toBeInstanceOf(FormData);
  expect(fetcher.mock.calls[0]?.[1]?.headers).not.toHaveProperty("Content-Type");
});
