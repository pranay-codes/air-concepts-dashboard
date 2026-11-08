import { createClient } from "@supabase/supabase-js";

import type { AppEnv } from "../env";
import { AuthError, type AuthService } from "./authService";

/** Uses only the public URL and publishable key. The provider's error text is not passed on. */
export function createSupabaseAuth(env: AppEnv): AuthService {
  const client = createClient(env.supabaseUrl, env.supabasePublishableKey, {
    auth: { detectSessionInUrl: false },
  });

  async function operation(
    request: () => PromiseLike<{ error: { status?: number; code?: string } | null }>,
    message: string,
  ) {
    try {
      const { error } = await request();
      if (error?.status === 429)
        throw new AuthError("Too many attempts. Wait a minute before trying again.", 60);
      if (error?.code === "otp_expired")
        throw new AuthError("This code has expired. Request a new code and try again.");
      if (error) throw new AuthError(message);
    } catch (error) {
      throw error instanceof AuthError ? error : new AuthError(message);
    }
  }

  function emailAddress(email: string) {
    const value = email.trim().toLowerCase();
    if (value.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))
      throw new AuthError("Enter your registered email address.");
    return value;
  }

  return {
    async getAccessToken() {
      try {
        const { data, error } = await client.auth.getSession();
        if (error) throw new AuthError("Could not read your session. Sign in again.");
        return data.session?.access_token ?? null;
      } catch {
        throw new AuthError("Could not read your session. Sign in again.");
      }
    },
    async requestCode(email) {
      const address = emailAddress(email);
      await operation(
        () => client.auth.signInWithOtp({ email: address, options: { shouldCreateUser: false } }),
        "Could not send a code. Check your registered email and try again.",
      );
    },
    async verifyCode(email, code) {
      const address = emailAddress(email);
      if (!/^\d{6}$/.test(code)) throw new AuthError("Enter the six-digit code from your email.");
      await operation(
        () => client.auth.verifyOtp({ email: address, token: code, type: "email" }),
        "The code could not be verified. Check the six digits or request a new code.",
      );
    },
    async signOut() {
      await operation(
        () => client.auth.signOut({ scope: "local" }),
        "Sign-out could not be confirmed. Try again.",
      );
    },
    onSessionChange(listener) {
      const { data } = client.auth.onAuthStateChange((_event, session) =>
        listener(session?.access_token ?? null),
      );
      return () => data.subscription.unsubscribe();
    },
  };
}
