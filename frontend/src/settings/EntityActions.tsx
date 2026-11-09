import { useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { Failure } from "../ui/Feedback";
import { Modal } from "../ui/Modal";

interface RenameProps {
  api: DashboardApi;
  entity: Entity;
  onAccessDenied(): void;
  onClose(): void;
  onRenamed(): void;
}

export function RenameEntityDialog({
  api,
  entity,
  onAccessDenied,
  onClose,
  onRenamed,
}: RenameProps) {
  const [value, setValue] = useState(entity.displayName);
  const action = useAction(`rename-entity:${entity.id}`);
  const trimmed = value.trim();
  const changed = trimmed !== entity.displayName;

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!trimmed || trimmed.length > 160 || !changed) return;
    const result = await action.run(
      (signal) => api.renameEntity(entity.id, trimmed, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    // Keep the typed name on failure so the user does not lose the edit.
    if (result) onRenamed();
  }

  function requestClose() {
    if (changed && !window.confirm("Discard the changes to this entity name?")) return;
    onClose();
  }

  return (
    <Modal title="Rename entity" onClose={requestClose}>
      <form onSubmit={save} aria-label="Rename entity">
        <label htmlFor="rename-entity-name">Entity name</label>
        <input
          id="rename-entity-name"
          data-autofocus
          required
          maxLength={160}
          value={value}
          disabled={action.busy}
          onChange={(event) => setValue(event.target.value)}
        />
        <p className="supporting">The name is shown in the entity switcher and on every record.</p>
        {action.error ? <Failure error={action.error} /> : null}
        <div className="dialog-actions">
          <button type="button" disabled={action.busy} onClick={requestClose}>
            Cancel
          </button>
          <button
            type="submit"
            className="primary"
            disabled={action.busy || !trimmed || trimmed.length > 160 || !changed}
          >
            {action.busy ? "Saving…" : "Save name"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

interface ArchiveProps {
  api: DashboardApi;
  entity: Entity;
  organisationName: string | null;
  onAccessDenied(): void;
  onClose(): void;
  onArchived(): void;
}

export function ArchiveEntityDialog({
  api,
  entity,
  organisationName,
  onAccessDenied,
  onClose,
  onArchived,
}: ArchiveProps) {
  const action = useAction(`archive-entity:${entity.id}`);

  async function archive() {
    const result = await action.run(
      (signal) => api.archiveEntity(entity.id, signal).then(() => true as const),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) onArchived();
  }

  function requestClose() {
    if (!action.busy) onClose();
  }

  return (
    <Modal title={`Remove ${entity.displayName}?`} onClose={requestClose}>
      <p>
        {entity.displayName} will be archived. It is hidden from the entity switcher and from
        invoice lists.
      </p>
      <ul className="consequences">
        <li>Its invoices, assignments and attachments are kept in the dashboard and in Xero.</li>
        <li>
          Xero access is not revoked, and
          {organisationName ? ` ${organisationName} stays` : " any linked organisation stays"}{" "}
          linked to this archived entity, so it cannot be connected to another entity.
        </li>
        <li>You cannot restore an archived entity from this screen.</li>
      </ul>
      {action.error ? <Failure error={action.error} onRetry={archive} label="Try again" /> : null}
      <div className="dialog-actions">
        <button type="button" disabled={action.busy} onClick={onClose}>
          Cancel
        </button>
        <button
          type="button"
          className="danger"
          disabled={action.busy}
          onClick={() => void archive()}
        >
          {action.busy ? "Removing…" : "Remove entity"}
        </button>
      </div>
    </Modal>
  );
}
