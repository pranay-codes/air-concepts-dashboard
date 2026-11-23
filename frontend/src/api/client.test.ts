import { describe, expect, it, vi } from "vitest";
import type { AuthService } from "../auth/authService";
import { ApiClient, ApiError } from "./client";

function auth(): AuthService {
  return {
    getAccessToken: vi.fn().mockResolvedValue("synthetic-access"),
    requestCode: vi.fn(),
    verifyCode: vi.fn(),
    signOut: vi.fn(),
    onSessionChange: () => () => {},
  };
}
function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
const isString = (value: unknown): value is string => typeof value === "string";

describe("UC5 authenticated API boundary", () => {
  it("GL-STATUS-05 explains the reserved Xero daily budget", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(json({ code: "XERO_DAILY_BUDGET_RESERVED" }, 429));
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/entities", isString)).rejects.toThrow(
      "Xero's daily request allowance is nearly used. Try again later.",
    );
  });
  it("GL-STATUS-01 explains a partial invoice refresh", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(json({ code: "INVOICE_SYNC_PARTIAL" }, 409));
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/entities", isString)).rejects.toThrow(
      "Some invoice checks could not be completed. Retry or ask an administrator to review.",
    );
  });
  it("FR-UC15-05 calls fetch without binding the API client as its browser receiver", async () => {
    const fetcher: typeof fetch = async function (this: unknown) {
      if (this !== undefined) throw new TypeError("Illegal invocation");
      return json("confirmed");
    };
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/me", isString)).resolves.toBe("confirmed");
  });
  it("FR-UC15-18 obtains a fresh session token per request and sends it only in a bearer header", async () => {
    const service = auth();
    vi.mocked(service.getAccessToken)
      .mockResolvedValueOnce("first-synthetic-access")
      .mockResolvedValueOnce("second-synthetic-access");
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => json("confirmed"));
    const api = new ApiClient("https://api.example.invalid", service, fetcher);
    await api.json("/api/v1/me", isString);
    await api.json("/api/v1/entities", isString);
    const first = fetcher.mock.calls[0],
      second = fetcher.mock.calls[1];
    expect(first?.[0]).toBe("https://api.example.invalid/api/v1/me");
    expect(first?.[1]).toMatchObject({
      cache: "no-store",
      credentials: "omit",
      headers: { Authorization: "Bearer first-synthetic-access" },
    });
    expect(second?.[1]).toMatchObject({
      headers: { Authorization: "Bearer second-synthetic-access" },
    });
    expect(first?.[1]?.body).toBeUndefined();
  });

  it("FR-UC15-14 preserves safe provider status/retry while discarding raw details", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      json(
        {
          code: "XERO_RATE_LIMITED",
          upstreamStatus: 429,
          retryAfterSeconds: 60,
          detail: "private upstream body synthetic-access",
        },
        502,
      ),
    );
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/entities", isString)).rejects.toMatchObject({
      status: 502,
      code: "XERO_RATE_LIMITED",
      upstreamStatus: 429,
      retryAfterSeconds: 60,
    });
    await expect(api.json("/api/v1/entities", isString)).rejects.not.toThrow(
      /private upstream|synthetic-access/,
    );
  });

  it("FR-UC15-25 never turns an error response into a file Blob", async () => {
    const response = json(
      { code: "XERO_ORGANISATION_ACCESS_DENIED", detail: "private error" },
      502,
    );
    const blob = vi.spyOn(response, "blob");
    const api = new ApiClient("", auth(), vi.fn<typeof fetch>().mockResolvedValue(response));
    await expect(api.download("/api/v1/entities/attachment/file")).rejects.toBeInstanceOf(ApiError);
    expect(blob).not.toHaveBeenCalled();
  });

  it("FR-UC15-30 rejects malformed success data and HTML masquerading as a download", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json({ wrong: "shape" }))
      .mockResolvedValueOnce(
        new Response("private html", { headers: { "Content-Type": "text/html" } }),
      );
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/me", isString)).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
    await expect(api.download("/api/v1/entities/attachment/file")).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });

  it("FR-UC15-18 distinguishes dashboard permission denial from provider access refusal", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json({}, 403))
      .mockResolvedValueOnce(
        json({ code: "XERO_ORGANISATION_ACCESS_DENIED", upstreamStatus: 403 }, 502),
      );
    const api = new ApiClient("", auth(), fetcher);
    await expect(api.json("/api/v1/me", isString)).rejects.toMatchObject({
      status: 403,
      code: "ACCESS_DENIED",
    });
    await expect(api.json("/api/v1/entities", isString)).rejects.toMatchObject({
      status: 502,
      code: "XERO_ORGANISATION_ACCESS_DENIED",
    });
  });

  it("FR-UC15-30 cancels before forwarding a token if context changes while Auth is pending", async () => {
    let finish: (value: string) => void = () => {};
    const service = auth();
    vi.mocked(service.getAccessToken).mockImplementation(
      () =>
        new Promise<string>((resolve) => {
          finish = resolve;
        }),
    );
    const fetcher = vi.fn<typeof fetch>();
    const api = new ApiClient("", service, fetcher);
    const controller = new AbortController();
    const attempt = api.json("/api/v1/me", isString, controller.signal);
    const rejected = expect(attempt).rejects.toMatchObject({ name: "AbortError" });
    controller.abort();
    finish("stale-synthetic-access");
    await rejected;
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("FR-UC15-18 missing sessions stop before any API call", async () => {
    const service = auth();
    vi.mocked(service.getAccessToken).mockResolvedValue(null);
    const fetcher = vi.fn<typeof fetch>();
    await expect(
      new ApiClient("", service, fetcher).json("/api/v1/me", isString),
    ).rejects.toMatchObject({ status: 401, code: "SESSION_ENDED" });
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("FR-UC15-01 accepts only 204 No Content for commands without a response body", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
    const api = new ApiClient("https://api.example.invalid", auth(), fetcher);
    await expect(
      api.noContent("/api/v1/entities/x/archive", undefined, { method: "POST" }),
    ).resolves.toBeUndefined();
    expect(fetcher.mock.calls[0]?.[1]?.method).toBe("POST");
  });
  it("FR-UC15-01 rejects a successful command response that carries a body", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(json({ unexpected: true }, 200));
    const api = new ApiClient("https://api.example.invalid", auth(), fetcher);
    await expect(
      api.noContent("/api/v1/entities/x/archive", undefined, { method: "POST" }),
    ).rejects.toMatchObject({ status: 502, code: "INVALID_RESPONSE" });
  });
});
