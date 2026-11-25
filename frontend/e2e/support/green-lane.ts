import { expect, type Page } from "@playwright/test";
import {
  ENTITY_A,
  ENTITY_B,
  INVOICE_A,
  STAFF_A,
  entities,
  invoice,
  profile,
} from "../../src/test/fixtures";
import type { Role, Staff } from "../../src/api/contracts";

export { ENTITY_A, ENTITY_B, INVOICE_A, STAFF_A };
export const ATTEMPT = "00000000-0000-4000-8000-000000000090";
export const CONNECTION = "00000000-0000-4000-8000-000000000091";
export const JOB_A = "00000000-0000-4000-8000-000000000110";
export const JOB_B = "00000000-0000-4000-8000-000000000111";
/** Synthetic boundaries; the production UI and Supabase browser adapter run unchanged. */
export async function greenLane(page: Page, role: Role = "DIRECTOR") {
  const state = {
    role,
    otpBody: {} as Record<string, unknown>,
    entities: [...entities],
    users: [
      {
        id: STAFF_A,
        email: "alex@example.invalid",
        displayName: "Alex Example",
        role: "DIRECTOR",
        active: true,
      },
    ] as Staff[],
    owners: new Map<string, string>(),
    requests: [] as { path: string; method: string; authorized: boolean; query: string }[],
    confirmed: false,
    failInvoices: false,
    invoiceCount: 1,
  };
  const token = `${btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }))}.${btoa(JSON.stringify({ sub: STAFF_A, exp: Math.floor(Date.now() / 1000) + 3600, role: "authenticated", aud: "authenticated" }))}.synthetic-signature`;
  await page.route("https://synthetic-auth.example.invalid/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/otp")) state.otpBody = route.request().postDataJSON();
    const user = {
      id: STAFF_A,
      aud: "authenticated",
      role: "authenticated",
      email: "alex@example.invalid",
      app_metadata: {},
      user_metadata: {},
      created_at: "2026-11-01T00:00:00Z",
    };
    await route.fulfill({
      json: path.endsWith("/verify")
        ? {
            access_token: token,
            refresh_token: "synthetic-refresh",
            token_type: "bearer",
            expires_in: 3600,
            user,
          }
        : path.endsWith("/user")
          ? user
          : {},
    });
  });
  await page.route("https://synthetic-api.example.invalid/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname,
      method = request.method();
    state.requests.push({
      path,
      method,
      query: url.search,
      authorized: request.headers().authorization === `Bearer ${token}`,
    });
    const body = () => request.postDataJSON() as Record<string, unknown>;
    if (method === "OPTIONS") {
      await route.fulfill({
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "authorization,content-type",
          "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,OPTIONS",
        },
      });
      return;
    }
    let result: unknown;
    if (path.endsWith("/me")) result = { ...profile, role: state.role };
    else if (path === "/api/v1/entities") {
      if (method === "POST")
        state.entities.push({
          id: "00000000-0000-4000-8000-000000000003",
          displayName: String(body().displayName),
        });
      result = method === "POST" ? state.entities.at(-1) : state.entities;
    } else if (path.endsWith("/staff"))
      result = state.users
        .filter((u) => u.active)
        .map(({ id, displayName }) => ({ id, displayName }));
    else if (path.includes("/users")) {
      if (method === "POST") state.users.push({ ...body(), id: ENTITY_B, active: true } as Staff);
      if (method === "PATCH")
        state.users = state.users.map((u) =>
          u.id === path.split("/").at(-1) ? ({ ...u, ...body() } as Staff) : u,
        );
      result =
        method === "GET"
          ? state.users
          : method === "POST"
            ? state.users.at(-1)
            : state.users.find((u) => u.id === path.split("/").at(-1));
    } else if (path.includes("/attempts/")) {
      if (method === "PUT") state.confirmed = true;
      // The attempt belongs to the entity most recently added in this session, as the real flow does.
      const attemptEntity = state.entities.at(-1)?.id ?? ENTITY_A;
      result =
        method === "PUT"
          ? {
              entityId: attemptEntity,
              status: "CONNECTED",
              organisationName: "Synthetic Xero organisation",
            }
          : {
              id: ATTEMPT,
              entityId: attemptEntity,
              state: state.confirmed ? "COMPLETED" : "READY",
              choices: [{ connectionId: CONNECTION, name: "Synthetic Xero organisation" }],
            };
    } else if (path.endsWith("/authorization")) {
      result = {
        attemptId: ATTEMPT,
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        authorizationUrl: `https://login.xero.com/identity/connect/authorize?response_type=code&scope=offline_access%20accounting.invoices.read%20accounting.attachments.read&state=${"s".repeat(43)}`,
      };
    } else if (path.endsWith("/xero"))
      result = {
        entityId: path.split("/")[4],
        status: "CONNECTED",
        organisationName: "Synthetic Xero organisation",
        lastConfirmedAt: "2026-11-08T00:00:00Z",
      };
    else if (path.endsWith("/green-line/sync"))
      result = { entityId: path.split("/")[4], xero: {}, vwork: {} };
    else if (path === "/api/v1/work-items/my-work" || path.includes("/work-items/")) {
      const job = (entityId: string) => ({
        id: entityId === ENTITY_A ? JOB_A : JOB_B,
        entityId,
        entityName: state.entities.find((item) => item.id === entityId)?.displayName ?? "Unknown",
        xeroQuoteId:
          entityId === ENTITY_A
            ? "00000000-0000-4000-8000-000000000120"
            : "00000000-0000-4000-8000-000000000121",
        quoteNumber: entityId === ENTITY_A ? "Q-A" : "Q-B",
        jobId: entityId === ENTITY_A ? "J-A" : "J-B",
        customerName: entityId === ENTITY_A ? "Customer A" : "Customer B",
        xeroQuoteStatus: "SENT",
        stage: "QUOTED",
        stageEnteredAt: "2026-11-19T00:00:00Z",
        daysInStage: 2,
        late: false,
        nextAction: "FOLLOW_UP",
        flags: [],
        attention: [],
        version: 0,
      });
      if (path.endsWith("/overview")) {
        const entity = url.searchParams.get("entityId");
        const rows = (entity ? [entity] : [ENTITY_A, ENTITY_B]).map((id) => ({
          summary: { ...job(id), stage: "JOB_COMPLETED", quoteTotal: "100.00", currency: "NZD" },
          technician: "Taylor Technician",
        }));
        const sum = `${rows.length * 100}.00`;
        result = {
          generatedAt: "2026-11-25T00:00:00Z",
          from: url.searchParams.get("from"),
          to: url.searchParams.get("to"),
          stages: [
            {
              stage: "JOB_COMPLETED",
              count: rows.length,
              totals: [{ currency: "NZD", amount: sum }],
            },
          ],
          closed: [],
          openItems: rows,
          openTruncated: false,
          uninvoiced: rows,
          uninvoicedTruncated: false,
          unsent: [],
          unsentTruncated: false,
          cycleTimes: [
            {
              step: "COMPLETED_TO_SENT",
              medianDays: 2,
              averageDays: 2.5,
              sampleSize: 2,
              excludedBackfilled: 0,
              excludedNoStart: 0,
              excludedAlreadyComplete: 0,
              excludedOutOfOrder: 0,
            },
          ],
          completedTotal: rows.length,
          completedAlreadyWhenFirstSeen: 0,
          completedByOwner: [{ name: "Unattributed", count: rows.length }],
          completedByTechnician: [{ name: "Taylor Technician", count: rows.length }],
          invoicesSent: {
            count: 0,
            totals: [],
            items: [],
            truncated: false,
            paidNotSeenSent: 0,
          },
        };
      } else if (path.endsWith("/my-work")) {
        const entity = url.searchParams.get("entityId");
        result = {
          needsAction: (entity ? [entity] : [ENTITY_A, ENTITY_B]).map(job),
          waiting: [],
          needsAttention: [],
          generatedAt: "2026-11-19T00:00:00Z",
          truncated: false,
        };
      } else {
        const entity = path.split("/")[4]!;
        result = {
          summary: job(entity),
          notes: "Synthetic note",
          vwork: {},
          invoice: {},
          links: {},
          events: [],
          operations: [],
          allowedActions: ["EDIT_NOTES"],
        };
      }
    } else if (path.includes("/invoices")) {
      if (state.role === "STANDARD_USER" && url.searchParams.get("type") === "ACCPAY") {
        await route.fulfill({
          status: 403,
          headers: { "Access-Control-Allow-Origin": "*" },
          json: { code: "ACCESS_DENIED" },
        });
        return;
      }
      if (state.failInvoices) {
        await route.fulfill({
          status: 503,
          headers: { "Access-Control-Allow-Origin": "*" },
          json: { code: "XERO_UNAVAILABLE" },
        });
        return;
      }
      const entity = path.split("/")[4]!,
        ownerId = state.owners.get(entity);
      const row = invoice({
        type: url.searchParams.get("type") === "ACCPAY" ? "ACCPAY" : "ACCREC",
        contact: entity === ENTITY_A ? "Acme Example" : "Second entity customer",
        ...(ownerId ? { owner: { id: ownerId, displayName: "Alex Example", active: true } } : {}),
      });
      if (path.endsWith("/assignment")) {
        const assignee = body().assigneeId;
        if (assignee) state.owners.set(entity, String(assignee));
        else state.owners.delete(entity);
        result = {
          invoiceId: INVOICE_A,
          ...(assignee
            ? { owner: { id: assignee, displayName: "Alex Example", active: true } }
            : {}),
        };
      } else if (!path.endsWith("/invoices"))
        result = {
          invoice: row,
          lines: [
            {
              description: "Service work",
              quantity: "1",
              unitAmount: "100.00",
              lineAmount: "100.00",
              taxAmount: "15.00",
            },
          ],
          payments: [{ date: "2026-11-03", amount: "10.00", reference: "Bank payment" }],
          subtotal: "100.00",
          tax: "15.00",
          amountPaid: "10.00",
          amountCredited: "0.00",
          taxBasis: "EXCLUSIVE",
        };
      else {
        const search = url.searchParams.get("search")?.toLowerCase() ?? "";
        const rows = Array.from({ length: state.invoiceCount }, (_, index) => ({
          ...row,
          id:
            index === 0
              ? row.id
              : `00000000-0000-4000-8000-${String(index + 100).padStart(12, "0")}`,
          number: `INV-${String(index + 1).padStart(3, "0")}`,
        })).filter((record) =>
          `${record.number} ${record.reference} ${record.contact}`.toLowerCase().includes(search),
        );
        const pageNumber = Number(url.searchParams.get("page") ?? 1);
        const pageSize = Number(url.searchParams.get("pageSize") ?? 25);
        result = {
          items: rows.slice((pageNumber - 1) * pageSize, pageNumber * pageSize),
          page: pageNumber,
          pageSize,
          pageCount: Math.ceil(rows.length / pageSize),
          itemCount: rows.length,
        };
      }
    } else {
      await route.fulfill({ status: 404, json: { code: "NOT_FOUND" } });
      return;
    }
    await route.fulfill({ headers: { "Access-Control-Allow-Origin": "*" }, json: result });
  });
  await page.route("https://login.xero.com/**", (route) =>
    route.fulfill({
      status: 302,
      headers: { location: `http://localhost:4173/?xeroAttempt=${ATTEMPT}` },
      body: "",
    }),
  );
  return state;
}
export async function signIn(page: Page, destination: "invoices" | "mywork" = "invoices") {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("alex@example.invalid");
  await page.getByRole("button", { name: "Send code", exact: true }).click();
  await page.getByLabel("Six-digit code", { exact: true }).fill("123456");
  await page.getByRole("button", { name: "Verify and sign in" }).click();
  await expect(page.getByRole("heading", { name: "My Work", exact: true })).toBeVisible();
  if (destination === "invoices") {
    await page.getByRole("button", { name: "Invoices", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Invoices", exact: true })).toBeVisible();
  }
}
