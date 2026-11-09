import { useCallback, useEffect, useState } from "react";

type Request = { key: string; enabled: boolean; generation: number; reason: "context" | "refresh" };
type Snapshot<T> = { request: Request } & (
  | { status: "success"; data: T; loadedAt: Date }
  | { status: "error"; error: unknown; loadedAt: Date | null }
);
export interface Resource<T> {
  status: "idle" | "loading" | "refreshing" | "success" | "error";
  data: T | undefined;
  error: unknown;
  loadedAt: Date | null;
  refresh(): void;
}

/** One server-state owner. Supply a stable loader and a key including user, entity and every criterion. */
export function useResource<T>(
  key: string,
  load: (signal: AbortSignal) => Promise<T>,
  enabled = true,
): Resource<T> {
  const [request, setRequest] = useState<Request>({
    key,
    enabled,
    generation: 0,
    reason: "context",
  });
  const [snapshot, setSnapshot] = useState<Snapshot<T> | null>(null);
  // Reset identity during render so A→B→A never reuses A's previous successful snapshot, even before Effects run.
  if (request.key !== key || request.enabled !== enabled) {
    setRequest({ key, enabled, generation: request.generation + 1, reason: "context" });
  }
  useEffect(() => {
    if (!request.enabled) return;
    const controller = new AbortController();
    let active = true;
    Promise.resolve()
      .then(() => load(controller.signal))
      .then((data) => {
        if (active) setSnapshot({ request, status: "success", data, loadedAt: new Date() });
      })
      .catch((error: unknown) => {
        if (active)
          setSnapshot((previous) => ({
            request,
            status: "error",
            error,
            loadedAt: previous?.request.key === request.key ? previous.loadedAt : null,
          }));
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [request, load]);

  const refresh = useCallback(() => {
    if (!enabled) return;
    setRequest((current) =>
      current.key === key && current.enabled === enabled
        ? { ...current, generation: current.generation + 1, reason: "refresh" }
        : current,
    );
  }, [key, enabled]);

  if (!enabled)
    return { status: "idle", data: undefined, error: undefined, loadedAt: null, refresh };
  if (request.key === key && snapshot?.request === request) {
    return snapshot.status === "success"
      ? {
          status: "success",
          data: snapshot.data,
          loadedAt: snapshot.loadedAt,
          error: undefined,
          refresh,
        }
      : {
          status: "error",
          data: undefined,
          loadedAt: snapshot.loadedAt,
          error: snapshot.error,
          refresh,
        };
  }
  if (
    request.key === key &&
    request.reason === "refresh" &&
    snapshot?.status === "success" &&
    snapshot.request.key === key
  ) {
    return {
      status: "refreshing",
      data: snapshot.data,
      error: undefined,
      loadedAt: snapshot.loadedAt,
      refresh,
    };
  }
  return { status: "loading", data: undefined, error: undefined, loadedAt: null, refresh };
}
