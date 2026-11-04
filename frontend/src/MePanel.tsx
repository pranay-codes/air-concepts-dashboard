import { useEffect, useState } from "react";

import { fetchMe, type Me } from "./api/me";

interface MePanelProps {
  apiBaseUrl: string;
  token: string;
  onSignOut: () => void;
}

export function MePanel({ apiBaseUrl, token, onSignOut }: MePanelProps) {
  const [me, setMe] = useState<Me | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    fetchMe(apiBaseUrl, token)
      .then((result) => {
        if (active) setMe(result);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, [apiBaseUrl, token]);

  if (failed) {
    return <p role="alert">Could not load your profile.</p>;
  }
  if (!me) {
    return null;
  }
  return (
    <div>
      <p>Signed in as {me.subject}</p>
      {me.role ? <p>Role: {me.role}</p> : null}
      <button type="button" onClick={onSignOut}>
        Sign out
      </button>
    </div>
  );
}
