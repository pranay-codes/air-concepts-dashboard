import { useEffect, useRef, useState } from "react";

import { ProtectedWorkspace } from "./workspace/ProtectedWorkspace";
import { AppFrame } from "./ui/AppFrame";
import { ErrorBoundary } from "./ui/ErrorBoundary";
import { SignIn } from "./auth/SignIn";
import type { AuthService } from "./auth/authService";

interface AppProps {
  auth?: AuthService;
  apiBaseUrl?: string;
}

export function App({ auth, apiBaseUrl = "" }: AppProps) {
  return auth ? (
    <ErrorBoundary key={apiBaseUrl}>
      <Session auth={auth} apiBaseUrl={apiBaseUrl} />
    </ErrorBoundary>
  ) : (
    <AppFrame />
  );
}

/** undefined while the stored session is being read, null when signed out. */
function Session({ auth, apiBaseUrl }: { auth: AuthService; apiBaseUrl: string }) {
  const [session, setSession] = useState<{
    source: AuthService;
    token: string | null | undefined;
    epoch: number;
    error: "read" | "signout" | null;
    signingOut: boolean;
  }>({ source: auth, token: undefined, epoch: 0, error: null, signingOut: false });
  const [readAttempt, setReadAttempt] = useState(0);
  const locked = useRef(false);
  const source = useRef(auth);
  const lifetime = useRef(0);

  useEffect(() => {
    let active = true;
    let eventSeen = false;
    lifetime.current += 1;
    if (source.current !== auth) {
      source.current = auth;
      locked.current = false;
    }
    const release = auth.onSessionChange((token) => {
      if (!active || locked.current) return;
      eventSeen = true;
      setSession((previous) =>
        previous.source === auth && previous.token === token && previous.error === null
          ? previous
          : {
              source: auth,
              token,
              epoch: previous.epoch + 1,
              error: null,
              signingOut: false,
            },
      );
    });
    Promise.resolve()
      .then(() => auth.getAccessToken())
      .then((token) => {
        if (active && !eventSeen && !locked.current) {
          setSession((previous) => ({
            source: auth,
            token,
            epoch: previous.epoch + 1,
            error: null,
            signingOut: false,
          }));
        }
      })
      .catch(() => {
        if (active && !eventSeen && !locked.current) {
          setSession((previous) => ({
            source: auth,
            token: undefined,
            epoch: previous.epoch + 1,
            error: "read",
            signingOut: false,
          }));
        }
      });
    return () => {
      active = false;
      lifetime.current += 1;
      release();
    };
  }, [auth, readAttempt]);

  function readSession() {
    locked.current = false;
    setSession((previous) => ({
      source: auth,
      token: undefined,
      epoch: previous.epoch + 1,
      error: null,
      signingOut: false,
    }));
    setReadAttempt((value) => value + 1);
  }

  async function signOut() {
    const started = lifetime.current;
    locked.current = true;
    setSession((previous) => ({
      source: auth,
      token: null,
      epoch: previous.epoch + 1,
      error: null,
      signingOut: true,
    }));
    try {
      await auth.signOut();
      if (source.current === auth && lifetime.current === started) {
        setSession((previous) => ({ ...previous, token: null, signingOut: false }));
      }
    } catch {
      if (source.current === auth && lifetime.current === started) {
        setSession((previous) => ({
          ...previous,
          token: null,
          signingOut: false,
          error: "signout",
        }));
      }
    }
  }

  if (session.source !== auth || session.signingOut) {
    return (
      <AppFrame>
        <p role="status">
          {session.signingOut && session.source === auth ? "Signing out…" : "Reading your session…"}
        </p>
      </AppFrame>
    );
  }
  if (session.error === "read") {
    return (
      <AppFrame>
        <p role="alert">Could not read your session. Try again.</p>
        <button type="button" onClick={readSession}>
          Try session again
        </button>
      </AppFrame>
    );
  }
  if (session.token === undefined)
    return (
      <AppFrame>
        <p role="status">Reading your session…</p>
      </AppFrame>
    );
  if (session.token === null) {
    return (
      <AppFrame>
        {session.error === "signout" ? (
          <div>
            <p role="alert">Sign-out could not be confirmed. Protected content has been cleared.</p>
            <button type="button" onClick={() => void signOut()}>
              Try sign-out again
            </button>
          </div>
        ) : null}
        <SignIn auth={auth} onSignedIn={readSession} />
      </AppFrame>
    );
  }
  return (
    <ProtectedWorkspace
      key={`${apiBaseUrl}:${session.epoch}`}
      apiBaseUrl={apiBaseUrl}
      auth={auth}
      onSignOut={() => void signOut()}
      onAccessDenied={readSession}
    />
  );
}
