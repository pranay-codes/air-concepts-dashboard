import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
  it("shows classified queues, action and age and validates selected entity", async () => {
    const { api, fetcher } = testApi(() => result(entities[0]!.id));
    render(
      <MyWork
        api={api}
        entity={entities[0]!}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={vi.fn()}
      />,
    );
    expect(await screen.findByRole("heading", { name: "Needs your action" })).toBeInTheDocument();
    expect(screen.getByText("Q-100")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Follow up Q-100" })).toBeInTheDocument();
    expect(screen.getByText("2 days in stage")).toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(([url]) => String(url).includes(`entityId=${entities[0]!.id}`)),
    ).toBe(true);
  });
  it("refuses a row belonging to a different entity", async () => {
    const { api } = testApi(() => result(entities[1]!.id));
    render(
      <MyWork
        api={api}
        entity={entities[0]!}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={vi.fn()}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByText("Q-100")).not.toBeInTheDocument();
  });
  it("rejects an unrecognised entity even in the aggregate view", async () => {
    const unknown = "00000000-0000-4000-8000-000000000099";
    const { api } = testApi(() => result(unknown));
    render(
      <MyWork
        api={api}
        entity={null}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={vi.fn()}
      />,
    );
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
    const view = render(
      <MyWork
        api={api}
        entity={null}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={select}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Follow up Q-100" }));
    expect(select).toHaveBeenCalledWith(entities[0]!.id);
    view.rerender(
      <MyWork
        api={api}
        entity={entities[0]!}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={select}
      />,
    );
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
    render(
      <MyWork
        api={api}
        entity={entities[0]!}
        entities={entities}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
        onSelectEntity={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Follow up Q-100" }));
    fireEvent.click(await screen.findByRole("button", { name: "Back to My Work" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Follow up Q-100" })).toHaveFocus(),
    );
  });
});
