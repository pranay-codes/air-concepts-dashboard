import { useCallback, useState, type FormEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import type { Attempt, Entity } from "../api/contracts";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { Modal } from "../ui/Modal";
import type { SettingsProps } from "./Settings";

export type EntityDialogMode =
  { kind: "add" } | { kind: "connect"; entity: Entity } | { kind: "resume"; attemptId: string };

type Step = 1 | 2 | 3 | 4;
const stepLabels = ["Name", "Sign in to Xero", "Choose organisation", "Confirm"] as const;

interface Props extends Pick<
  SettingsProps,
  "api" | "entities" | "onAccessDenied" | "openAuthorization"
> {
  mode: EntityDialogMode;
  /** Called for every close path. The parent removes any Xero return parameter from the address. */
  onClose(): void;
  /** Called once a connection is confirmed. The parent closes the dialog and shows a success notice. */
  onConfirmed(message: string): void;
  onEntitiesChanged(): void;
}

export function EntityDialog(props: Props) {
  const { api, entities, onAccessDenied, mode, onClose, onConfirmed, onEntitiesChanged } = props;
  const [step, setStep] = useState<Step>(() => (mode.kind === "add" ? 1 : 2));
  const [name, setName] = useState("");
  const [created, setCreated] = useState<Entity | null>(
    mode.kind === "connect" ? mode.entity : null,
  );
  const [selected, setSelected] = useState("");
  const createAction = useAction("entity-dialog:create");
  const startAction = useAction("entity-dialog:start");
  const confirmAction = useAction("entity-dialog:confirm");
  const attemptId = mode.kind === "resume" ? mode.attemptId : null;

  const load = useCallback(
    async (signal: AbortSignal) => {
      if (!attemptId) throw new ApiError(400, "INVALID_REQUEST");
      try {
        return await api.attempt(attemptId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, attemptId, onAccessDenied],
  );
  const resource = useResource(`xero-attempt:${attemptId ?? "none"}`, load, Boolean(attemptId));
  const attempt: Attempt | undefined = resource.data;
  const entity =
    created ?? entities.find((candidate) => candidate.id === attempt?.entityId) ?? null;
  // A ready attempt means the return from Xero has happened, so the dialog shows the organisation choice.
  const shownStep: Step = attempt?.state === "READY" && step === 2 ? 3 : step;
  const choices = attempt?.state === "READY" ? attempt.choices : [];
  const chosen = choices.find((choice) => choice.connectionId === selected);
  const completed = attempt?.state === "COMPLETED";
  const failed = attempt?.state === "FAILED";

  function startAuthorisation(target: Entity) {
    setStep(2);
    void startAction
      .run(
        (signal) => api.startConnection(target.id, signal),
        (error) => {
          if (isAccessFailure(error)) onAccessDenied();
        },
      )
      .then((result) => {
        if (result)
          (props.openAuthorization ?? ((url: string) => window.location.assign(url)))(
            result.authorizationUrl,
          );
      });
  }

  async function continueToXero(event: FormEvent) {
    event.preventDefault();
    if (created) {
      startAuthorisation(created);
      return;
    }
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 160) return;
    const result = await createAction.run(
      (signal) => api.createEntity(trimmed, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (!result) return;
    setCreated(result);
    onEntitiesChanged();
    startAuthorisation(result);
  }

  async function confirm(event: FormEvent) {
    event.preventDefault();
    if (!attemptId || !attempt || !entity || !selected) return;
    const result = await confirmAction.run(
      async (signal) => {
        const value = await api.confirmConnection(attemptId, selected, signal);
        if (value.entityId !== attempt.entityId) throw new ApiError(502, "INVALID_RESPONSE");
        return value;
      },
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) onConfirmed(`Connected ${entity.displayName} to ${chosen?.name ?? "Xero"}.`);
  }

  function requestClose() {
    // A typed name that has not been saved is unsaved input; closing it needs an explicit choice.
    if (
      mode.kind === "add" &&
      !created &&
      name.trim() &&
      !window.confirm("Discard this entity name?")
    )
      return;
    onClose();
  }

  const title =
    mode.kind === "add"
      ? "Add entity"
      : entity
        ? `Connect ${entity.displayName} to Xero`
        : "Connect to Xero";

  return (
    <Modal title={title} onClose={requestClose} describedBy="entity-dialog-steps">
      <ol id="entity-dialog-steps" className="stepper" aria-label="Progress">
        {stepLabels.map((label, index) => {
          const number = (index + 1) as Step;
          const state =
            number < shownStep || (completed && number <= 4)
              ? "complete"
              : number === shownStep
                ? "current"
                : "upcoming";
          return (
            <li
              key={label}
              className={`step ${state}`}
              aria-current={state === "current" ? "step" : undefined}
            >
              <span className="step-number" aria-hidden="true">
                {number}
              </span>
              <span>{label}</span>
            </li>
          );
        })}
      </ol>

      {shownStep === 1 ? (
        <form onSubmit={continueToXero} aria-label="Name the entity">
          <label htmlFor="new-entity-name">Entity name</label>
          <input
            id="new-entity-name"
            data-autofocus
            required
            maxLength={160}
            value={name}
            placeholder="For example, HotAS"
            disabled={createAction.busy}
            onChange={(event) => setName(event.target.value)}
          />
          <p className="supporting">Shown in the entity switcher and on every record.</p>
          <p>
            Next you&apos;ll sign in to Xero and choose the organisation this entity uses. You need
            Xero access to that organisation.
          </p>
          {createAction.error ? (
            <>
              <Failure error={createAction.error} />
              <p className="supporting">
                If the entity was created before this failed, check the Entities list before trying
                again so it is not added twice.
              </p>
            </>
          ) : null}
          <div className="dialog-actions">
            <button type="button" disabled={createAction.busy} onClick={requestClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary"
              disabled={createAction.busy || !name.trim() || name.trim().length > 160}
            >
              {createAction.busy ? "Adding entity…" : "Continue to Xero"}
            </button>
          </div>
        </form>
      ) : null}

      {shownStep === 2 && mode.kind !== "resume" ? (
        <div>
          <p>
            You&apos;ll be taken to Xero to sign in and approve access for{" "}
            <strong>{entity?.displayName ?? "this entity"}</strong>. You&apos;ll then come back here
            to choose the organisation.
          </p>
          {created && !startAction.error && !startAction.busy ? (
            <Notice>{created.displayName} was added. It is not connected to Xero yet.</Notice>
          ) : null}
          {startAction.error ? <Failure error={startAction.error} /> : null}
          <p className="supporting">
            If you close this dialog now, {entity?.displayName ?? "the entity"} stays in the list as
            Not connected. Connect it again from its card.
          </p>
          <div className="dialog-actions">
            <button type="button" disabled={startAction.busy} onClick={requestClose}>
              Cancel
            </button>
            <button
              type="button"
              className="primary"
              disabled={startAction.busy || !entity}
              onClick={() => entity && startAuthorisation(entity)}
            >
              {startAction.busy ? "Opening Xero…" : "Continue to Xero"}
            </button>
          </div>
        </div>
      ) : null}

      {mode.kind === "resume" && resource.status === "loading" ? (
        <Loading label="Checking Xero authorisation…" />
      ) : null}
      {mode.kind === "resume" && resource.status === "error" ? (
        <Failure
          error={resource.error}
          onRetry={resource.refresh}
          label="Check authorisation again"
        />
      ) : null}

      {mode.kind === "resume" && attempt && shownStep === 2 && !completed && !failed ? (
        <div>
          <Notice>
            Authorisation is still in progress. Return to this page after Xero has finished, then
            check again.
          </Notice>
          <div className="dialog-actions">
            <button type="button" onClick={requestClose}>
              Close
            </button>
            <button type="button" onClick={resource.refresh}>
              Check again
            </button>
          </div>
        </div>
      ) : null}

      {mode.kind === "resume" && failed ? (
        <div>
          <Notice tone="warning">
            Authorisation failed or expired{attempt?.errorCode ? ` (${attempt.errorCode})` : ""}.
            Start again to connect {entity?.displayName ?? "this entity"}.
          </Notice>
          <div className="dialog-actions">
            <button type="button" onClick={requestClose}>
              Close
            </button>
            <button
              type="button"
              className="primary"
              disabled={startAction.busy || !entity}
              onClick={() => entity && startAuthorisation(entity)}
            >
              Start again
            </button>
          </div>
          {startAction.error ? <Failure error={startAction.error} /> : null}
        </div>
      ) : null}

      {shownStep === 3 && attempt?.state === "READY" ? (
        choices.length ? (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (selected) setStep(4);
            }}
          >
            <label htmlFor="xero-organisation">Xero organisation</label>
            <select
              id="xero-organisation"
              data-autofocus
              value={selected}
              onChange={(event) => setSelected(event.target.value)}
            >
              <option value="">Choose an authorised organisation</option>
              {choices.map((choice) => (
                <option key={choice.connectionId} value={choice.connectionId}>
                  {choice.name}
                </option>
              ))}
            </select>
            <p className="supporting">
              Choose the organisation that belongs to {entity?.displayName ?? "this entity"}.
            </p>
            <div className="dialog-actions">
              <button type="button" onClick={requestClose}>
                Cancel
              </button>
              <button type="submit" className="primary" disabled={!selected}>
                Continue
              </button>
            </div>
          </form>
        ) : (
          <div>
            <Notice tone="warning">
              No authorised organisations are available. Start again and grant access to the
              organisation in Xero.
            </Notice>
            <div className="dialog-actions">
              <button type="button" onClick={requestClose}>
                Close
              </button>
              <button
                type="button"
                className="primary"
                disabled={startAction.busy || !entity}
                onClick={() => entity && startAuthorisation(entity)}
              >
                Start again
              </button>
            </div>
          </div>
        )
      ) : null}

      {shownStep === 4 && attempt?.state === "READY" ? (
        <form onSubmit={confirm} aria-label="Confirm Xero connection">
          <p>
            <strong>{entity?.displayName ?? "This entity"}</strong> will use{" "}
            <strong>{chosen?.name ?? "the selected organisation"}</strong>.
          </p>
          <p className="supporting">
            An organisation can be linked to only one entity. Invoices stay in Xero and are read
            through this connection.
          </p>
          {confirmAction.error ? <Failure error={confirmAction.error} /> : null}
          <div className="dialog-actions">
            <button type="button" disabled={confirmAction.busy} onClick={() => setStep(3)}>
              Back
            </button>
            <button type="submit" className="primary" disabled={confirmAction.busy || !selected}>
              {confirmAction.busy ? "Confirming…" : "Confirm connection"}
            </button>
          </div>
        </form>
      ) : null}

      {mode.kind === "resume" && completed ? (
        <div>
          <Notice tone="success">
            {entity?.displayName ?? "This entity"} is already connected. Close this dialog to
            continue.
          </Notice>
          <div className="dialog-actions">
            <button
              type="button"
              className="primary"
              onClick={() => {
                onEntitiesChanged();
                onClose();
              }}
            >
              Close
            </button>
          </div>
        </div>
      ) : null}
    </Modal>
  );
}
