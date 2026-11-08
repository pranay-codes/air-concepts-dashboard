import { vi } from "vitest";
import type { AuthService } from "../auth/authService";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";

export function testApi(
  handler: (url: URL, init?: RequestInit) => unknown | Response | Promise<unknown | Response>,
) {
  const auth: AuthService = {
    getAccessToken: vi.fn().mockResolvedValue("synthetic-access"),
    requestCode: vi.fn(),
    verifyCode: vi.fn(),
    signOut: vi.fn(),
    onSessionChange: () => () => {},
  };
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
    const result = await handler(new URL(String(input), "https://api.example.invalid"), init);
    return result instanceof Response
      ? result
      : new Response(JSON.stringify(result), { headers: { "Content-Type": "application/json" } });
  });
  return { api: new DashboardApi(new ApiClient("", auth, fetcher)), fetcher };
}
