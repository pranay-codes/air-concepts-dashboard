import { createClient } from "@supabase/supabase-js";

import type { AppEnv } from "../env";
import { AuthError, type AuthService } from "./authService";

/** Uses only the public URL and publishable key. The provider's error text is not passed on. */
export function createSupabaseAuth(env: AppEnv): AuthService {
  const client = createClient(env.supabaseUrl, env.supabasePublishableKey);

  return {
    async getAccessToken() {
      const { data } = await client.auth.getSession();
      return data.session?.access_token ?? null;
    },
    async signIn(email, password) {
      const { error } = await client.auth.signInWithPassword({ email, password });
      if (error) {
        throw new AuthError("Sign-in failed. Check your email and password.");
      }
    },
    async signOut() {
      await client.auth.signOut();
    },
  };
}
