import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { Workspace } from "../workspace/Workspace";
import { DirectorOverview } from "./DirectorOverview";

const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const row = (n: number, entityId: string, quoteNumber: string, overrides: object = {}) => ({
  summary: {
    id: uuid(n),
    entityId,
    entityName: "First entity",
    xeroQuoteId: uuid(n + 500),
    quoteNumber,
    customerName: "Example customer",
    xeroQuoteStatus: "ACCEPTED",
    stage: "JOB_COMPLETED",
    stageEnteredAt: "2026-11-19T00:00:00Z",
    daysInStage: 6,
    late: false,
    flags: [],
    attention: [],
    quoteTotal: "100.0000",
    currency: "NZD",
    version: 0,
    ...overrides,
  },
});
const overview = (entityId: string, overrides: object = {}) => {
  const owned = row(1, entityId, "Q-100", {
    owner: { id: uuid(9), displayName: "Alex Example" },
    late: true,
  });
  const unowned = row(2, entityId, "Q-200", { stage: "QUOTED" });
  const sent = {
    ...row(3, entityId, "Q-300", { stage: "INVOICED" }),
    invoiceNumber: "INV-300",
    invoiceTotal: "115.0000",
    invoiceSentSeenAt: "2026-11-20T00:00:00Z",
  };
  return {
    generatedAt: "2026-11-25T00:00:00Z",
    from: "2026-10-27",
    to: "2026-11-25",
    stages: [
      { stage: "QUOTED", count: 1, totals: [{ currency: "NZD", amount: "100.0000" }] },
      { stage: "JOB_COMPLETED", count: 1, totals: [{ currency: "NZD", amount: "100.0000" }] },
    ],
    closed: [{ reason: "PAID", count: 2 }],
    openItems: [owned, unowned],
    openTruncated: false,
    uninvoiced: [owned],
    uninvoicedTruncated: false,
    unsent: [],
    unsentTruncated: false,
    cycleTimes: [
      {
        step: "COMPLETED_TO_SENT",
        medianDays: 1.5,
        averageDays: 2,
        sampleSize: 4,
        excludedBackfilled: 1,
        excludedNoStart: 0,
        excludedAlreadyComplete: 0,
        excludedOutOfOrder: 2,
      },
      {
        step: "QUOTE_TO_ACCEPTED",
        sampleSize: 0,
        excludedBackfilled: 0,
        excludedNoStart: 0,
        excludedAlreadyComplete: 0,
        excludedOutOfOrder: 0,
      },
    ],
    completedTotal: 3,
    completedAlreadyWhenFirstSeen: 1,
    completedByOwner: [{ id: uuid(9), name: "Alex Example", count: 3 }],
    completedByTechnician: [{ name: "Taylor Technician", count: 3 }],
    invoicesSent: {
      count: 1,
      totals: [{ currency: "NZD", amount: "115.0000" }],
      items: [sent],
      truncated: false,
      paidNotSeenSent: 2,
    },
    ...overrides,
  };
};
const view = (api: ReturnType<typeof testApi>["api"], entity: (typeof entities)[number] | null) => (
  <DirectorOverview
    api={api}
    entity={entity}
    entities={entities}
    profile={profile}
    onAccessDenied={vi.fn()}
  />
);
const section = (name: string) => within(screen.getByRole("region", { name }));

describe("GL-24 Director overview", () => {
  it("GL-DIR-01_05 shows who has what, stuck money, cycle times, completions and invoices", async () => {
    const { api } = testApi(() => overview(entities[0]!.id));
    render(view(api, entities[0]!));
    await screen.findByRole("region", { name: "Who has what" });
    const people = section("Who has what");
    expect(people.getByText("Alex Example: 1 open, 1 late")).toBeInTheDocument();
    expect(people.getByText("Unassigned: 1 open")).toBeInTheDocument();
    expect(section("Completed, not invoiced").getByText("Q-100")).toBeInTheDocument();
    expect(section("Invoices created, not sent").getByText(/Nothing is waiting/)).toBeVisible();
    const times = section("How long each step takes");
    expect(times.getByText("1.5 days")).toBeInTheDocument();
    expect(times.getByText("1 imported, 2 out of order")).toBeInTheDocument();
    expect(times.getByText("Not enough data")).toBeInTheDocument();
    expect(section("Jobs completed").getByText("Taylor Technician")).toBeInTheDocument();
    const invoices = section("Invoices sent");
    expect(invoices.getByText("INV-300")).toBeInTheDocument();
    expect(invoices.getByText(/2 were paid without being seen as sent/)).toBeInTheDocument();
    expect(section("Pipeline").getByText("Paid: 2")).toBeInTheDocument();
  });

  it("GL-DIR-06 refuses a response containing another organisation's row", async () => {
    const { api } = testApi(() => overview(entities[1]!.id));
    render(view(api, entities[0]!));
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
  });

  it("GL-DIR-06 removes the previous organisation's figures while the next one loads", async () => {
    const { api } = testApi((url) =>
      url.searchParams.get("entityId") === entities[0]!.id
        ? overview(entities[0]!.id)
        : new Promise(() => {}),
    );
    const screenView = render(view(api, entities[0]!));
    await screen.findByRole("region", { name: "Who has what" });
    screenView.rerender(view(api, entities[1]!));
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Loading the overview");
  });

  it("GL-DIR-03 requests the chosen period and blocks an impossible one", async () => {
    const { api, fetcher } = testApi(() => overview(entities[0]!.id));
    render(view(api, entities[0]!));
    await screen.findByRole("region", { name: "Who has what" });
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-11-10" } });
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-11-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply period" }));
    expect(screen.getByRole("alert")).toHaveTextContent("on or before");
    const calls = fetcher.mock.calls.length;
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-11-20" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply period" }));
    await waitFor(() => expect(fetcher.mock.calls.length).toBeGreaterThan(calls));
    expect(String(fetcher.mock.calls.at(-1)![0])).toContain("from=2026-11-10&to=2026-11-20");
  });

  it("GL-24 warns when a list is cut off and offers retry after a failure", async () => {
    let fail = true;
    const { api } = testApi(() =>
      fail
        ? new Response(JSON.stringify({ code: "XERO_TENANT_CHANGED" }), { status: 409 })
        : overview(entities[0]!.id, { openTruncated: true }),
    );
    render(view(api, entities[0]!));
    await screen.findByRole("alert");
    fail = false;
    fireEvent.click(screen.getByRole("button", { name: "Retry overview" }));
    expect(await screen.findByText(/Only the oldest 500 open items/)).toBeInTheDocument();
  });

  it.each(["FINANCIAL_ADMIN", "STANDARD_USER"] as const)(
    "GL-DIR-06 hides Overview navigation from %s",
    async (role) => {
      const { api } = testApi(() => new Promise(() => {}));
      render(
        <Workspace
          api={api}
          entities={[entities[0]!]}
          profile={{ ...profile, role }}
          onSignOut={vi.fn()}
          onAccessDenied={vi.fn()}
          onEntitiesChanged={vi.fn()}
        />,
      );
      expect(screen.queryByRole("button", { name: "Overview" })).not.toBeInTheDocument();
    },
  );

  it("GL-DIR-06 lets a Director open Overview across all entities", async () => {
    const { api, fetcher } = testApi((url) =>
      url.pathname.endsWith("/overview") ? overview(entities[0]!.id) : new Promise(() => {}),
    );
    render(
      <Workspace
        api={api}
        entities={entities}
        profile={{ ...profile, role: "DIRECTOR" }}
        onSignOut={vi.fn()}
        onAccessDenied={vi.fn()}
        onEntitiesChanged={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Overview" }));
    expect(await screen.findByRole("heading", { name: "Overview" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Entity"), { target: { value: "__all__" } });
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(
          ([url]) => String(url).includes("/overview?") && !String(url).includes("entityId"),
        ),
      ).toBe(true),
    );
  });
});
