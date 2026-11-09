import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { App } from "./App";
import type { AuthService } from "./auth/authService";
import { entities, invoice, profile } from "./test/fixtures";

function fakeAuth(token: string | null): AuthService {
  return {
    getAccessToken: vi.fn().mockResolvedValue(token),
    requestCode: vi.fn().mockResolvedValue(undefined),
    verifyCode: vi.fn().mockResolvedValue(undefined),
    signOut: vi.fn().mockResolvedValue(undefined),
    onSessionChange: vi.fn(() => () => {}),
  };
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
function backend(
  readProfile: (token: string | null) => Response = () => jsonResponse(profile, 200),
) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
    const url = new URL(String(input), "https://api.test");
    if (url.pathname.endsWith("/me"))
      return readProfile(new Headers(init?.headers).get("Authorization"));
    if (url.pathname.endsWith("/entities")) return jsonResponse(entities, 200);
    if (url.pathname.endsWith("/xero"))
      return jsonResponse(
        {
          entityId: url.pathname.split("/").at(-2),
          status: "CONNECTED",
          organisationName: "Synthetic Xero organisation",
        },
        200,
      );
    if (url.pathname.endsWith("/staff"))
      return jsonResponse([{ id: profile.subject, displayName: profile.displayName }], 200);
    if (url.pathname.endsWith("/invoices"))
      return jsonResponse(
        { items: [invoice()], page: 1, pageSize: 25, pageCount: 1, itemCount: 1 },
        200,
      );
    return jsonResponse({}, 404);
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("App", () => {
  it("FR-UC15-16 preserves Settings edits when Auth re-emits the unchanged session", async () => {
    let changed: (token: string | null) => void = () => {};
    const auth: AuthService = {
      ...fakeAuth("synthetic-token"),
      onSessionChange: (listener) => {
        changed = listener;
        return () => {};
      },
    };
    backend();
    render(<App auth={auth} />);
    fireEvent.click(await screen.findByRole("button", { name: "Settings" }));
    const draft = await screen.findByLabelText("New entity name");
    fireEvent.change(draft, { target: { value: "Unsaved entity" } });
    act(() => changed("synthetic-token"));
    expect(screen.getByLabelText("New entity name")).toHaveValue("Unsaved entity");
  });
  it("shows the dashboard heading and the placeholder layout region", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Air Concepts Financial Admin Dashboard" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Dashboard content" })).toBeInTheDocument();
  });

  it("shows the sign-in form when there is no session", async () => {
    render(<App auth={fakeAuth(null)} apiBaseUrl="https://api.test" />);

    expect(await screen.findByRole("form", { name: "Sign in" })).toBeInTheDocument();
  });

  it("loads the signed-in user from the API using the session token", async () => {
    const fetchSpy = backend(() => jsonResponse({ ...profile, role: "FINANCIAL_ADMIN" }, 200));

    render(<App auth={fakeAuth("token-abc")} apiBaseUrl="https://api.test" />);

    expect(await screen.findByText(profile.displayName)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { level: 1, name: "Invoices" })).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledWith(
      "https://api.test/api/v1/me",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer token-abc" }),
        cache: "no-store",
        credentials: "omit",
      }),
    );
    expect(screen.queryByRole("form", { name: "Sign in" })).not.toBeInTheDocument();
  });

  it("shows an error instead of user details when the API rejects the token", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 401 }));

    render(<App auth={fakeAuth("expired-token")} apiBaseUrl="https://api.test" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Your session has ended.");
  });

  it("FR-UC15-18 does not restore an old session after a newer sign-out event", async () => {
    let finish: (token: string | null) => void = () => {};
    let changed: (token: string | null) => void = () => {};
    const release = vi.fn();
    const auth: AuthService = {
      ...fakeAuth(null),
      getAccessToken: vi.fn(
        () =>
          new Promise<string | null>((resolve) => {
            finish = resolve;
          }),
      ),
      onSessionChange: (listener) => {
        changed = listener;
        return release;
      },
    };
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const view = render(<App auth={auth} />);
    act(() => changed(null));
    expect(await screen.findByRole("form", { name: "Sign in" })).toBeInTheDocument();
    await act(async () => finish("stale-synthetic-token"));
    expect(screen.getByRole("form", { name: "Sign in" })).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
    view.unmount();
    expect(release).toHaveBeenCalledOnce();
  });

  it("FR-UC15-18 responds to refreshed tokens and clears earlier profile data", async () => {
    let changed: (token: string | null) => void = () => {};
    const auth: AuthService = {
      ...fakeAuth("old-synthetic-token"),
      onSessionChange: (listener) => {
        changed = listener;
        return () => {};
      },
    };
    const fetchSpy = backend((token) =>
      token === "Bearer old-synthetic-token"
        ? jsonResponse({ ...profile, displayName: "Old user", role: "STANDARD_USER" }, 200)
        : new Response(null, { status: 401 }),
    );
    render(<App auth={auth} />);
    await screen.findByText("Old user");
    vi.mocked(auth.getAccessToken).mockResolvedValue("new-synthetic-token");
    act(() => changed("new-synthetic-token"));
    expect(screen.queryByText("Old user")).not.toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("Your session has ended.");
    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/me",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer new-synthetic-token" }),
      }),
    );
  });

  it("FR-UC15-18 offers safe session-read recovery", async () => {
    const auth: AuthService = {
      ...fakeAuth(null),
      getAccessToken: vi
        .fn()
        .mockRejectedValueOnce(new Error("private SDK error"))
        .mockResolvedValueOnce(null),
    };
    render(<App auth={auth} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not read your session.");
    expect(screen.queryByText(/private SDK error/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try session again" }));
    expect(await screen.findByRole("form", { name: "Sign in" })).toBeInTheDocument();
  });

  it("FR-UC15-18 clears protected content during sign-out and ignores late refresh events", async () => {
    let finish = () => {};
    let changed: (token: string | null) => void = () => {};
    const auth: AuthService = {
      ...fakeAuth("synthetic-token"),
      signOut: vi.fn(
        () =>
          new Promise<void>((resolve) => {
            finish = resolve;
          }),
      ),
      onSessionChange: (listener) => {
        changed = listener;
        return () => {};
      },
    };
    backend(() =>
      jsonResponse({ ...profile, displayName: "Signed-in user", role: "STANDARD_USER" }, 200),
    );
    render(<App auth={auth} />);
    await screen.findByText("Signed-in user");
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
    act(() => changed("late-synthetic-token"));
    expect(screen.queryByText("Signed-in user")).not.toBeInTheDocument();
    await act(async () => finish());
    expect(await screen.findByRole("form", { name: "Sign in" })).toBeInTheDocument();
  });
});
