import { createClient } from "@supabase/supabase-js";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { AppEnv } from "../env";
import { createSupabaseAuth } from "./supabaseAuth";
import { AuthError } from "./authService";

const client = {
  auth: {
    signInWithPassword: vi.fn(),
    signOut: vi.fn(),
    getSession: vi.fn(),
  },
};

vi.mock("@supabase/supabase-js", () => ({
  createClient: vi.fn(() => client),
}));

const env: AppEnv = {
  apiBaseUrl: "http://localhost:8080",
  supabaseUrl: "https://placeholder.supabase.co",
  supabasePublishableKey: "placeholder-publishable-key",
};

describe("createSupabaseAuth", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates the client from the public URL and publishable key only", () => {
    createSupabaseAuth(env);

    expect(createClient).toHaveBeenCalledWith(
      "https://placeholder.supabase.co",
      "placeholder-publishable-key",
    );
  });

  it("signs in with the entered email and password", async () => {
    client.auth.signInWithPassword.mockResolvedValue({ data: {}, error: null });

    await createSupabaseAuth(env).signIn("tester@example.invalid", "placeholder-password");

    expect(client.auth.signInWithPassword).toHaveBeenCalledWith({
      email: "tester@example.invalid",
      password: "placeholder-password",
    });
  });

  it("reports a failed sign-in with a generic message that hides the provider error", async () => {
    client.auth.signInWithPassword.mockResolvedValue({
      data: {},
      error: { message: "Invalid login credentials" },
    });

    const attempt = createSupabaseAuth(env).signIn("tester@example.invalid", "wrong");

    await expect(attempt).rejects.toBeInstanceOf(AuthError);
    await expect(attempt).rejects.toThrow("Sign-in failed. Check your email and password.");
    await expect(attempt).rejects.not.toThrow(/Invalid login credentials/);
  });

  it("returns the access token of the current session", async () => {
    client.auth.getSession.mockResolvedValue({
      data: { session: { access_token: "token-abc" } },
      error: null,
    });

    await expect(createSupabaseAuth(env).getAccessToken()).resolves.toBe("token-abc");
  });

  it("returns null when there is no session", async () => {
    client.auth.getSession.mockResolvedValue({ data: { session: null }, error: null });

    await expect(createSupabaseAuth(env).getAccessToken()).resolves.toBeNull();
  });

  it("signs out through the client", async () => {
    client.auth.signOut.mockResolvedValue({ error: null });

    await createSupabaseAuth(env).signOut();

    expect(client.auth.signOut).toHaveBeenCalledTimes(1);
  });
});
