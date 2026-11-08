import { createClient } from "@supabase/supabase-js";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { AppEnv } from "../env";
import { createSupabaseAuth } from "./supabaseAuth";
import { AuthError } from "./authService";

const client = {
  auth: {
    signInWithPassword: vi.fn(),
    signInWithOtp: vi.fn(),
    verifyOtp: vi.fn(),
    onAuthStateChange: vi.fn(),
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
      { auth: { detectSessionInUrl: false } },
    );
  });

  it("FR-UC15-17 requests email codes for registered users only", async () => {
    client.auth.signInWithOtp.mockResolvedValue({ data: {}, error: null });
    await createSupabaseAuth(env).requestCode(" tester@example.invalid ");
    expect(client.auth.signInWithOtp).toHaveBeenCalledWith({
      email: "tester@example.invalid",
      options: { shouldCreateUser: false },
    });
    expect(client.auth.signInWithPassword).not.toHaveBeenCalled();
  });

  it("FR-UC15-17 hides raw provider details when code requests fail", async () => {
    client.auth.signInWithOtp.mockResolvedValue({
      data: {},
      error: { message: "Invalid login credentials" },
    });

    const attempt = createSupabaseAuth(env).requestCode("tester@example.invalid");

    await expect(attempt).rejects.toBeInstanceOf(AuthError);
    await expect(attempt).rejects.toThrow(
      "Could not send a code. Check your registered email and try again.",
    );
    await expect(attempt).rejects.not.toThrow(/Invalid login credentials/);
  });

  it("FR-UC15-17 verifies six digits with email OTP and rejects invalid codes locally", async () => {
    client.auth.verifyOtp.mockResolvedValue({ data: {}, error: null });
    const auth = createSupabaseAuth(env);
    await auth.verifyCode("tester@example.invalid", "123456");
    expect(client.auth.verifyOtp).toHaveBeenCalledWith({
      email: "tester@example.invalid",
      token: "123456",
      type: "email",
    });
    await expect(auth.verifyCode("tester@example.invalid", "123")).rejects.toBeInstanceOf(
      AuthError,
    );
    expect(client.auth.verifyOtp).toHaveBeenCalledTimes(1);
  });

  it("FR-UC15-17 reports expired codes and rate limits safely", async () => {
    client.auth.verifyOtp.mockResolvedValue({
      data: {},
      error: { code: "otp_expired", message: "private provider detail" },
    });
    await expect(
      createSupabaseAuth(env).verifyCode("tester@example.invalid", "123456"),
    ).rejects.toThrow(/expired/);
    client.auth.signInWithOtp.mockResolvedValue({
      data: {},
      error: { status: 429, message: "private provider detail" },
    });
    await expect(
      createSupabaseAuth(env).requestCode("tester@example.invalid"),
    ).rejects.toMatchObject({ retryAfterSeconds: 60 });
  });

  it("FR-UC15-18 subscribes to refreshed and cleared sessions and releases the subscription", () => {
    const unsubscribe = vi.fn();
    client.auth.onAuthStateChange.mockReturnValue({ data: { subscription: { unsubscribe } } });
    const listener = vi.fn();
    const release = createSupabaseAuth(env).onSessionChange(listener);
    const callback = client.auth.onAuthStateChange.mock.calls[0]?.[0];
    callback("TOKEN_REFRESHED", { access_token: "refreshed-synthetic-token" });
    callback("SIGNED_OUT", null);
    expect(listener).toHaveBeenNthCalledWith(1, "refreshed-synthetic-token");
    expect(listener).toHaveBeenNthCalledWith(2, null);
    release();
    expect(unsubscribe).toHaveBeenCalledOnce();
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
