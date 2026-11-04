import { type FormEvent, useState } from "react";

import { AuthError, type AuthService } from "./authService";

interface SignInProps {
  auth: AuthService;
  onSignedIn: () => void;
}

export function SignIn({ auth, onSignedIn }: SignInProps) {
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      await auth.signIn(String(form.get("email")), String(form.get("password")));
      onSignedIn();
    } catch (failure) {
      setError(failure instanceof AuthError ? failure.message : "Sign-in failed. Try again.");
    } finally {
      setPending(false);
    }
  }

  return (
    <form aria-label="Sign in" onSubmit={handleSubmit}>
      <label>
        Email
        <input name="email" type="email" autoComplete="username" />
      </label>
      <label>
        Password
        <input name="password" type="password" autoComplete="current-password" />
      </label>
      <button type="submit" disabled={pending}>
        Sign in
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}
