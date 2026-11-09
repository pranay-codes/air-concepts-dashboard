import type { AuthService } from "../auth/authService";

const messages = {
  SESSION_ENDED: "Your session has ended. Sign in again.",
  ACCESS_DENIED: "Your access has changed. Check your permissions or contact a Director.",
  INVALID_REQUEST: "Check the entered values and try again.",
  INVALID_RESPONSE: "This section returned an incomplete response. Try again.",
  RECORD_UNAVAILABLE: "This record is no longer available. Refresh the list.",
  RECORD_CHANGED: "This record changed. Refresh its details before trying again.",
  RECORD_CONFLICT: "These details conflict with an existing record. Refresh and check them.",
  REQUEST_FAILED: "The request could not be completed. Try again.",
  REQUEST_TIMEOUT: "The request timed out. Try again.",
  NETWORK_ERROR: "Could not reach the dashboard. Check your connection and try again.",
  REQUEST_RATE_LIMITED: "Requests are limited. Wait before retrying.",
  XERO_NOT_CONNECTED:
    "This entity is not connected to Xero. Ask a Director to connect it in Settings.",
  XERO_RECONNECT_REQUIRED: "Xero access needs to be reconnected by a Director in Settings.",
  XERO_ORGANISATION_ACCESS_DENIED:
    "Xero refused access to this entity. Ask a Director to check its connection.",
  XERO_RATE_LIMITED: "Xero has limited requests. Wait before retrying.",
  XERO_PROVIDER_FAILED: "Xero could not confirm this request. Retry or check the connection.",
  XERO_UNAVAILABLE: "Xero is unavailable. Retry when the connection is restored.",
  XERO_INVALID_RESPONSE: "Xero returned incomplete data. Retry this request.",
  XERO_CONFIGURATION_ERROR: "The Xero setup needs attention from the setup owner.",
  XERO_CONNECTION_CHANGED: "This entity's connection changed. Refresh before trying again.",
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
  ) {
    super(messages[code]);
    this.retryAt =
      retryAfterSeconds === undefined ? undefined : Date.now() + retryAfterSeconds * 1000;
  }
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

type JsonOptions = { method?: "GET" | "POST" | "PUT" | "PATCH"; body?: unknown };
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
    }, 90000);
    try {
      controller.signal.throwIfAborted();
      const token = await abortable(this.auth.getAccessToken(), controller.signal);
      controller.signal.throwIfAborted();
      if (!token) throw new ApiError(401, "SESSION_ENDED");
      const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: accept };
      if (options.body !== undefined) headers["Content-Type"] = "application/json";
      // Native Window.fetch rejects an arbitrary object as its receiver.
      const fetcher = this.fetcher;
      const response = await fetcher(`${this.baseUrl}${path}`, {
        method: options.method ?? "GET",
        headers,
        credentials: "omit",
        cache: "no-store",
        signal: controller.signal,
        ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
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
  );
}
