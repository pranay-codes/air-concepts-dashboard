import { describe, expect, it, vi } from "vitest";
import type { AuthService } from "../auth/authService";
import { ApiClient } from "./client";
import { DashboardApi } from "./dashboard";

const entity = "00000000-0000-4000-8000-000000000001";
const invoice = "00000000-0000-4000-8000-000000000002";
function setup(body: unknown) {
  const auth: AuthService = {
    getAccessToken: vi.fn().mockResolvedValue("synthetic-access"),
    requestCode: vi.fn(),
    verifyCode: vi.fn(),
    signOut: vi.fn(),
    onSessionChange: () => () => {},
  };
  const fetcher = vi
    .fn<typeof fetch>()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }),
    );
  return { api: new DashboardApi(new ApiClient("", auth, fetcher)), fetcher };
}
function summary() {
  return {
    id: invoice,
    type: "ACCREC",
    number: "INV-1",
    contact: "Acme",
    date: "2026-11-01",
    status: "AUTHORISED",
    overdue: true,
    total: "9007199254740993.01",
  };
}

describe("UC5 generated-contract feature adapters", () => {
  it("FR-UC15-08_26 sends combined private criteria to a bounded API page without selecting a tenant", async () => {
    const { api, fetcher } = setup({
      items: [summary()],
      page: 2,
      pageSize: 1,
      pageCount: 2,
      itemCount: 2,
    });
    const page = await api.invoices(entity, {
      type: "ACCREC",
      search: "Acme & Sons",
      status: "AUTHORISED",
      overdue: true,
      from: "2026-01-01",
      to: "2026-11-07",
      sort: "CONTACT",
      direction: "ASC",
      page: 2,
      pageSize: 1,
    });
    const url = new URL(String(fetcher.mock.calls[0]?.[0]), "https://api.example.invalid");
    expect(url.pathname).toBe(`/api/v1/entities/${entity}/invoices`);
    expect(url.searchParams.get("search")).toBe("Acme & Sons");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("tenantId")).toBeNull();
    expect(page.items[0]?.total).toBe("9007199254740993.01");
  });

  it("FR-UC15-06_08 rejects rounded-number money, inconsistent counts and wrong document types", async () => {
    for (const body of [
      {
        items: [{ ...summary(), total: 10.01 }],
        page: 1,
        pageSize: 25,
        pageCount: 1,
        itemCount: 1,
      },
      { items: [summary()], page: 1, pageSize: 25, pageCount: 1, itemCount: 2 },
      {
        items: [{ ...summary(), type: "ACCPAY" }],
        page: 1,
        pageSize: 25,
        pageCount: 1,
        itemCount: 1,
      },
    ]) {
      await expect(setup(body).api.invoices(entity, { type: "ACCREC" })).rejects.toMatchObject({
        code: "INVALID_RESPONSE",
      });
    }
  });

  it("FR-UC15-32 rejects bill ownership metadata", async () => {
    const body = {
      items: [
        { ...summary(), type: "ACCPAY", owner: { id: entity, displayName: "Owner", active: true } },
      ],
      page: 1,
      pageSize: 25,
      pageCount: 1,
      itemCount: 1,
    };
    await expect(setup(body).api.invoices(entity, { type: "ACCPAY" })).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });

  it("FR-UC15-30 rejects malformed IDs before transport and mismatched detail identities", async () => {
    const { api, fetcher } = setup({});
    await expect(api.invoices("../another-entity", { type: "ACCREC" })).rejects.toMatchObject({
      code: "INVALID_REQUEST",
    });
    expect(fetcher).not.toHaveBeenCalled();
    await expect(
      setup({ invoice: { ...summary(), id: entity }, lines: [], payments: [] }).api.invoice(
        entity,
        "ACCREC",
        invoice,
      ),
    ).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });

  it("FR-UC15-02_30 accepts variable authorised entity counts and rejects duplicate IDs", async () => {
    const rows = Array.from({ length: 4 }, (_, index) => ({
      id: `00000000-0000-4000-8000-00000000000${index + 1}`,
      displayName: `Entity ${index + 1}`,
    }));
    expect(await setup(rows).api.entities()).toHaveLength(4);
    await expect(setup([rows[0], rows[0]]).api.entities()).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });

  it("FR-UC15-10 refuses unsafe authorisation links", async () => {
    const body = {
      attemptId: entity,
      authorizationUrl: "https://attacker.example.invalid/authorize",
      expiresAt: "2026-11-07T00:10:00Z",
    };
    await expect(setup(body).api.startConnection(entity)).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
  it("GL-T08 accepts the documented quote workflow permissions in Xero authorisation", async () => {
    const url = new URL("https://login.xero.com/identity/connect/authorize");
    url.searchParams.set("response_type", "code");
    url.searchParams.set("state", "a".repeat(43));
    url.searchParams.set(
      "scope",
      "offline_access accounting.invoices.read accounting.attachments.read accounting.contacts.read",
    );
    const body = {
      attemptId: entity,
      authorizationUrl: url.toString(),
      expiresAt: "2026-11-07T00:10:00Z",
    };
    await expect(setup(body).api.startConnection(entity)).resolves.toEqual(body);
  });
});
