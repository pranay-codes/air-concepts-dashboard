import { useCallback, useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, EntityVworkTemplate, Profile } from "../api/contracts";
import { isVworkTemplateRef } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";

interface Props {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  onDirty(value: boolean): void;
  onAccessDenied(): void;
}

export function VworkTemplatesSection(props: Props) {
  const { api, entity, profile, onAccessDenied } = props;
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.entityVworkTemplates(entity.id, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, onAccessDenied],
  );
  const resource = useResource(`vwork-templates:${profile.subject}:${entity.id}`, load);
  return (
    <>
      {resource.status === "loading" ? (
        <Loading label={`Loading ${entity.displayName} vWork templates…`} />
      ) : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} />
      ) : null}
      {resource.data ? <TemplatesForm {...props} initial={resource.data} /> : null}
    </>
  );
}

function TemplatesForm({
  api,
  entity,
  profile,
  onDirty,
  onAccessDenied,
  initial,
}: Props & { initial: EntityVworkTemplate[] }) {
  const [lines, setLines] = useState(initial.map((item) => item.templateRef).join("\n"));
  const [selected, setSelected] = useState(
    initial.find((item) => item.isDefault)?.templateRef ?? "",
  );
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const action = useAction(`vwork-templates-save:${profile.subject}:${entity.id}`);
  const refs = lines
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
  const options = [...new Set(refs)];
  const prefix = `vwork-templates-${entity.id}`;
  function changed() {
    setSaved(false);
    setError("");
    onDirty(true);
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (
      refs.length < 1 ||
      refs.length > 20 ||
      refs.some((ref) => !isVworkTemplateRef(ref)) ||
      options.length !== refs.length
    ) {
      setError("Enter 1 to 20 unique template references, each at most 120 characters.");
      document.getElementById(`${prefix}-refs`)?.focus();
      return;
    }
    if (!selected || !refs.includes(selected)) {
      setError("Choose a default template.");
      document.getElementById(`${prefix}-default`)?.focus();
      return;
    }
    if (
      !window.confirm(
        `Save ${refs.length} vWork template${refs.length === 1 ? "" : "s"} for ${entity.displayName}? Default: ${selected}.`,
      )
    )
      return;
    const templates = refs.map((templateRef) => ({
      templateRef,
      isDefault: templateRef === selected,
    }));
    const result = await action.run(
      (signal) => api.saveEntityVworkTemplates(entity.id, { templates }, signal),
      (failure) => {
        if (isAccessFailure(failure)) onAccessDenied();
      },
    );
    if (result) {
      setLines(result.map((item) => item.templateRef).join("\n"));
      setSelected(result.find((item) => item.isDefault)?.templateRef ?? "");
      setSaved(true);
      onDirty(false);
    }
  }
  return (
    <form
      className="card settings-form"
      aria-label={`vWork templates for ${entity.displayName}`}
      onSubmit={submit}
      noValidate
    >
      <h3>{entity.displayName} vWork templates</h3>
      <p>
        Enter one template reference per line. Each reference can belong to only one entity.
        References are not checked against vWork yet.
      </p>
      <label htmlFor={`${prefix}-refs`}>Template references</label>
      <textarea
        id={`${prefix}-refs`}
        value={lines}
        disabled={action.busy}
        aria-describedby={error ? `${prefix}-error` : undefined}
        onChange={(event) => {
          setLines(event.target.value);
          changed();
        }}
        rows={4}
      />
      <label htmlFor={`${prefix}-default`}>Default template</label>
      <select
        id={`${prefix}-default`}
        value={options.includes(selected) ? selected : ""}
        disabled={action.busy}
        aria-invalid={error === "Choose a default template."}
        aria-describedby={error === "Choose a default template." ? `${prefix}-error` : undefined}
        onChange={(event) => {
          setSelected(event.target.value);
          changed();
        }}
      >
        <option value="">Choose a default</option>
        {options.map((ref) => (
          <option key={ref} value={ref}>
            {ref}
          </option>
        ))}
      </select>
      {error ? (
        <p id={`${prefix}-error`} role="alert">
          {error}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      {saved ? <Notice tone="success">vWork templates saved.</Notice> : null}
      <button type="submit" className="primary" disabled={action.busy}>
        {action.busy ? "Saving…" : "Save vWork templates"}
      </button>
    </form>
  );
}
