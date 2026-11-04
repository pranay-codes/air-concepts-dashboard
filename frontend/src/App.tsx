import { useEffect, useState } from "react";

import { MePanel } from "./MePanel";
import { SignIn } from "./auth/SignIn";
import type { AuthService } from "./auth/authService";

interface AppProps {
  auth?: AuthService;
  apiBaseUrl?: string;
}

export function App({ auth, apiBaseUrl = "" }: AppProps) {
  return (
    <main>
      <h1>Air Concepts Financial Admin Dashboard</h1>
      <section aria-label="Dashboard content">
        {auth ? <Session auth={auth} apiBaseUrl={apiBaseUrl} /> : null}
      </section>
    </main>
  );
}

/** undefined while the stored session is being read, null when signed out. */
function Session({ auth, apiBaseUrl }: { auth: AuthService; apiBaseUrl: string }) {
  const [token, setToken] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    let active = true;
    auth.getAccessToken().then((result) => {
      if (active) setToken(result);
    });
    return () => {
      active = false;
    };
  }, [auth]);

  if (token === undefined) {
    return null;
  }
  if (token === null) {
    return <SignIn auth={auth} onSignedIn={() => auth.getAccessToken().then(setToken)} />;
  }
  return (
    <MePanel
      apiBaseUrl={apiBaseUrl}
      token={token}
      onSignOut={() => auth.signOut().then(() => setToken(null))}
    />
  );
}
