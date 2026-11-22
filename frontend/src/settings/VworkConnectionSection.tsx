import { useCallback, useRef, useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { Modal } from "../ui/Modal";

export function VworkConnectionSection({
  api,
  profile,
  onAccessDenied,
  onDirty,
}: {
  api: DashboardApi;
  profile: Profile;
  onAccessDenied(): void;
  onDirty(value: boolean): void;
}) {
  const [open, setOpen] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const [hasInput, setHasInput] = useState(false);
  const action = useAction(`vwork-connection:${profile.subject}`);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.vworkConnection(signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, onAccessDenied],
  );
  const resource = useResource(`vwork-connection:${profile.subject}`, load);
  const status = resource.data?.status;
  function close() {
    if (action.busy) return;
    if (hasInput && !window.confirm("Discard the entered vWork key?")) return;
    if (input.current) input.current.value = "";
    setHasInput(false);
    onDirty(false);
    setOpen(false);
  }
  function failed(error: unknown) {
    if (isAccessFailure(error)) onAccessDenied();
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = input.current?.value ?? "";
    if (!value.trim()) return;
    if (input.current) input.current.value = "";
    setHasInput(false);
    onDirty(false);
    const result = await action.run((signal) => api.connectVwork(value, signal), failed);
    if (result) {
      setOpen(false);
      resource.refresh();
    }
  }
  async function disconnect() {
    if (
      !window.confirm(
        "Disconnect vWork for all entities? Existing job details will remain available.",
      )
    )
      return;
    const result = await action.run((signal) => api.disconnectVwork(signal), failed);
    if (result) resource.refresh();
  }
  return (
    <section className="card settings-form" aria-label="vWork account">
      <h3>vWork account</h3>
      <p>This connection is shared by all entities.</p>
      {resource.status === "loading" ? <Loading label="Loading vWork connection…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} />
      ) : null}
      {status ? (
        <>
          <p>
            {status === "ACTIVE"
              ? "Connected"
              : status === "DISCONNECTED"
                ? "Not connected"
                : status === "INVALID"
                  ? "Reconnect required"
                  : "Verification pending"}
          </p>
          {status === "UNVERIFIED" ? (
            <Notice tone="warning">Saved, but we couldn't reach vWork to check it.</Notice>
          ) : null}
          {status === "INVALID" ? (
            <Notice tone="warning">Replace the key to reconnect vWork.</Notice>
          ) : null}
          <button
            type="button"
            disabled={action.busy || resource.status === "refreshing"}
            onClick={() => setOpen(true)}
          >
            {status === "DISCONNECTED" ? "Connect vWork" : "Replace key"}
          </button>
          {status !== "DISCONNECTED" ? (
            <button
              type="button"
              disabled={action.busy || resource.status === "refreshing"}
              onClick={disconnect}
            >
              Disconnect vWork
            </button>
          ) : null}
        </>
      ) : null}
      {!open && action.error ? <Failure error={action.error} /> : null}
      {open ? (
        <Modal
          title={status === "DISCONNECTED" ? "Connect vWork" : "Replace vWork key"}
          onClose={close}
        >
          <form onSubmit={submit} className="settings-form">
            <p>The key is stored securely and cannot be displayed again.</p>
            <label htmlFor="vwork-api-key">vWork API key</label>
            <input
              id="vwork-api-key"
              ref={input}
              type="password"
              autoComplete="new-password"
              maxLength={512}
              required
              data-autofocus
              disabled={action.busy}
              onChange={(event) => {
                const dirty = event.target.value.length > 0;
                setHasInput(dirty);
                onDirty(dirty);
              }}
            />
            {action.error ? <Failure error={action.error} /> : null}
            <button type="submit" className="primary" disabled={action.busy}>
              {action.busy ? "Saving…" : "Save key"}
            </button>
            <button type="button" disabled={action.busy} onClick={close}>
              Cancel
            </button>
          </form>
        </Modal>
      ) : null}
    </section>
  );
}
