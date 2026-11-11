import { useEffect, useId, useRef, useState } from "react";
import { apiMessage } from "../api/client";

/** Matches the server limit of 2000 Unicode code points. */
export const NOTE_MAX_CODE_POINTS = 2000;

/**
 * Compact note for one forecast line. Shows an "Add note" action when empty, or a truncated preview.
 * Activating either opens a small inline editor with Save and Cancel. A failed save keeps the draft.
 */
export function TransactionNoteEditor({
  label,
  note,
  onSave,
}: {
  /** Names the transaction for assistive technology, for example "INV-0017 · City Limousines". */
  label: string;
  note: string | null;
  onSave(text: string): Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(note ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const field = useRef<HTMLTextAreaElement>(null);
  const restoreFocus = useRef(false);
  const fieldId = useId();
  const hintId = useId();
  const errorId = useId();

  // Move focus into the editor on open, and back to the trigger when the editor closes.
  useEffect(() => {
    if (editing) field.current?.focus();
    else if (restoreFocus.current) {
      restoreFocus.current = false;
      trigger.current?.focus();
    }
  }, [editing]);

  useEffect(() => {
    if (!saved) return;
    const timer = setTimeout(() => setSaved(false), 2000);
    return () => clearTimeout(timer);
  }, [saved]);

  function open() {
    setDraft(note ?? "");
    setError(null);
    setEditing(true);
  }

  function close() {
    restoreFocus.current = true;
    setEditing(false);
  }

  function cancel() {
    if (saving) return;
    setDraft(note ?? "");
    setError(null);
    close();
  }

  async function save() {
    if (saving) return;
    const text = draft.trim();
    if ([...text].length > NOTE_MAX_CODE_POINTS) {
      setError(
        `Notes can be up to ${NOTE_MAX_CODE_POINTS} characters. Shorten the note and save again.`,
      );
      return;
    }
    if (text === (note ?? "")) {
      setError(null);
      close();
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSave(text);
      setSaving(false);
      setSaved(true);
      close();
    } catch (failure) {
      setSaving(false);
      setError(apiMessage(failure));
    }
  }

  if (!editing) {
    return (
      <div className="note-cell">
        {note ? (
          <button
            ref={trigger}
            type="button"
            className="note-preview"
            title={note}
            aria-label={`Edit note for ${label}: ${note}`}
            onClick={open}
          >
            {note}
          </button>
        ) : (
          <button
            ref={trigger}
            type="button"
            className="link-button note-add"
            aria-label={`Add note for ${label}`}
            onClick={open}
          >
            Add note
          </button>
        )}
        {saved ? (
          <span className="note-saved" role="status">
            Saved
          </span>
        ) : null}
      </div>
    );
  }

  return (
    <form
      className="note-editor"
      aria-busy={saving}
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          cancel();
        }
      }}
    >
      <label className="visually-hidden" htmlFor={fieldId}>
        Note for {label}
      </label>
      <textarea
        id={fieldId}
        ref={field}
        rows={2}
        value={draft}
        readOnly={saving}
        aria-describedby={error ? `${hintId} ${errorId}` : hintId}
        aria-invalid={error ? true : undefined}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
            event.preventDefault();
            void save();
          }
        }}
      />
      <div className="note-editor-actions">
        <button type="submit" className="compact-button primary" aria-disabled={saving}>
          {saving ? "Saving…" : "Save"}
        </button>
        <button type="button" className="compact-button" aria-disabled={saving} onClick={cancel}>
          Cancel
        </button>
        <span id={hintId} className="supporting">
          Leave blank to remove the note. Ctrl or Cmd + Enter saves.
        </span>
      </div>
      {error ? (
        <p id={errorId} role="alert" className="note-error">
          {error} Your text is still here.
        </p>
      ) : null}
    </form>
  );
}
