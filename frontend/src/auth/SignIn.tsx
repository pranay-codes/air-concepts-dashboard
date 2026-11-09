import { type FormEvent, useEffect, useRef, useState } from "react";

import { AuthError, type AuthService } from "./authService";

interface SignInProps {
  auth: AuthService;
  onSignedIn: () => void;
}

export function SignIn({ auth, onSignedIn }: SignInProps) {
  const [email, setEmail] = useState("");
  const [requestedEmail, setRequestedEmail] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [invalid, setInvalid] = useState<"email" | "code" | null>(null);
  const [pending, setPending] = useState(false);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [expiresAt, setExpiresAt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const busy = useRef(false);
  const mounted = useRef(false);
  const emailInput = useRef<HTMLInputElement>(null);
  const codeInput = useRef<HTMLInputElement>(null);
  const remaining = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));
  const expired = expiresAt > 0 && now >= expiresAt;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    if (cooldownUntil === 0 && expiresAt === 0) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [cooldownUntil, expiresAt]);
  useEffect(() => {
    if (requestedEmail) codeInput.current?.focus();
    else emailInput.current?.focus();
  }, [requestedEmail]);

  function failure(error: unknown, fallback: string) {
    setError(error instanceof AuthError ? error.message : fallback);
    if (error instanceof AuthError && error.retryAfterSeconds) {
      setCooldownUntil(Date.now() + error.retryAfterSeconds * 1000);
      setNow(Date.now());
    }
  }

  async function sendCode() {
    if (busy.current || Date.now() < cooldownUntil) return;
    const address = (requestedEmail ?? email).trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) || address.length > 254) {
      setInvalid("email");
      setError("Enter your registered email address.");
      emailInput.current?.focus();
      return;
    }
    busy.current = true;
    setPending(true);
    setError(null);
    setInvalid(null);
    try {
      await auth.requestCode(address);
      if (!mounted.current) return;
      setRequestedEmail(address);
      setCode("");
      setNow(Date.now());
      setCooldownUntil(Date.now() + 60000);
      setExpiresAt(Date.now() + 600000);
      codeInput.current?.focus();
    } catch (error) {
      if (mounted.current) failure(error, "Could not send a code. Try again.");
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current || !requestedEmail || expired) return;
    if (!/^\d{6}$/.test(code)) {
      setInvalid("code");
      setError("Enter the six-digit code from your email.");
      codeInput.current?.focus();
      return;
    }
    busy.current = true;
    setPending(true);
    setError(null);
    setInvalid(null);
    try {
      await auth.verifyCode(requestedEmail, code);
      if (mounted.current) onSignedIn();
    } catch (error) {
      if (mounted.current) {
        failure(error, "Could not verify the code. Try again.");
        codeInput.current?.focus();
      }
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }

  return (
    <section className="auth-card card" aria-labelledby="sign-in-title">
      <h2 id="sign-in-title">{requestedEmail ? "Enter your code" : "Sign in"}</h2>
      {requestedEmail ? (
        <>
          <p role="status">
            A code was requested for <strong>{requestedEmail}</strong>. Check your inbox and spam
            folder.
          </p>
          <form aria-label="Verify code" noValidate onSubmit={verify}>
            <label htmlFor="sign-in-code">Six-digit code</label>
            <input
              id="sign-in-code"
              ref={codeInput}
              name="code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9]{6}"
              maxLength={6}
              required
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\s/g, ""))}
              onPaste={(event) => {
                const pasted = event.clipboardData.getData("text").replace(/\s/g, "");
                if (/^\d{6}$/.test(pasted)) {
                  event.preventDefault();
                  setCode(pasted);
                }
              }}
              aria-invalid={invalid === "code"}
              aria-describedby={error ? "code-help sign-in-error" : "code-help"}
              disabled={pending}
            />
            <p id="code-help" className="supporting">
              This code is single use and expires in 10 minutes. You can paste it here.
            </p>
            {expired ? (
              <p role="status" className="notice warning">
                This code has expired. Request a new code.
              </p>
            ) : null}
            <button className="primary" type="submit" disabled={pending || expired}>
              {pending ? "Checking…" : "Verify and sign in"}
            </button>
          </form>
          <div className="action-row">
            <button
              type="button"
              disabled={pending || remaining > 0}
              onClick={() => void sendCode()}
            >
              Resend code{remaining > 0 ? ` (${remaining}s)` : ""}
            </button>
            <button
              type="button"
              disabled={pending}
              onClick={() => {
                setRequestedEmail(null);
                setCode("");
                setError(null);
                setInvalid(null);
                setCooldownUntil(0);
                setExpiresAt(0);
              }}
            >
              Use a different email
            </button>
          </div>
        </>
      ) : (
        <form
          aria-label="Sign in"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void sendCode();
          }}
        >
          <p>
            Use the email registered by your Director. We’ll email you a single-use sign-in code.
          </p>
          <label htmlFor="sign-in-email">Email</label>
          <input
            id="sign-in-email"
            ref={emailInput}
            name="email"
            type="email"
            autoComplete="email"
            required
            maxLength={254}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={pending}
            aria-invalid={invalid === "email"}
            aria-describedby={error ? "sign-in-error" : undefined}
          />
          <button className="primary" type="submit" disabled={pending || remaining > 0}>
            {pending ? "Sending code…" : "Send code"}
          </button>
          {remaining > 0 ? (
            <p role="status">Wait {remaining} seconds before requesting another code.</p>
          ) : null}
        </form>
      )}
      {error ? (
        <p id="sign-in-error" role="alert" className="notice error">
          {error}
        </p>
      ) : null}
    </section>
  );
}
