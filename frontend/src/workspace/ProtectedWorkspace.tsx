import { useCallback, useMemo } from "react";
import type { AuthService } from "../auth/authService";
import { ApiClient } from "../api/client";
import { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { AppFrame } from "../ui/AppFrame";
import { Failure, Loading } from "../ui/Feedback";
import { Workspace } from "./Workspace";

export function ProtectedWorkspace({
  auth,
  apiBaseUrl,
  onSignOut,
  onAccessDenied,
}: {
  auth: AuthService;
  apiBaseUrl: string;
  onSignOut(): void;
  onAccessDenied(): void;
}) {
  const api = useMemo(() => new DashboardApi(new ApiClient(apiBaseUrl, auth)), [apiBaseUrl, auth]);
  const load = useCallback(
    async (signal: AbortSignal) => {
      const [profile, entities] = await Promise.all([api.me(signal), api.entities(signal)]);
      return { profile, entities };
    },
    [api],
  );
  const resource = useResource("authorised-workspace", load);
  if (resource.status === "loading")
    return (
      <AppFrame>
        <Loading label="Checking your access and entities…" />
      </AppFrame>
    );
  if (resource.status === "error")
    return (
      <AppFrame>
        <Failure error={resource.error} onRetry={resource.refresh} label="Check access again" />
        <button type="button" onClick={onSignOut}>
          Sign out
        </button>
      </AppFrame>
    );
  if (!resource.data) return null;
  return (
    <Workspace
      api={api}
      profile={resource.data.profile}
      entities={resource.data.entities}
      onSignOut={onSignOut}
      onAccessDenied={onAccessDenied}
      onEntitiesChanged={resource.refresh}
    />
  );
}
