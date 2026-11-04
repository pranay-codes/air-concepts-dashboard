/** Sign-in failure whose message is safe to show to the user. */
export class AuthError extends Error {}

/** The browser's view of Supabase Auth. Tokens stay inside this module and the Supabase client. */
export interface AuthService {
  getAccessToken(): Promise<string | null>;
  signIn(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
}
