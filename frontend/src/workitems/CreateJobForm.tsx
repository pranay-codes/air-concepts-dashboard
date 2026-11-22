import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import type { CreateJobDraft, CreateJobRequest } from "./contracts";

export function CreateJobForm({
  api,
  entity,
  itemId,
  profile,
  onDirty,
  onCancel,
  onCompleted,
  onAccessDenied,
  retryOperationId,
}: {
  api: DashboardApi;
  entity: Entity;
  itemId: string;
  profile: Profile;
  onDirty(value: boolean): void;
  onCancel(): void;
  onCompleted(): void;
  onAccessDenied(): void;
  retryOperationId?: string;
}) {
  const key = `${profile.subject}:${profile.role}:${entity.id}:${itemId}`;
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.createJobDraft(entity.id, itemId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, itemId, onAccessDenied],
  );
  const resource = useResource(`job-draft:${key}`, load);
  return (
    <section aria-label="Create job">
      {resource.status === "loading" ? <Loading label="Preparing job details…" /> : null}
      {resource.status === "error" ? (
        <>
          <Failure error={resource.error} onRetry={resource.refresh} label="Retry job draft" />
          <button type="button" onClick={onCancel}>
            Back to job
          </button>
        </>
      ) : null}
      {resource.data ? (
        <DraftEditor
          key={key}
          api={api}
          entity={entity}
          itemId={itemId}
          draft={resource.data}
          onDirty={onDirty}
          onCancel={onCancel}
          onCompleted={onCompleted}
          onAccessDenied={onAccessDenied}
          retryOperationId={retryOperationId}
        />
      ) : null}
    </section>
  );
}

function DraftEditor({
  api,
  entity,
  itemId,
  draft,
  onDirty,
  onCancel,
  onCompleted,
  onAccessDenied,
  retryOperationId,
}: {
  api: DashboardApi;
  entity: Entity;
  itemId: string;
  draft: CreateJobDraft;
  onDirty(value: boolean): void;
  onCancel(): void;
  onCompleted(): void;
  onAccessDenied(): void;
  retryOperationId?: string;
}) {
  const initial = (): CreateJobRequest => ({
    version: draft.version,
    reference: draft.reference ?? "",
    templateId: draft.defaultTemplate ?? "",
    description: draft.scope[0]?.description || draft.quoteNumber,
    customer: draft.customer ?? "",
    contactPerson: draft.contactPerson ?? "",
    phone: draft.phone ?? "",
    siteAddress: draft.siteAddress ?? "",
    notes: "",
    scope: draft.scope.length
      ? draft.scope.map((line) => ({
          description: line.description ?? "",
          quantity: line.quantity ?? "",
        }))
      : [{ description: "", quantity: "" }],
  });
  const [original] = useState(initial);
  const [form, setForm] = useState(initial);
  const action = useAction(`create-job:${entity.id}:${itemId}`);
  const dirty = JSON.stringify(original) !== JSON.stringify(form);
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
  const update = (patch: Partial<CreateJobRequest>) =>
    setForm((current) => ({ ...current, ...patch }));
  const unknown =
    action.error instanceof ApiError && action.error.code === "EXTERNAL_OUTCOME_UNKNOWN";
  const valid =
    [
      form.reference,
      form.templateId,
      form.description,
      form.customer,
      form.contactPerson,
      form.phone,
      form.siteAddress,
    ].every((value) => value.trim().length > 0) &&
    form.scope.length > 0 &&
    form.scope.every((line) => line.description.trim() && line.quantity.trim());
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid || unknown) return;
    const result = await action.run(
      (signal) =>
        retryOperationId
          ? api.retryVworkOperation(entity.id, itemId, retryOperationId, form, signal)
          : api.createVworkJob(entity.id, itemId, form, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) {
      onDirty(false);
      onCompleted();
    }
  }
  const textField = (
    label: string,
    field: "reference" | "description" | "customer" | "contactPerson" | "phone" | "siteAddress",
  ) => (
    <label>
      {label}
      <input
        value={form[field]}
        required
        readOnly={field === "customer"}
        maxLength={
          field === "reference"
            ? 255
            : field === "phone"
              ? 40
              : field === "siteAddress"
                ? 2000
                : field === "description"
                  ? 4000
                  : 500
        }
        onChange={(event) => update({ [field]: event.target.value })}
      />
    </label>
  );
  return (
    <>
      <h2>{retryOperationId ? "Retry vWork job" : "Create vWork job"}</h2>
      <p>
        {entity.displayName} · Quote {draft.quoteNumber}
      </p>
      <p>Check the site address and scope before creating the job.</p>
      {action.error ? <Failure error={action.error} /> : null}
      <form onSubmit={submit}>
        {textField("Job ID", "reference")}
        {textField("Job description", "description")}
        {textField("Customer", "customer")}
        {textField("Contact person", "contactPerson")}
        {textField("Phone", "phone")}
        {textField("Site address", "siteAddress")}
        <label>
          vWork template
          <select
            required
            value={form.templateId}
            onChange={(event) => update({ templateId: event.target.value })}
          >
            <option value="">Choose a template</option>
            {draft.templates.map((template) => (
              <option key={template.templateRef} value={template.templateRef}>
                {template.templateRef}
              </option>
            ))}
          </select>
        </label>
        <fieldset>
          <legend>Scope</legend>
          {form.scope.map((line, index) => (
            <div key={index}>
              <label>
                Scope description {index + 1}
                <input
                  required
                  value={line.description}
                  onChange={(event) =>
                    update({
                      scope: form.scope.map((row, n) =>
                        n === index ? { ...row, description: event.target.value } : row,
                      ),
                    })
                  }
                />
              </label>
              <label>
                Quantity {index + 1}
                <input
                  required
                  value={line.quantity}
                  onChange={(event) =>
                    update({
                      scope: form.scope.map((row, n) =>
                        n === index ? { ...row, quantity: event.target.value } : row,
                      ),
                    })
                  }
                />
              </label>
              <button
                type="button"
                disabled={form.scope.length === 1}
                onClick={() => update({ scope: form.scope.filter((_, n) => n !== index) })}
              >
                Remove scope line {index + 1}
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={() => update({ scope: [...form.scope, { description: "", quantity: "" }] })}
          >
            Add scope line
          </button>
        </fieldset>
        <label>
          Notes (optional)
          <textarea
            maxLength={2000}
            value={form.notes ?? ""}
            onChange={(event) => update({ notes: event.target.value })}
          />
        </label>
        <div className="action-row">
          <button type="button" onClick={onCancel}>
            Back to job
          </button>
          <button type="submit" className="primary" disabled={!valid || action.busy || unknown}>
            {action.busy ? "Creating job…" : retryOperationId ? "Retry job" : "Create job"}
          </button>
        </div>
      </form>
    </>
  );
}
