import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { Workspace } from "../workspace/Workspace";
import { JobsList } from "./JobsList";

const row = (entityId: string) => ({
  id: "00000000-0000-4000-8000-000000000010",
  entityId,
  entityName: "First entity",
  xeroQuoteId: "00000000-0000-4000-8000-000000000011",
  quoteNumber: "Q-100",
  customerName: "Example customer",
  xeroQuoteStatus: "SENT",
  quoteTotal: "123.45",
  currency: "NZD",
  stage: "QUOTED",
  stageEnteredAt: "2026-11-19T00:00:00Z",
  daysInStage: 0,
  late: false,
  nextAction: "FOLLOW_UP",
  flags: ["JOB_ID_MISSING"],
  attention: [],
  version: 0,
});
const page = (entityId: string) => ({
  items: [row(entityId)],
  page: 1,
  pageSize: 25,
  total: 1,
  totalPages: 1,
});
const detail = (entityId: string, version = 0) => ({
  summary: { ...row(entityId), version },
  notes: "Original note",
  vwork: { reportStatus: "NOT_APPLICABLE", searchState: "NOT_CHECKED" },
  invoice: {},
  links: {},
  events: [{ id: 1, at: "2026-11-19T00:00:00Z", source: "SYSTEM", eventType: "ITEM_CREATED" }],
  operations: [],
  allowedActions: ["EDIT_NOTES", "CLOSE_LOST"],
});

describe("GL-01 and GL-03 Jobs list", () => {
  it("filters quotes using Xero status and follow-up due date", async () => {
    const { api, fetcher } = testApi(() => page(entities[0]!.id));
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    await screen.findAllByText("Q-100");
    fireEvent.change(screen.getByLabelText("Xero status"), { target: { value: "SENT" } });
    fireEvent.change(screen.getByLabelText("Follow-up due by"), {
      target: { value: "2026-12-01" },
    });
    await waitFor(() => {
      const url = new URL(String(fetcher.mock.calls.at(-1)?.[0]), "https://api.example.invalid");
      expect(url.searchParams.get("xeroStatus")).toBe("SENT");
      expect(url.searchParams.get("followUpDueBy")).toBe("2026-12-01");
    });
  });

  it("saves an inline follow-up without overwriting other fields", async () => {
    const { api, fetcher } = testApi((url) =>
      url.pathname.endsWith("/work-items") ? page(entities[0]!.id) : detail(entities[0]!.id, 1),
    );
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    const field = (await screen.findAllByLabelText("Follow-up date for Q-100"))[0]!;
    fireEvent.change(field, { target: { value: "2026-12-01" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]!);
    await waitFor(() =>
      expect(fetcher.mock.calls.some(([, init]) => init?.method === "PATCH")).toBe(true),
    );
    const write = fetcher.mock.calls.find(([, init]) => init?.method === "PATCH");
    expect(JSON.parse(String(write?.[1]?.body))).toEqual({
      version: 0,
      followUpDate: "2026-12-01",
    });
  });

  it("clears an existing follow-up without changing notes", async () => {
    const existing = { ...row(entities[0]!.id), followUpDate: "2026-12-01" };
    const { api, fetcher } = testApi((url) =>
      url.pathname.endsWith("/work-items")
        ? { ...page(entities[0]!.id), items: [existing] }
        : detail(entities[0]!.id, 1),
    );
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    const field = (await screen.findAllByLabelText("Follow-up date for Q-100"))[0]!;
    fireEvent.change(field, { target: { value: "" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]!);
    await waitFor(() =>
      expect(fetcher.mock.calls.some(([, init]) => init?.method === "PATCH")).toBe(true),
    );
    const write = fetcher.mock.calls.find(([, init]) => init?.method === "PATCH");
    expect(JSON.parse(String(write?.[1]?.body))).toEqual({ version: 0, clearFollowUp: true });
  });

  it("GL-17 reviews the entity and quote before explicitly creating a test invoice", async () => {
    const entity = entities[0]!;
    let created = false;
    const operationId = "00000000-0000-4000-8000-000000000099";
    const { api, fetcher } = testApi((url) => {
      if (url.pathname.endsWith("/invoice-draft"))
        return {
          entityId: entity.id,
          itemId: row(entity.id).id,
          version: 1,
          quoteNumber: "Q-100",
          organisationName: "Example Xero",
          customer: "Example customer",
          reference: "J-1 / Q-100",
          lineCount: 2,
          total: "123.45",
          currency: "NZD",
          fingerprint: "a".repeat(64),
        };
      if (url.pathname.endsWith("/invoice")) {
        created = true;
        return { operationId, outcome: "CREATED" };
      }
      if (url.pathname.endsWith("/work-items")) return page(entity.id);
      return {
        ...detail(entity.id, created ? 2 : 1),
        summary: {
          ...row(entity.id),
          stage: created ? "INVOICE_DRAFTED" : "TO_INVOICE",
          version: created ? 2 : 1,
        },
        invoice: created
          ? {
              invoiceId: "00000000-0000-4000-8000-000000000098",
              invoiceNumber: "MOCK-000001",
              status: "DRAFT",
            }
          : {},
        allowedActions: created ? [] : ["PREPARE_INVOICE"],
      };
    });
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Prepare invoice" }));
    expect(await screen.findByText("Example Xero")).toBeInTheDocument();
    expect(screen.getByText("J-1 / Q-100")).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => String(url).endsWith("/invoice"))).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Create test invoice" }));
    expect(await screen.findByText("Test invoice created.")).toBeInTheDocument();
    expect(await screen.findByText("MOCK-000001")).toBeInTheDocument();
  });
  it("GL-21 confirms an authorised invoice was sent before updating the stage", async () => {
    const entity = entities[0]!;
    const { api, fetcher } = testApi((url) => {
      if (url.pathname.endsWith("/work-items")) return page(entity.id);
      const sent = url.pathname.endsWith("/mark-sent");
      return {
        ...detail(entity.id, sent ? 2 : 1),
        summary: {
          ...row(entity.id),
          stage: sent ? "INVOICED" : "INVOICE_DRAFTED",
          version: sent ? 2 : 1,
        },
        invoice: {
          invoiceId: "00000000-0000-4000-8000-000000000098",
          invoiceNumber: "INV-1",
          status: "AUTHORISED",
        },
        allowedActions: sent ? [] : ["MARK_SENT"],
      };
    });
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Mark invoice as sent" }));
    expect(fetcher.mock.calls.some(([url]) => String(url).endsWith("/mark-sent"))).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Confirm sent" }));
    expect(await screen.findByText("Invoice marked as sent.")).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => String(url).endsWith("/mark-sent"))).toBe(true);
  });
  it("GL-19 explains when multiple Xero invoices need review", async () => {
    const entity = entities[0]!;
    const { api } = testApi((url) =>
      url.pathname.endsWith("/work-items")
        ? page(entity.id)
        : {
            ...detail(entity.id),
            summary: { ...row(entity.id), attention: ["INVOICE_MULTIPLE_MATCHES"] },
          },
    );
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    expect(await screen.findByText(/Multiple Xero invoices match this job/)).toBeInTheDocument();
  });
  it("GL-21 shows the linked invoice and remaining balance", async () => {
    const entity = entities[0]!;
    const { api } = testApi((url) =>
      url.pathname.endsWith("/work-items")
        ? page(entity.id)
        : {
            ...detail(entity.id),
            invoice: {
              invoiceId: "00000000-0000-4000-8000-000000000098",
              invoiceNumber: "INV-1",
              status: "AUTHORISED",
              total: "100.00",
              amountDue: "40.00",
              dueDate: "2026-12-01",
            },
          },
    );
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    expect(await screen.findByText("INV-1")).toBeInTheDocument();
    expect(screen.getByText(/40\.00/)).toBeInTheDocument();
  });
  it("GL-22 retries only after lookup confirms the job was not created", async () => {
    let state: "unknown" | "failed" | "created" = "unknown";
    const entity = entities[0]!;
    const operationId = "00000000-0000-4000-8000-000000000099";
    const { api, fetcher } = testApi((url) => {
      if (url.pathname.endsWith("/lookup")) {
        state = "failed";
        return { outcome: "NOT_FOUND" };
      }
      if (url.pathname.endsWith("/job-draft"))
        return {
          entityId: entity.id,
          itemId: row(entity.id).id,
          version: 2,
          reference: "J-1",
          quoteNumber: "Q-100",
          customer: "Example customer",
          contactPerson: "Alex",
          phone: "555123",
          scope: [{ description: "Install", quantity: "1" }],
          templates: [{ templateRef: "template-a", isDefault: true }],
          defaultTemplate: "template-a",
          missingFields: ["siteAddress"],
        };
      if (url.pathname.endsWith("/retry")) {
        state = "created";
        return { operationId, outcome: "CREATED" };
      }
      if (url.pathname.endsWith("/work-items")) return page(entity.id);
      return {
        ...detail(entity.id, state === "unknown" ? 1 : state === "failed" ? 2 : 3),
        operations:
          state === "created"
            ? []
            : [
                {
                  id: operationId,
                  kind: "VWORK_CREATE_JOB",
                  status: state === "unknown" ? "UNKNOWN" : "FAILED",
                  requestedAt: "2026-11-19T00:00:00Z",
                  allowLookup: state === "unknown",
                  allowRetry: state === "failed",
                },
              ],
        allowedActions: [],
      };
    });
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    expect(screen.queryByRole("button", { name: "Retry vWork job" })).not.toBeInTheDocument();
    fireEvent.click(await screen.findByRole("button", { name: "Look up vWork job" }));
    fireEvent.click(await screen.findByRole("button", { name: "Retry vWork job" }));
    fireEvent.change(await screen.findByLabelText("Site address"), {
      target: { value: "Example Street" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Retry job" }));
    expect(await screen.findByText("vWork job linked.")).toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(([url]) => String(url).includes(`/operations/${operationId}/retry`)),
    ).toBe(true);
  });
  it("GL-22 looks up an unconfirmed job and reloads the detail", async () => {
    let lookedUp = false;
    const entity = entities[0]!;
    const operationId = "00000000-0000-4000-8000-000000000099";
    const { api, fetcher } = testApi((url) => {
      if (url.pathname.endsWith("/lookup")) {
        lookedUp = true;
        return { outcome: "LINKED_EXISTING" };
      }
      if (url.pathname.endsWith("/work-items")) return page(entity.id);
      return {
        ...detail(entity.id, lookedUp ? 2 : 1),
        operations: lookedUp
          ? []
          : [
              {
                id: operationId,
                kind: "VWORK_CREATE_JOB",
                status: "UNKNOWN",
                requestedAt: "2026-11-19T00:00:00Z",
                allowLookup: true,
                allowRetry: false,
              },
            ],
        allowedActions: [],
      };
    });
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Look up vWork job" }));
    expect(await screen.findByText("vWork job linked.")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Look up vWork job" })).not.toBeInTheDocument(),
    );
    expect(
      fetcher.mock.calls.some(([url]) => String(url).includes(`/operations/${operationId}/lookup`)),
    ).toBe(true);
  });
  it("GL-13 opens the create form and refreshes the detail after mock creation", async () => {
    let created = false;
    const entity = entities[0]!;
    const { api } = testApi((url) => {
      if (url.pathname.endsWith("/job-draft"))
        return {
          entityId: entity.id,
          itemId: row(entity.id).id,
          version: 0,
          reference: "J-1",
          quoteNumber: "Q-100",
          customer: "Example customer",
          contactPerson: "Alex",
          phone: "555123",
          scope: [{ description: "Install", quantity: "1" }],
          templates: [{ templateRef: "template-a", isDefault: true }],
          defaultTemplate: "template-a",
          missingFields: ["siteAddress"],
        };
      if (url.pathname.endsWith("/vwork-job")) {
        created = true;
        return { operationId: "00000000-0000-4000-8000-000000000099", outcome: "CREATED" };
      }
      if (url.pathname.endsWith("/work-items")) return page(entity.id);
      return {
        ...detail(entity.id, created ? 1 : 0),
        summary: {
          ...row(entity.id),
          stage: "TO_SCHEDULE",
          xeroQuoteStatus: "ACCEPTED",
          version: created ? 1 : 0,
        },
        vwork: created ? { jobId: "mock-1" } : {},
        allowedActions: created ? [] : ["CREATE_JOB"],
      };
    });
    render(<JobsList api={api} entity={entity} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Create vWork job" }));
    fireEvent.change(await screen.findByLabelText("Site address"), {
      target: { value: "Example Street" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create job" }));
    expect(await screen.findByText("vWork job linked.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create vWork job" })).not.toBeInTheDocument();
  });
  it("shows quote data and flags and sends entity-scoped filters", async () => {
    const { api, fetcher } = testApi((url) => page(url.searchParams.get("entityId")!));
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    expect(await screen.findAllByText("Q-100")).not.toHaveLength(0);
    expect(screen.getAllByText("Needs a Job ID")).not.toHaveLength(0);
    fireEvent.change(screen.getByLabelText("Search jobs"), { target: { value: "Acme" } });
    await waitFor(() => expect(String(fetcher.mock.calls.at(-1)?.[0])).toContain("search=Acme"));
    const url = new URL(String(fetcher.mock.calls.at(-1)?.[0]), "https://api.example.invalid");
    expect(url.searchParams.get("entityId")).toBe(entities[0]!.id);
    expect(url.searchParams.get("page")).toBe("1");
  });

  it("rejects records from another entity and offers retry", async () => {
    const { api } = testApi(() => page(entities[1]!.id));
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry jobs" })).toBeInTheDocument();
  });

  it("clears the prior entity immediately and ignores a late response", async () => {
    let resolveA: ((value: unknown) => void) | undefined;
    const { api } = testApi((url) =>
      url.searchParams.get("entityId") === entities[0]!.id
        ? new Promise((resolve) => {
            resolveA = resolve;
          })
        : {
            ...page(entities[1]!.id),
            items: [
              { ...row(entities[1]!.id), quoteNumber: "Q-B", entityName: entities[1]!.displayName },
            ],
          },
    );
    const view = render(
      <JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />,
    );
    await waitFor(() => expect(resolveA).toBeDefined());
    view.rerender(
      <JobsList api={api} entity={entities[1]!} profile={profile} onAccessDenied={vi.fn()} />,
    );
    expect(await screen.findAllByText("Q-B")).not.toHaveLength(0);
    resolveA?.(page(entities[0]!.id));
    await waitFor(() => expect(screen.queryByText("Q-100")).not.toBeInTheDocument());
  });

  it("opens service jobs from workspace navigation for the selected entity", async () => {
    const { api } = testApi((url) => {
      if (url.pathname.endsWith("/xero")) return { entityId: entities[0]!.id, status: "CONNECTED" };
      if (url.pathname.endsWith("/invoices"))
        return { items: [], page: 1, pageSize: 25, pageCount: 0, itemCount: 0 };
      if (url.pathname.endsWith("/work-items")) return page(entities[0]!.id);
      return {};
    });
    render(
      <Workspace
        api={api}
        profile={profile}
        entities={[entities[0]!]}
        onSignOut={vi.fn()}
        onAccessDenied={vi.fn()}
        onEntitiesChanged={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Service jobs" }));
    expect(await screen.findByRole("heading", { name: "Service jobs" })).toBeInTheDocument();
    expect(await screen.findAllByText("Q-100")).not.toHaveLength(0);
  });

  it("GL-04 opens detail and saves notes using the observed version", async () => {
    const { api, fetcher } = testApi((url, init) => {
      if (url.pathname.endsWith("/work-items")) return page(entities[0]!.id);
      return detail(entities[0]!.id, init?.method === "PATCH" ? 1 : 0);
    });
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    const notes = await screen.findByLabelText("Notes");
    fireEvent.change(notes, { target: { value: "Updated note" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() =>
      expect(fetcher.mock.calls.some(([, init]) => init?.method === "PATCH")).toBe(true),
    );
    const write = fetcher.mock.calls.find(([, init]) => init?.method === "PATCH");
    expect(JSON.parse(String(write?.[1]?.body))).toMatchObject({
      version: 0,
      notes: "Updated note",
    });
  });

  it("GL-05 requires a reason and allows Escape to return focus", async () => {
    const { api } = testApi((url) =>
      url.pathname.endsWith("/work-items") ? page(entities[0]!.id) : detail(entities[0]!.id),
    );
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    const close = await screen.findByRole("button", { name: "Close as lost" });
    close.focus();
    fireEvent.click(close);
    const dialog = screen.getByRole("dialog", { name: "Close as lost" });
    expect(dialog).toHaveTextContent("First entity");
    fireEvent.click(screen.getByRole("button", { name: "Confirm closure" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Enter a reason");
    fireEvent.keyDown(dialog, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(close).toHaveFocus();
  });

  it("GL-04 refreshes on a version conflict while preserving the draft", async () => {
    let reads = 0;
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/work-items")) return page(entities[0]!.id);
      if (init?.method === "PATCH")
        return new Response(JSON.stringify({ code: "WORK_ITEM_CHANGED" }), { status: 409 });
      return detail(entities[0]!.id, reads++ ? 1 : 0);
    });
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.change(await screen.findByLabelText("Notes"), {
      target: { value: "Keep this draft" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("This job changed");
    await waitFor(() => expect(reads).toBeGreaterThan(1));
    expect(screen.getByLabelText("Notes")).toHaveValue("Keep this draft");
  });

  it("GL-06 rejects a detail from another entity", async () => {
    const { api } = testApi((url) =>
      url.pathname.endsWith("/work-items") ? page(entities[0]!.id) : detail(entities[1]!.id),
    );
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByLabelText("Notes")).not.toBeInTheDocument();
  });

  it("GL-05 sends a confirmed reason and version once", async () => {
    const { api, fetcher } = testApi((url, init) => {
      if (url.pathname.endsWith("/work-items")) return page(entities[0]!.id);
      if (init?.method === "POST")
        return {
          ...detail(entities[0]!.id, 1),
          summary: { ...row(entities[0]!.id), stage: "CLOSED", version: 1 },
          allowedActions: [],
        };
      return detail(entities[0]!.id);
    });
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Close as lost" }));
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Customer declined" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirm closure" }));
    await screen.findByText("Job closed.");
    const writes = fetcher.mock.calls.filter(([, init]) => init?.method === "POST");
    expect(writes).toHaveLength(1);
    expect(JSON.parse(String(writes[0]?.[1]?.body))).toEqual({
      version: 0,
      reason: "Customer declined",
    });
  });

  it("keeps closure failures visible inside the confirmation dialog", async () => {
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/work-items")) return page(entities[0]!.id);
      if (init?.method === "POST") return new Response("{}", { status: 500 });
      return detail(entities[0]!.id);
    });
    render(<JobsList api={api} entity={entities[0]!} profile={profile} onAccessDenied={vi.fn()} />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Q-100" }))[0]!);
    fireEvent.click(await screen.findByRole("button", { name: "Close as lost" }));
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Declined" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirm closure" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("could not be completed");
    expect(screen.getByRole("dialog")).toContainElement(screen.getByRole("alert"));
  });
});
