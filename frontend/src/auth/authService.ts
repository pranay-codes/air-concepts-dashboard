/** Sign-in failure whose message is safe to show to the user. */
export class AuthError extends Error {
  constructor(
    message: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

/** Browser Auth boundary. Supabase session tokens authorise API requests; Xero tokens never enter the browser. */
export interface AuthService {
  getAccessToken(): Promise<string | null>;
  requestCode(email: string): Promise<void>;
  verifyCode(email: string, code: string): Promise<void>;
  signOut(): Promise<void>;
  onSessionChange(listener: (token: string | null) => void): () => void;
}
