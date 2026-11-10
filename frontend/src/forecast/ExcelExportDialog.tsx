import { useState } from "react";
import { ApiError, type ForecastFile } from "../api/client";
import { useAction } from "../api/useAction";
import { Modal } from "../ui/Modal";
import { Failure, Notice } from "../ui/Feedback";
import { formatFetchTime } from "../ui/format";
import { type ForecastContext, handleAccess, useDirty } from "./context";
export function ExcelExportDialog(props: ForecastContext & { onClose(): void }) {
  const [file, setFile] = useState<File | null>(null);
  const [validation, setValidation] = useState("");
  const [ready, setReady] = useState<ForecastFile | null>(null);
  const action = useAction(`${props.subject}:${props.entity.id}:${props.month}:export`);
  useDirty(file !== null || ready !== null, props.onDirty);
  const close = () => {
    if ((!file && !ready) || window.confirm("Discard this workbook generation?")) props.onClose();
  };
  function download() {
    if (!ready) return;
    const url = URL.createObjectURL(ready.blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = ready.filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
    props.onClose();
  }
  return (
    <Modal title="Export forecast to Excel" onClose={close}>
      <p>
        {props.entity.displayName} · {props.month}
      </p>
      {ready ? (
        <div className="forecast-export-notice">
          <Notice tone={ready.reconciled ? "success" : "warning"}>
            Forecast ready for {props.entity.displayName}, {props.month}, generated{" "}
            {formatFetchTime(ready.generatedAt)}. Download completed workbook?
          </Notice>
          {!ready.reconciled ? <p role="alert">Workbook totals need review before use.</p> : null}
          {ready.warnings.length ? (
            <ul>
              {ready.warnings.map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          ) : null}
          <div className="forecast-actions">
            <button type="button" onClick={download}>
              Download
            </button>
            <button type="button" onClick={props.onClose}>
              Discard
            </button>
          </div>
        </div>
      ) : (
        <form
          className="forecast-form"
          onSubmit={async (event) => {
            event.preventDefault();
            if (!file) return;
            const result = await action.run(
              (signal) => props.api.exportForecast(props.entity.id, props.month, file, signal),
              (error) => handleAccess(props.onAccessDenied, error),
            );
            if (result) setReady(result);
          }}
        >
          <label>
            Forecast template (.xlsx)
            <input
              type="file"
              accept=".xlsx"
              disabled={action.busy}
              required
              onChange={(event) => {
                const selected = event.target.files?.[0];
                setValidation("");
                if (!selected) {
                  setFile(null);
                  return;
                }
                if (!/\.xlsx$/i.test(selected.name) || selected.size > 5 * 1024 * 1024) {
                  setValidation("Choose a .xlsx workbook no larger than 5 MiB.");
                  setFile(null);
                  return;
                }
                setFile(selected);
              }}
            />
          </label>
          <p>
            The workbook uses the same stored forecast data as the dashboard. Uploaded files are
            processed in memory.
          </p>
          {validation ? <p role="alert">{validation}</p> : null}
          {action.error ? <Failure error={action.error} /> : null}
          {action.error instanceof ApiError && action.error.problems.length ? (
            <ul>
              {action.error.problems.map((problem, i) => (
                <li key={i}>
                  {problem.sheet}
                  {problem.cell ? `!${problem.cell}` : ""}: {problem.expected}
                </li>
              ))}
            </ul>
          ) : null}
          <div className="forecast-actions">
            <button type="submit" disabled={!file || action.busy}>
              {action.busy ? "Generating…" : "Generate workbook"}
            </button>
            <button type="button" onClick={close}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
