import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { MyWork } from "./MyWork";
import { Workspace } from "../workspace/Workspace";

const id = "00000000-0000-4000-8000-000000000010";
const summary = (entityId: string, quoteNumber: string) => ({
  id,
  entityId,
  entityName: "First entity",
  xeroQuoteId: "00000000-0000-4000-8000-000000000011",
  quoteNumber,
  customerName: "Example",
  xeroQuoteStatus: "SENT",
  stage: "QUOTED",
  stageEnteredAt: "2026-11-19T00:00:00Z",
  daysInStage: 2,
  late: true,
  nextAction: "FOLLOW_UP",
  flags: ["LATE"],
  attention: [],
  version: 0,
});
const result = (entityId: string) => ({
  needsAction: [summary(entityId, "Q-100")],
  waiting: [],
  needsAttention: [],
  generatedAt: "2026-11-19T00:00:00Z",
  truncated: false,
});
const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const item = (n: number, entityId: string, quoteNumber: string, overrides: object = {}) => ({
  ...summary(entityId, quoteNumber),
  id: uuid(n),
  xeroQuoteId: uuid(n + 500),
  late: false,
  flags: [],
  attention: [],
  ...overrides,
});
const work = (overrides: object) => ({
  needsAction: [],
  waiting: [],
  needsAttention: [],
  generatedAt: "2026-11-19T00:00:00Z",
  truncated: false,
  ...overrides,
});
const view = (
  api: ReturnType<typeof testApi>["api"],
  entity: (typeof entities)[number] | null = entities[0]!,
  select = vi.fn(),
) => (
  <MyWork
    api={api}
    entity={entity}
    entities={entities}
    profile={profile}
    onDirty={vi.fn()}
    onAccessDenied={vi.fn()}
    onSelectEntity={select}
  />
);

describe("GL-10 My Work", () => {
  it.each(["DIRECTOR", "FINANCIAL_ADMIN", "STANDARD_USER"] as const)(
    "lands on My Work for %s",
    async (role) => {
      const { api } = testApi((url) =>
        url.pathname.endsWith("/xero")
          ? { entityId: entities[0]!.id, status: "CONNECTED" }
          : result(entities[0]!.id),
      );
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
      expect(await screen.findByRole("heading", { name: "My Work" })).toBeInTheDocument();
      expect(await screen.findByText("Q-100")).toBeInTheDocument();
    },
  );
  it("shows work categories, action and age and validates selected entity", async () => {
    const { api, fetcher } = testApi(() => result(entities[0]!.id));
    render(view(api));
    expect(await screen.findByRole("button", { name: "Needs your action 1" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    const table = screen.getByRole("region", { name: "My work items" });
    expect(within(table).getByText("Q-100")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Follow up Q-100" })).toBeInTheDocument();
    expect(screen.getByText("2 days in stage")).toBeInTheDocument();
    expect(within(table).getByText("Late")).toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(([url]) => String(url).includes(`entityId=${entities[0]!.id}`)),
    ).toBe(true);
  });
  it("refuses a row belonging to a different entity", async () => {
    const { api } = testApi(() => result(entities[1]!.id));
    render(view(api));
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
  });
  it("rejects an unrecognised entity even in the aggregate view", async () => {
    const unknown = "00000000-0000-4000-8000-000000000099";
    const { api } = testApi(() => result(unknown));
    render(view(api, null));
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
  });
  it("opens detail only after choosing its entity from an aggregate view", async () => {
    const select = vi.fn();
    const { api } = testApi((url) =>
      url.pathname.endsWith("my-work")
        ? result(entities[0]!.id)
        : {
            summary: summary(entities[0]!.id, "Q-100"),
            notes: "Call",
            vwork: {},
            invoice: {},
            links: {},
            events: [],
            operations: [],
            allowedActions: [],
          },
    );
    const rendered = render(view(api, null, select));
    fireEvent.click(await screen.findByRole("button", { name: "Follow up Q-100" }));
    expect(select).toHaveBeenCalledWith(entities[0]!.id);
    rendered.rerender(view(api, entities[0]!, select));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Q-100" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole("heading", { name: "Q-100" })).toHaveFocus());
    fireEvent.click(screen.getByRole("button", { name: "Back to My Work" }));
    expect(select).toHaveBeenCalledWith("__all__");
  });

  it("returns focus to the action after closing a selected-entity detail", async () => {
    const { api } = testApi((url) =>
      url.pathname.endsWith("my-work")
        ? result(entities[0]!.id)
        : {
            summary: summary(entities[0]!.id, "Q-100"),
            vwork: {},
            invoice: {},
            links: {},
            events: [],
            operations: [],
            allowedActions: [],
          },
    );
    render(view(api));
    fireEvent.click(await screen.findByRole("button", { name: "Follow up Q-100" }));
    fireEvent.click(await screen.findByRole("button", { name: "Back to My Work" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Follow up Q-100" })).toHaveFocus(),
    );
  });

  it("filters by work category and keeps the count of each queue", async () => {
    const e = entities[0]!.id;
    const { api } = testApi(() =>
      work({
        needsAction: [item(1, e, "Q-100", { late: false })],
        needsAttention: [
          item(2, e, "Q-200", { attention: ["NO_DEFAULT_OWNER"], nextAction: "RESOLVE" }),
        ],
        waiting: [item(3, e, "Q-300", { nextAction: "AWAIT_PAYMENT" })],
      }),
    );
    render(view(api));
    expect(await screen.findByRole("button", { name: "All work 3" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    fireEvent.click(screen.getByRole("button", { name: "Needs attention 1" }));
    expect(screen.getByRole("button", { name: "Needs attention 1" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByText("Q-200")).toBeInTheDocument();
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
    expect(screen.queryByText("Q-300")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Waiting 1" }));
    expect(screen.getByText("Q-300")).toBeInTheDocument();
    expect(screen.queryByText("Q-200")).not.toBeInTheDocument();
  });

  it("sorts by most overdue and filters by attention status", async () => {
    const e = entities[0]!.id;
    const { api } = testApi(() =>
      work({
        needsAction: [
          item(1, e, "Q-A", { late: true, daysInStage: 5, followUpDate: "2999-12-31" }),
          item(2, e, "Q-B", { late: false, daysInStage: 9, followUpDate: "2000-01-01" }),
          item(3, e, "Q-C", { late: true, daysInStage: 1 }),
        ],
      }),
    );
    render(view(api));
    await screen.findByRole("button", { name: "Needs your action 3" });
    fireEvent.change(screen.getByLabelText("Sort by"), { target: { value: "OVERDUE" } });
    const openRows = () =>
      screen.getAllByRole("button", { name: /^Open Q-/ }).map((button) => button.textContent);
    expect(openRows()).toEqual(["Q-A", "Q-C", "Q-B"]);
    expect(screen.getByText("Past due")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Attention status"), { target: { value: "NONE" } });
    expect(openRows()).toEqual(["Q-B"]);
    expect(screen.getByText("Showing 1 of 3 items")).toBeInTheDocument();
  });

  it("explains an empty filter result and clears the filters", async () => {
    const e = entities[0]!.id;
    const { api } = testApi(() => work({ needsAction: [item(1, e, "Q-100", { late: false })] }));
    render(view(api));
    await screen.findByText("Q-100");
    fireEvent.change(screen.getByLabelText("Workflow stage"), { target: { value: "INVOICED" } });
    expect(screen.getByText("No work matches these filters.")).toBeInTheDocument();
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Clear filters" })[0]!);
    expect(await screen.findByText("Q-100")).toBeInTheDocument();
  });

  it("explains when no work is open", async () => {
    const { api } = testApi(() => work({}));
    render(view(api));
    expect(await screen.findByText(/No open work/)).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("uses cards without a table on a narrow screen", async () => {
    const original = window.matchMedia;
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === "(width < 48rem)",
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })) as unknown as typeof window.matchMedia;
    try {
      const { api } = testApi(() => result(entities[0]!.id));
      render(view(api));
      expect(await screen.findByRole("list", { name: "My work items" })).toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Follow up Q-100" })).toBeInTheDocument();
    } finally {
      window.matchMedia = original;
    }
  });
});
