import { useCallback, useState } from "react";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatMoney } from "../ui/format";
import { DocumentEditor, type AdjustmentMode } from "./AdjustmentDialog";
import { type ForecastContext, handleAccess } from "./context";
export function XeroItems(props: ForecastContext & { revision: number }) {
  const [type, setType] = useState("ACCREC");
  const [settled, setSettled] = useState(false);
  const [page, setPage] = useState(1);
  const [editing, setEditing] = useState<{ id: string; mode: AdjustmentMode } | null>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastDocuments(props.entity.id, page, type, settled, signal);
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, page, type, settled, props.onAccessDenied],
  );
  const resource = useResource(
    `${props.subject}:${props.entity.id}:${props.financialYear}:${page}:${type}:${settled}:${props.revision}`,
    load,
  );
  return (
    <section>
      <h2>Xero items</h2>
      <div className="forecast-actions">
        <label>
          Document type
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(1);
            }}
          >
            <option value="ACCREC">Sales invoices</option>
            <option value="ACCPAY">Supplier bills</option>
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            checked={settled}
            onChange={(e) => {
              setSettled(e.target.checked);
              setPage(1);
            }}
          />{" "}
          Include settled documents
        </label>
      </div>
      {resource.status === "loading" ? <Loading /> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.data?.items.length === 0 ? (
        <Notice>No matching stored documents. Sync Xero or change these filters.</Notice>
      ) : null}
      {resource.data?.items.map((doc) => (
        <article className="forecast-card" key={doc.id}>
          <h3>
            {doc.number ?? doc.id.slice(0, 8)} · {doc.contact}
          </h3>
          <p>
            {formatMoney(doc.amountDue, "NZD")} · Due {formatDate(doc.dueDate)}
          </p>
          <span className="badge information">{doc.status}</span>
          {doc.adjustment.excluded ? (
            <span className="badge warning">Excluded: {doc.adjustment.exclusionReason}</span>
          ) : null}
          {doc.adjustment.dateConflictSince ? (
            <span className="badge warning">Schedule needs review</span>
          ) : null}
          {doc.outstanding ? (
            <div className="forecast-row-actions">
              <button type="button" onClick={() => setEditing({ id: doc.id, mode: "date" })}>
                Change date
              </button>
              <button type="button" onClick={() => setEditing({ id: doc.id, mode: "split" })}>
                Split
              </button>
              <button type="button" onClick={() => setEditing({ id: doc.id, mode: "exclude" })}>
                {doc.adjustment.excluded ? "Include" : "Exclude"}
              </button>
              {doc.adjustment.dateConflictSince ? (
                <button type="button" onClick={() => setEditing({ id: doc.id, mode: "conflict" })}>
                  Review conflict
                </button>
              ) : null}
            </div>
          ) : null}
        </article>
      ))}
      {resource.data ? (
        <div className="forecast-actions">
          <p>
            Page {page} · {resource.data.itemCount} matching records
          </p>
          <button type="button" disabled={page === 1} onClick={() => setPage((p) => p - 1)}>
            Previous documents
          </button>
          <button
            type="button"
            disabled={page * resource.data.pageSize >= resource.data.itemCount}
            onClick={() => setPage((p) => p + 1)}
          >
            Next documents
          </button>
        </div>
      ) : null}
      {editing ? (
        <DocumentEditor
          {...props}
          documentId={editing.id}
          mode={editing.mode}
          onClose={() => setEditing(null)}
        />
      ) : null}
    </section>
  );
}
