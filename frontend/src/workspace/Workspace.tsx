import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Invoices } from "../invoices/Invoices";
import { Failure, Loading, Notice, roleNames } from "../ui/Feedback";
import { formatFetchTime } from "../ui/format";
import { EntitySelector } from "./EntitySelector";

const Settings = lazy(() => import("../settings/Settings"));
const Forecast = lazy(() => import("../forecast/Forecast"));
const JobsList = lazy(() =>
  import("../workitems/JobsList").then((module) => ({ default: module.JobsList })),
);
const DirectorOverview = lazy(() =>
  import("../workitems/DirectorOverview").then((module) => ({
    default: module.DirectorOverview,
  })),
);
const MyWork = lazy(() =>
  import("../workitems/MyWork").then((module) => ({ default: module.MyWork })),
);
type Route = "overview" | "mywork" | "invoices" | "jobs" | "forecast" | "settings";
interface Props {
  api: DashboardApi;
  profile: Profile;
  entities: Entity[];
  onSignOut(): void;
  onAccessDenied(): void;
  onEntitiesChanged(): void;
}
const preference = (subject: string) => `uc5.entity.v1:${subject}`;
function remembered(profile: Profile, entities: Entity[]): string | null {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(preference(profile.subject)) ?? "null");
    if (
      stored &&
      typeof stored === "object" &&
      "entityId" in stored &&
      typeof stored.entityId === "string" &&
      entities.some((entity) => entity.id === stored.entityId)
    )
      return stored.entityId;
  } catch {
    /* A preference is optional; never relax authorised selection. */
  }
  return entities[0]?.id ?? null;
}
export function Workspace({
  api,
  profile,
  entities,
  onSignOut,
  onAccessDenied,
  onEntitiesChanged,
}: Props) {
  const [choice, setChoice] = useState(() => ({
    subject: profile.subject,
    id: remembered(profile, entities),
    generation: 0,
  }));
  if (choice.subject !== profile.subject)
    setChoice({
      subject: profile.subject,
      id: remembered(profile, entities),
      generation: choice.generation + 1,
    });
  const [route, setRoute] = useState<Route>(() =>
    profile.role === "DIRECTOR" && new URLSearchParams(window.location.search).has("xeroAttempt")
      ? "settings"
      : "mywork",
  );
  const [collapsed, setCollapsed] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [accessError, setAccessError] = useState<unknown>(null);
  const aggregate =
    choice.id === "__all__" &&
    (route === "mywork" ||
      (route === "forecast" && profile.role !== "STANDARD_USER") ||
      (route === "overview" && profile.role === "DIRECTOR"));
  const selected = aggregate
    ? undefined
    : (entities.find((entity) => entity.id === choice.id) ?? entities[0]);
  const entityId = selected?.id;
  const loadConnection = useCallback(
    async (signal: AbortSignal) => {
      if (!entityId) throw new ApiError(400, "INVALID_REQUEST");
      try {
        return await api.connection(entityId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entityId, onAccessDenied],
  );
  const connection = useResource(
    `${profile.subject}:${entityId ?? "none"}`,
    loadConnection,
    Boolean(entityId),
  );
  useEffect(() => {
    let current: AbortController | undefined;
    const check = () => {
      current?.abort();
      const controller = new AbortController();
      current = controller;
      api
        .me(controller.signal)
        .then((fresh) => {
          if (controller.signal.aborted) return;
          setAccessError(null);
          if (fresh.subject !== profile.subject || fresh.role !== profile.role) onAccessDenied();
        })
        .catch((error) => {
          if (controller.signal.aborted) return;
          if (isAccessFailure(error)) onAccessDenied();
          else setAccessError(error);
        });
    };
    window.addEventListener("focus", check);
    return () => {
      window.removeEventListener("focus", check);
      current?.abort();
    };
  }, [api, profile.subject, profile.role, onAccessDenied]);
  function navigate(next: Route) {
    if (dirty && !window.confirm("Discard unsaved changes before leaving this page?")) return;
    setDirty(false);
    setRoute(next);
    document.getElementById("workspace-main")?.focus();
  }
  const currentRoute =
    ((route === "settings" || route === "overview") && profile.role !== "DIRECTOR") ||
    (route === "forecast" && profile.role === "STANDARD_USER")
      ? "mywork"
      : route;
  function changeChoice(id: string) {
    setChoice((current) => ({ ...current, id, generation: current.generation + 1 }));
    try {
      if (id !== "__all__")
        localStorage.setItem(
          preference(profile.subject),
          JSON.stringify({ version: 1, entityId: id }),
        );
    } catch {
      /* The current selection remains valid when storage is unavailable. */
    }
  }
  return (
    <div className={`workspace-shell${collapsed ? " navigation-collapsed" : ""}`}>
      <a className="skip-link" href="#workspace-main">
        Skip to content
      </a>
      <aside className="sidebar" aria-label="Workspace navigation">
        <div className="brand">
          <strong>Air Concepts</strong>
          <span>Financial admin</span>
        </div>
        <button
          className="navigation-toggle"
          type="button"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed((value) => !value)}
        >
          {collapsed ? "Expand navigation" : "Collapse navigation"}
        </button>
        <nav aria-label="Main navigation">
          {profile.role === "DIRECTOR" ? (
            <button
              type="button"
              aria-current={currentRoute === "overview" ? "page" : undefined}
              onClick={() => navigate("overview")}
            >
              Overview
            </button>
          ) : null}
          <button
            type="button"
            aria-current={currentRoute === "mywork" ? "page" : undefined}
            onClick={() => navigate("mywork")}
          >
            My Work
          </button>
          <button
            type="button"
            aria-current={currentRoute === "invoices" ? "page" : undefined}
            onClick={() => navigate("invoices")}
          >
            Invoices
          </button>
          <button
            type="button"
            aria-current={currentRoute === "jobs" ? "page" : undefined}
            onClick={() => navigate("jobs")}
          >
            Service jobs
          </button>
          {profile.role !== "STANDARD_USER" ? (
            <button
              type="button"
              aria-current={currentRoute === "forecast" ? "page" : undefined}
              onClick={() => navigate("forecast")}
            >
              Forecast
            </button>
          ) : null}
          {profile.role === "DIRECTOR" ? (
            <button
              type="button"
              aria-current={currentRoute === "settings" ? "page" : undefined}
              onClick={() => navigate("settings")}
            >
              Settings
            </button>
          ) : null}
        </nav>
        <div className="account-area">
          <p>
            Signed in as <strong>{profile.displayName}</strong>
          </p>
          <p className="supporting">{roleNames[profile.role]}</p>
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </aside>
      <div className="workspace-content">
        <header className="context-bar">
          <EntitySelector
            entities={entities}
            selectedId={aggregate ? "__all__" : (selected?.id ?? null)}
            allowAll={
              currentRoute === "mywork" ||
              currentRoute === "overview" ||
              (currentRoute === "forecast" && profile.role !== "STANDARD_USER")
            }
            onSelect={(id) => {
              if (dirty && !window.confirm("Discard unsaved changes before switching entity?"))
                return;
              setDirty(false);
              changeChoice(id);
            }}
          />
          <div
            className="connection-info"
            hidden={aggregate}
            aria-label="Selected entity Xero connection"
          >
            {connection.status === "loading" ? <p role="status">Checking connection…</p> : null}
            {connection.status === "error" ? (
              <Failure
                error={connection.error}
                onRetry={connection.refresh}
                label="Retry connection"
              />
            ) : null}
            {connection.data ? (
              <>
                <p>
                  <span
                    className={`badge ${
                      connection.data.status === "CONNECTED"
                        ? connection.data.errorCode
                          ? "warning"
                          : "success"
                        : connection.data.status === "NEEDS_RECONNECTION"
                          ? "warning"
                          : "error"
                    }`}
                  >
                    {connection.data.status === "CONNECTED"
                      ? "Connected to Xero"
                      : connection.data.status === "NEEDS_RECONNECTION"
                        ? "Needs reconnection"
                        : "Not connected"}
                  </span>
                </p>
                {connection.data.organisationName ? (
                  <p>{connection.data.organisationName}</p>
                ) : null}
                <p className="supporting">
                  Connection last confirmed: {formatFetchTime(connection.data.lastConfirmedAt)}
                </p>
              </>
            ) : null}
            {selected ? (
              <button
                type="button"
                disabled={connection.status === "loading" || connection.status === "refreshing"}
                onClick={connection.refresh}
              >
                Refresh connection
              </button>
            ) : null}
          </div>
        </header>
        <main id="workspace-main" className="workspace-main" tabIndex={-1}>
          {accessError ? (
            <Failure error={accessError} onRetry={onAccessDenied} label="Check access again" />
          ) : null}
          {currentRoute === "settings" && profile.role === "DIRECTOR" ? (
            <Suspense fallback={<Loading label="Opening Settings…" />}>
              <Settings
                api={api}
                profile={profile}
                entities={entities}
                onDirty={setDirty}
                onEntitiesChanged={() => {
                  onEntitiesChanged();
                  connection.refresh();
                }}
                onAccessDenied={onAccessDenied}
              />
            </Suspense>
          ) : currentRoute === "overview" && entities.length ? (
            <Suspense fallback={<Loading label="Opening Overview…" />}>
              <DirectorOverview
                key={`${profile.subject}:${profile.role}`}
                api={api}
                profile={profile}
                entity={selected ?? null}
                entities={entities}
                onDirty={setDirty}
                onAccessDenied={onAccessDenied}
                onSelectEntity={(id) => {
                  setDirty(false);
                  changeChoice(id);
                }}
              />
            </Suspense>
          ) : currentRoute === "mywork" && entities.length ? (
            <Suspense fallback={<Loading label="Opening My Work…" />}>
              <MyWork
                key={`${profile.subject}:${profile.role}`}
                api={api}
                profile={profile}
                entity={selected ?? null}
                entities={entities}
                onDirty={setDirty}
                onAccessDenied={onAccessDenied}
                onSelectEntity={(id) => {
                  setDirty(false);
                  changeChoice(id);
                }}
              />
            </Suspense>
          ) : currentRoute === "forecast" ? (
            <Suspense fallback={<Loading label="Opening Forecast…" />}>
              <Forecast
                key={`${profile.subject}:${profile.role}:${aggregate ? "all" : selected?.id}:${choice.generation}`}
                api={api}
                profile={profile}
                entity={selected ?? null}
                entities={entities}
                combined={aggregate}
                onDirty={setDirty}
                onAccessDenied={onAccessDenied}
              />
            </Suspense>
          ) : currentRoute === "jobs" && selected ? (
            <Suspense fallback={<Loading label="Opening service jobs…" />}>
              <JobsList
                key={`${profile.subject}:${profile.role}:${selected.id}:${choice.generation}`}
                api={api}
                entity={selected}
                profile={profile}
                onAccessDenied={onAccessDenied}
                onDirty={setDirty}
              />
            </Suspense>
          ) : selected ? (
            <Invoices
              key={`${profile.subject}:${profile.role}:${selected.id}:${choice.generation}`}
              api={api}
              entity={selected}
              profile={profile}
              onAccessDenied={onAccessDenied}
              onSettings={() => navigate("settings")}
            />
          ) : (
            <>
              <h1>
                {currentRoute === "jobs"
                  ? "Service jobs"
                  : currentRoute === "mywork"
                    ? "My Work"
                    : currentRoute === "overview"
                      ? "Overview"
                      : "Invoices"}
              </h1>
              <Notice>
                No entities are available.{" "}
                {profile.role === "DIRECTOR" ? (
                  <button type="button" onClick={() => navigate("settings")}>
                    Add an entity in Settings
                  </button>
                ) : (
                  "Ask a Director to configure your entities."
                )}
              </Notice>
            </>
          )}
        </main>
      </div>
    </div>
  );
}
