import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { App } from "./App";
import type { AuthService } from "./auth/authService";

function fakeAuth(token: string | null): AuthService {
  return {
    getAccessToken: vi.fn().mockResolvedValue(token),
    signIn: vi.fn().mockResolvedValue(undefined),
    signOut: vi.fn().mockResolvedValue(undefined),
  };
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("App", () => {
  it("shows the dashboard heading and the placeholder layout region", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Air Concepts Financial Admin Dashboard" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Dashboard content" })).toBeInTheDocument();
  });

  it("shows the sign-in form when there is no session", async () => {
    render(<App auth={fakeAuth(null)} apiBaseUrl="http://api.test" />);

    expect(await screen.findByRole("form", { name: "Sign in" })).toBeInTheDocument();
  });

  it("loads the signed-in user from the API using the session token", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ subject: "user-123", role: "FINANCIAL_ADMIN" }, 200));

    render(<App auth={fakeAuth("token-abc")} apiBaseUrl="http://api.test" />);

    expect(await screen.findByText("Signed in as user-123")).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledWith("http://api.test/api/v1/me", {
      headers: { Authorization: "Bearer token-abc" },
    });
    expect(screen.queryByRole("form", { name: "Sign in" })).not.toBeInTheDocument();
  });

  it("shows an error instead of user details when the API rejects the token", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 401 }));

    render(<App auth={fakeAuth("expired-token")} apiBaseUrl="http://api.test" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load your profile.");
  });
});
