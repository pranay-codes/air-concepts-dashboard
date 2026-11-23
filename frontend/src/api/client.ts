import type { AuthService } from "../auth/authService";

const messages = {
  CREATE_JOB_INVALID: "Complete the required job fields and choose a template for this entity.",
  VWORK_CAPABILITY_UNVERIFIED: "Job creation is unavailable until vWork templates are verified.",
  VWORK_SEARCH_UNAVAILABLE: "The vWork job could not be checked. Review it before trying again.",
  EXTERNAL_OUTCOME_UNKNOWN: "The outcome needs review before trying again.",
  QUOTE_ACCEPT_UNAVAILABLE: "Accepting quotes in Xero is unavailable for this connection.",
  QUOTE_ACCEPT_RECORD_FAILED:
    "Xero accepted the quote, but the dashboard could not record it. Review the job before trying again.",
  OPERATION_ALREADY_EXISTS: "Review the existing job operation before trying again.",
  SYNC_IN_PROGRESS: "A refresh is already running for this entity.",
  SYNC_RATE_LIMITED: "Wait one minute before refreshing this entity again.",
  QUOTE_SYNC_COMPLETENESS_UNVERIFIED:
    "Xero refresh could not confirm that all quotes were returned.",
  QUOTE_SYNC_INVOICE_CHECK_REQUIRED:
    "Xero refresh needs a confirmed invoice check before it can finish.",
  XERO_SCOPE_REQUIRED: "Xero permissions need attention from a Director in Settings.",
  FORECAST_PERIOD_INVALID: "Choose the current month or one of the previous 12 months.",
  FORECAST_INPUT_INVALID: "Check the highlighted forecast fields.",
  FORECAST_RECORD_UNAVAILABLE: "This forecast record is unavailable for the selected entity.",
  FORECAST_DOCUMENT_NOT_OUTSTANDING: "This document is no longer outstanding. Refresh its details.",
  FORECAST_SPLIT_SUM_MISMATCH: "Split amounts must add up to the outstanding balance.",
  FORECAST_EXCLUSION_REASON_REQUIRED: "Enter an exclusion reason of 1–500 characters.",
  FORECAST_TEMPLATE_INVALID: "Choose a standard v1 .xlsx workbook no larger than 5 MiB.",
  FORECAST_TEMPLATE_CAPACITY: "The workbook supports at most 200 contributions on each Xero sheet.",
  FORECAST_FORMULA_UNSUPPORTED:
    "The workbook contains unsupported formulas. Check the listed cells.",
  FORECAST_SYNC_IN_PROGRESS:
    "A forecast sync is already running. Check History before requesting another.",
  FORECAST_SYNC_TOO_MANY_CHANGES:
    "More than 200 settlement checks are needed. Ask an administrator to review and rerun sync.",
  FORECAST_SYNC_EXPIRED: "This sync expired. Check History and request a new run.",
  FORECAST_SYNC_FAILED: "Sync did not complete. Check History before retrying.",
  FORECAST_CURRENCY_UNSUPPORTED:
    "Foreign-currency documents need an approved conversion rule before this forecast can be shown.",
  FORECAST_TENANT_CHANGED:
    "The Xero organisation changed. Ask an administrator to review the forecast mapping.",
  FORECAST_CONFLICT_UNAVAILABLE: "This conflict was already resolved. Refresh the document.",
  SESSION_ENDED: "Your session has ended. Sign in again.",
  ACCESS_DENIED: "Your access has changed. Check your permissions or contact a Director.",
  INVALID_REQUEST: "Check the entered values and try again.",
  INVALID_RESPONSE: "This section returned an incomplete response. Try again.",
  RECORD_UNAVAILABLE: "This record is no longer available. Refresh the list.",
  RECORD_CHANGED: "This record changed. Refresh its details before trying again.",
  WORK_ITEM_CHANGED: "This job changed. Refresh its details before trying again.",
  OPERATION_IN_PROGRESS: "Resolve the existing operation before closing this job.",
  WORK_ITEM_NOT_FOUND: "This job is no longer available. Refresh the list.",
  STAGE_ACTION_NOT_ALLOWED: "This action is no longer available. Refresh the job.",
  REASON_REQUIRED: "Enter a reason of 1–500 characters.",
  RECORD_CONFLICT: "These details conflict with an existing record. Refresh and check them.",
  REQUEST_FAILED: "The request could not be completed. Try again.",
  REQUEST_TIMEOUT: "The request timed out. Try again.",
  NETWORK_ERROR: "Could not reach the dashboard. Check your connection and try again.",
  REQUEST_RATE_LIMITED: "Requests are limited. Wait before retrying.",
  XERO_NOT_CONNECTED:
    "This entity is not connected to Xero. Ask a Director to connect it in Settings.",
  VWORK_KEY_REJECTED: "vWork rejected this key. Check it and try again.",
  VWORK_KEY_INVALID: "Enter a valid vWork API key.",
  VWORK_KEY_NOT_CONFIGURED: "The vWork encryption setup needs attention from the setup owner.",
  VWORK_NOT_CONFIGURED: "The vWork integration is not configured.",
  VWORK_CONNECTION_CHANGED: "The vWork connection changed. Refresh and try again.",
  VWORK_VERIFICATION_FAILED: "The vWork connection could not be verified. Try again.",
  VWORK_TEMPLATE_IN_USE: "A template is already assigned to another entity. Check the references.",
  VWORK_TEMPLATES_INVALID: "Enter unique template references and choose one default.",
  XERO_RECONNECT_REQUIRED: "Xero access needs to be reconnected by a Director in Settings.",
  XERO_ORGANISATION_ACCESS_DENIED:
    "Xero refused access to this entity. Ask a Director to check its connection.",
  XERO_RATE_LIMITED: "Xero has limited requests. Wait before retrying.",
  XERO_PROVIDER_FAILED: "Xero could not confirm this request. Retry or check the connection.",
  XERO_UNAVAILABLE: "Xero is unavailable. Retry when the connection is restored.",
  XERO_INVALID_RESPONSE: "Xero returned incomplete data. Retry this request.",
  XERO_CONFIGURATION_ERROR: "The Xero setup needs attention from the setup owner.",
  XERO_CONNECTION_CHANGED: "This entity's connection changed. Refresh before trying again.",
  XERO_TENANT_CHANGED:
    "This entity's Xero connection changed. Ask a Director to review its jobs before continuing.",
  XERO_CONNECTION_CONFLICT:
    "This Xero organisation is already connected or the request changed. Refresh Settings.",
  XERO_AUTH_EXPIRED: "This authorisation attempt expired. Connect Xero again.",
  XERO_CONSENT_DENIED: "Xero permission was not granted. You can connect again when ready.",
  XERO_AUTHORIZATION_FAILED: "Xero authorisation could not be confirmed. Connect again.",
  XERO_TENANT_NOT_AUTHORISED: "Choose a currently authorised Xero organisation.",
  XERO_DOCUMENT_UNAVAILABLE: "Xero could not find this document. Refresh the list.",
  ATTACHMENT_UNAVAILABLE: "This attachment is no longer available. Refresh the document.",
  ATTACHMENT_TOO_LARGE: "This attachment exceeds the 25 MiB download limit.",
  ASSIGNEE_UNAVAILABLE: "Choose a currently active dashboard user.",
  USER_CHANGED: "The user changed during the request. Refresh their details.",
  USER_CONFLICT: "These user details conflict with an existing or pending account. Refresh Users.",
  USER_UNAVAILABLE: "This user is no longer available. Refresh Users.",
} as const;
type Code = keyof typeof messages;

/** Deliberately contains no raw body, request URL, token or provider exception cause. */
export class ApiError extends Error {
  readonly retryAt: number | undefined;
  constructor(
    readonly status: number,
    readonly code: Code,
    readonly upstreamStatus?: number,
    readonly retryAfterSeconds?: number,
    readonly problems: TemplateProblem[] = [],
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(messages[code]);
    this.retryAt =
      retryAfterSeconds === undefined ? undefined : Date.now() + retryAfterSeconds * 1000;
  }
}
export interface TemplateProblem {
  sheet: string;
  cell: string;
  expected: string;
}
export interface ForecastFile {
  blob: Blob;
  filename: string;
  reconciled: boolean;
  warnings: string[];
  generatedAt: string;
}
export function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : messages.REQUEST_FAILED;
}
export function isAccessFailure(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}
export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

type JsonOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  timeoutMs?: number;
};
const binaryTypes = new Set([
  "application/octet-stream",
  "application/pdf",
  "image/png",
  "image/jpeg",
  "image/gif",
  "image/webp",
]);

/** Shared transport; feature adapters supply generated types plus boundary validators. */
export class ApiClient {
  private readonly baseUrl: string;
  constructor(
    baseUrl: string,
    private readonly auth: AuthService,
    private readonly fetcher: typeof fetch = fetch,
  ) {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    if (this.baseUrl) {
      try {
        const url = new URL(this.baseUrl);
        const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
        if (
          (url.protocol !== "https:" && !(url.protocol === "http:" && loopback)) ||
          url.username ||
          url.password ||
          url.search ||
          url.hash ||
          url.pathname !== "/"
        )
          throw new Error();
      } catch {
        throw new ApiError(0, "INVALID_REQUEST");
      }
    }
  }

  json<T>(
    path: string,
    validate: (value: unknown) => value is T,
    signal?: AbortSignal,
    options: JsonOptions = {},
  ): Promise<T> {
    return this.execute(path, signal, options, "application/json", async (response) => {
      const type = response.headers.get("Content-Type")?.split(";")[0]?.trim().toLowerCase();
      if (type !== "application/json" && !type?.endsWith("+json"))
        throw new ApiError(502, "INVALID_RESPONSE");
      let value: unknown;
      try {
        value = await response.json();
      } catch {
        throw new ApiError(502, "INVALID_RESPONSE");
      }
      if (!validate(value)) throw new ApiError(502, "INVALID_RESPONSE");
      return value;
    });
  }

  download(path: string, signal?: AbortSignal): Promise<Blob> {
    return this.execute(path, signal, {}, "application/octet-stream", async (response) => {
      const type = response.headers.get("Content-Type")?.split(";")[0]?.trim().toLowerCase();
      if (!type || !binaryTypes.has(type)) throw new ApiError(502, "INVALID_RESPONSE");
      const length = response.headers.get("Content-Length");
      if (length && Number(length) > 25 * 1024 * 1024)
        throw new ApiError(413, "ATTACHMENT_TOO_LARGE");
      const blob = await response.blob();
      if (blob.size > 25 * 1024 * 1024) throw new ApiError(413, "ATTACHMENT_TOO_LARGE");
      return blob;
    });
  }

  forecastExport(path: string, file: File, signal?: AbortSignal): Promise<ForecastFile> {
    const body = new FormData();
    body.append("file", file);
    return this.execute(
      path,
      signal,
      { method: "POST", body },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      async (response) => {
        if (
          response.headers.get("Content-Type")?.split(";")[0] !==
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
          throw new ApiError(502, "INVALID_RESPONSE");
        const filename = /filename="([^"]+)"/.exec(
          response.headers.get("Content-Disposition") ?? "",
        )?.[1];
        const reconciled = response.headers.get("X-Forecast-Reconciled");
        const generatedAt = response.headers.get("X-Forecast-Generated-At");
        if (
          !filename ||
          !/^forecast-[a-z0-9][a-z0-9_-]{0,79}-\d{4}-\d{2}-\d{8}-\d{4}\.xlsx$/.test(filename) ||
          !["true", "false"].includes(reconciled ?? "") ||
          !generatedAt ||
          !Number.isFinite(Date.parse(generatedAt))
        )
          throw new ApiError(502, "INVALID_RESPONSE");
        let warnings: unknown;
        try {
          warnings = JSON.parse(
            decodeURIComponent(
              (response.headers.get("X-Forecast-Warnings") ?? "%5B%5D").replace(/\+/g, "%20"),
            ),
          );
        } catch {
          throw new ApiError(502, "INVALID_RESPONSE");
        }
        if (
          !Array.isArray(warnings) ||
          !warnings.every((w: unknown) => typeof w === "string" && w.length < 2000)
        )
          throw new ApiError(502, "INVALID_RESPONSE");
        const blob = await response.blob();
        if (blob.size > 25 * 1024 * 1024) throw new ApiError(502, "INVALID_RESPONSE");
        return { blob, filename, reconciled: reconciled === "true", warnings, generatedAt };
      },
    );
  }

  /** For commands that succeed with 204 No Content; any other success response is rejected. */
  noContent(path: string, signal?: AbortSignal, options: JsonOptions = {}): Promise<void> {
    return this.execute(path, signal, options, "application/json", async (response) => {
      if (response.status !== 204) throw new ApiError(502, "INVALID_RESPONSE");
      await response.body?.cancel();
    });
  }

  private async execute<T>(
    path: string,
    signal: AbortSignal | undefined,
    options: JsonOptions,
    accept: string,
    consume: (response: Response) => Promise<T>,
  ): Promise<T> {
    if (!path.startsWith("/api/v1/") || path.split("?")[0]?.includes("//"))
      throw new ApiError(400, "INVALID_REQUEST");
    const controller = new AbortController();
    const cancel = () => controller.abort();
    signal?.addEventListener("abort", cancel, { once: true });
    if (signal?.aborted) cancel();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, options.timeoutMs ?? 90000);
    try {
      controller.signal.throwIfAborted();
      const token = await abortable(this.auth.getAccessToken(), controller.signal);
      controller.signal.throwIfAborted();
      if (!token) throw new ApiError(401, "SESSION_ENDED");
      const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: accept };
      const body = options.body;
      const multipart = body instanceof FormData;
      if (body !== undefined && !multipart) headers["Content-Type"] = "application/json";
      // Native Window.fetch rejects an arbitrary object as its receiver.
      const fetcher = this.fetcher;
      const response = await fetcher(`${this.baseUrl}${path}`, {
        method: options.method ?? "GET",
        headers,
        credentials: "omit",
        cache: "no-store",
        signal: controller.signal,
        ...(body !== undefined ? { body: multipart ? body : JSON.stringify(body) } : {}),
      });
      controller.signal.throwIfAborted();
      if (!response.ok) throw await problem(response);
      const result = await consume(response);
      controller.signal.throwIfAborted();
      return result;
    } catch (error) {
      if (signal?.aborted) throw new DOMException("Request cancelled", "AbortError");
      if (timedOut) throw new ApiError(0, "REQUEST_TIMEOUT");
      throw error instanceof ApiError ? error : new ApiError(0, "NETWORK_ERROR");
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener("abort", cancel);
    }
  }
}

function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const cancel = () => {
      reject(new DOMException("Request cancelled", "AbortError"));
      signal.removeEventListener("abort", cancel);
    };
    signal.addEventListener("abort", cancel, { once: true });
    if (signal.aborted) cancel();
    promise.then(
      (value) => {
        signal.removeEventListener("abort", cancel);
        resolve(value);
      },
      (error) => {
        signal.removeEventListener("abort", cancel);
        reject(error);
      },
    );
  });
}

async function problem(response: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  const values = isRecord(body) ? body : {};
  const knownCode =
    typeof values.code === "string" && Object.hasOwn(messages, values.code)
      ? (values.code as Code)
      : undefined;
  const code: Code =
    response.status === 401
      ? "SESSION_ENDED"
      : response.status === 403
        ? "ACCESS_DENIED"
        : (knownCode ??
          (response.status === 400
            ? "INVALID_REQUEST"
            : response.status === 404
              ? "RECORD_UNAVAILABLE"
              : response.status === 409
                ? "RECORD_CHANGED"
                : response.status === 413
                  ? "ATTACHMENT_TOO_LARGE"
                  : response.status === 429
                    ? "REQUEST_RATE_LIMITED"
                    : "REQUEST_FAILED"));
  const upstream =
    typeof values.upstreamStatus === "number" &&
    Number.isInteger(values.upstreamStatus) &&
    values.upstreamStatus >= 0 &&
    values.upstreamStatus <= 599
      ? values.upstreamStatus
      : undefined;
  const retry =
    typeof values.retryAfterSeconds === "number"
      ? values.retryAfterSeconds
      : Number(response.headers.get("Retry-After") ?? NaN);
  return new ApiError(
    response.status,
    code,
    upstream,
    Number.isInteger(retry) && retry >= 0 && retry <= 2147483647 ? retry : undefined,
    Array.isArray(values.problems)
      ? values.problems
          .filter(
            (p: unknown): p is TemplateProblem =>
              isRecord(p) &&
              typeof p.sheet === "string" &&
              typeof p.cell === "string" &&
              typeof p.expected === "string",
          )
          .slice(0, 200)
      : [],
    isRecord(values.fieldErrors)
      ? Object.fromEntries(
          Object.entries(values.fieldErrors).filter(
            (entry): entry is [string, string] => typeof entry[1] === "string",
          ),
        )
      : {},
  );
}
