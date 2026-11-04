import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchMe } from "./me";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("fetchMe", () => {
  it("sends the access token as a bearer token and returns the subject and role", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ subject: "user-123", role: "FINANCIAL_ADMIN" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const me = await fetchMe("http://localhost:8080", "token-abc", fetchSpy);

    expect(fetchSpy).toHaveBeenCalledWith("http://localhost:8080/api/v1/me", {
      headers: { Authorization: "Bearer token-abc" },
    });
    expect(me).toEqual({ subject: "user-123", role: "FINANCIAL_ADMIN" });
  });

  it("rejects an unauthorised response without echoing the token", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));

    const attempt = fetchMe("http://localhost:8080", "token-abc", fetchSpy);

    await expect(attempt).rejects.toThrow("GET /api/v1/me failed with status 401");
    await expect(attempt).rejects.not.toThrow(/token-abc/);
  });
});
