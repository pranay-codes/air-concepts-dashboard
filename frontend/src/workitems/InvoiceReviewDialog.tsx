import { useCallback } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import { formatMoney } from "../ui/format";
import { Modal } from "../ui/Modal";

export function InvoiceReviewDialog({
  api,
  entity,
  profile,
  itemId,
  onClose,
  onCompleted,
  onAccessDenied,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  itemId: string;
  onClose(): void;
  onCompleted(outcome: "CREATED" | "LINKED_EXISTING"): void;
  onAccessDenied(): void;
}) {
  const scope = `${profile.subject}:${profile.role}:${entity.id}:${itemId}`;
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.invoiceDraftReview(entity.id, itemId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, itemId, onAccessDenied],
  );
  const review = useResource(`invoice-review:${scope}`, load);
  const action = useAction(`invoice-create:${scope}`);
  async function submit() {
    const reviewed = review.data;
    if (!reviewed || action.error) return;
    const result = await action.run(
      (signal) =>
        api.prepareInvoice(entity.id, itemId, reviewed.version, reviewed.fingerprint, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result?.outcome === "CREATED" || result?.outcome === "LINKED_EXISTING")
      onCompleted(result.outcome);
  }
  return (
    <Modal
      title="Review invoice"
      onClose={() => {
        if (!action.busy) onClose();
      }}
    >
      {review.status === "loading" ? <Loading label="Preparing invoice details…" /> : null}
      {review.status === "error" ? (
        <Failure error={review.error} onRetry={review.refresh} label="Retry review" />
      ) : null}
      {review.data ? (
        <>
          <p>This creates a test invoice in the mock provider.</p>
          <dl>
            <dt>Entity</dt>
            <dd>{entity.displayName}</dd>
            <dt>Xero organisation</dt>
            <dd>{review.data.organisationName}</dd>
            <dt>Customer</dt>
            <dd>{review.data.customer}</dd>
            <dt>Quote</dt>
            <dd>{review.data.quoteNumber}</dd>
            <dt>Reference</dt>
            <dd>{review.data.reference}</dd>
            <dt>Lines</dt>
            <dd>{review.data.lineCount}</dd>
            <dt>Quote total</dt>
            <dd>{formatMoney(review.data.total, review.data.currency)}</dd>
          </dl>
          {action.error ? <Failure error={action.error} /> : null}
          <button
            type="button"
            className="primary"
            disabled={action.busy || Boolean(action.error)}
            onClick={() => void submit()}
          >
            {action.busy ? "Creating invoice…" : "Create test invoice"}
          </button>
        </>
      ) : null}
      <button type="button" disabled={action.busy} onClick={onClose}>
        Cancel
      </button>
    </Modal>
  );
}
