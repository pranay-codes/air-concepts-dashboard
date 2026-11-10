# Xero request diagnostics

Use this when a Xero call fails and the error text does not show enough about the response. The diagnostics log one line per Xero request, so the same tool works for any future error.

## Where it applies

Every request made through the accounting `RestClient` (invoices, forecast sync, attachments) is covered. The OAuth client is not covered, so tokens never pass through it.

## Enable it (Test environment only)

On Test, set these SSM parameters under `/air-concepts/test/`. The Test deploy (`infra/deploy/deploy-test.sh`) writes them to the backend environment, and the backend reads them at startup:

| SSM parameter | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `backend/diagnostics/xero-enabled` | `DASHBOARD_DIAGNOSTICS_XERO_ENABLED` | `false` | Logs one `XERO_DIAGNOSTIC` line per Xero request. |
| `backend/diagnostics/xero-include-bodies` | `DASHBOARD_DIAGNOSTICS_XERO_INCLUDE_BODIES` | `false` | Also logs response bodies, truncated. Synthetic Test data only. |
| `backend/diagnostics/xero-max-body-chars` | `DASHBOARD_DIAGNOSTICS_XERO_MAX_BODY_CHARS` | `4000` | Truncation length when bodies are included. |

Steps: create or set the parameter, redeploy Test (CI deploys after merge to `main`), then check the backend logs. To turn it off, set `xero-enabled` to `false` and redeploy. For a local run, set the environment variable in the terminal before starting the backend.

The wiring only reaches Test after the branch is merged and deployed. Live is not handled by this script.

Do not enable this on Live. Live is released only through the manually triggered workflow, and this change adds no Live setting.

## What a line contains

Example shape (values are illustrative):

```text
XERO_DIAGNOSTIC method=GET path=/api.xro/2.0/Invoices query=[where=present order=UpdatedDateUTC ASC,InvoiceID ASC page=1 pageSize=100 summaryOnly=true] status=200 ms=412 bytes=18211 shape=[fields=2 pagination={page=1,pageSize=100,pageCount=1,itemCount=12} Invoices=array(12)]
```

- **Logged:** method, path, safe query values (`page`, `pageSize`, `summaryOnly`, `order`, `unitdp`), status, timing, byte size, top-level field count, allowlisted envelope keys, array sizes, and pagination numbers.
- **Never logged:** headers, tokens, the `where` value (it can hold search text), or any response value unless include-bodies is on.
- **Failure path:** the caller still receives the same response and the same error. Diagnostics read a copy of the body and cannot change the outcome.

## Reading a failure

1. Find the `XERO_DIAGNOSTIC` line for the failing request, by path and time.
2. Compare the `shape=` field with what the code expects. For example, a page without `pagination=` explains a `pagination missing` failure.
3. If the field you need is not in the allowlist, add it to `ENVELOPE_KEYS` in `XeroRequestDiagnostics`. Do not log values to get it.

## Turn it off

Remove the variables and restart. Remove the feature entirely once the investigation is closed.
