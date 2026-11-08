import { useEffect, useRef, useState } from "react";

/** Explicit user actions only: one in flight, no automatic write retries, cancelled on context loss. */
export function useAction(key: string) {
  const [owner, setOwner] = useState({ key });
  if (owner.key !== key) setOwner({ key });
  const active = useRef<{ owner: typeof owner; controller: AbortController } | null>(null);
  const [state, setState] = useState<{ owner: typeof owner; busy: boolean; error: unknown }>({
    owner,
    busy: false,
    error: null,
  });
  useEffect(
    () => () => {
      if (active.current?.owner === owner) {
        active.current.controller.abort();
        active.current = null;
      }
    },
    [owner],
  );
  async function run<T>(
    operation: (signal: AbortSignal) => Promise<T>,
    onError?: (error: unknown) => void,
  ): Promise<T | undefined> {
    if (active.current?.owner === owner) return undefined;
    active.current?.controller.abort();
    const request = { owner, controller: new AbortController() };
    active.current = request;
    setState({ owner, busy: true, error: null });
    try {
      const result = await operation(request.controller.signal);
      return active.current === request && !request.controller.signal.aborted ? result : undefined;
    } catch (error) {
      if (active.current === request && !request.controller.signal.aborted) {
        setState({ owner, busy: false, error });
        onError?.(error);
      }
      return undefined;
    } finally {
      if (active.current === request) {
        active.current = null;
        setState((current) => (current.owner === owner ? { ...current, busy: false } : current));
      }
    }
  }
  return {
    busy: state.owner === owner && state.busy,
    error: state.owner === owner ? state.error : null,
    run,
  };
}
