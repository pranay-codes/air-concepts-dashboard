import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { SyncFreshness } from "./SyncFreshness";

const entity = entities[0]!;
const run = {
  id: "00000000-0000-4000-8000-000000000021",
  status: "SUCCEEDED",
  startedAt: "2026-11-20T00:00:00Z",
  finishedAt: "2026-11-20T00:01:00Z",
};
const failure = {
  id: "00000000-0000-4000-8000-000000000022",
  status: "FAILED",
  startedAt: "2026-11-20T01:00:00Z",
  finishedAt: "2026-11-20T01:01:00Z",
  errorCode: "QUOTE_SYNC_COMPLETENESS_UNVERIFIED",
};

describe("GL-25 sync freshness", () => {
  it("shows last success and later failure without claiming that vWork was synced", async () => {
    const { api } = testApi(() => ({
      entityId: entity.id,
      xero: { latest: failure, lastSuccess: run, lastFailure: failure },
      vwork: {},
    }));
    render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const xero = await screen.findByRole("group", { name: "Xero data" });
    expect(within(xero).getByText("Sync failed")).toBeInTheDocument();
    expect(within(xero).getByText(/Last successful sync/)).toBeInTheDocument();
    const vwork = screen.getByRole("group", { name: "vWork data" });
    expect(within(vwork).getByText("Not synced")).toBeInTheDocument();
    expect(within(vwork).getByText("No successful sync recorded")).toBeInTheDocument();
    expect(screen.queryByText("Synced")).not.toBeInTheDocument();
    expect(
      within(xero).getByText(/could not confirm that the complete list was received/),
    ).toBeInTheDocument();
    expect(screen.getByText(/QUOTE_SYNC_COMPLETENESS_UNVERIFIED/)).toBeInTheDocument();
  });

  it("explains a missing vWork connection and handles unknown failures safely", async () => {
    const { api } = testApi(() => ({
      entityId: entity.id,
      xero: { latest: { ...failure, errorCode: "UNRECOGNISED_ERROR" } },
      vwork: { latest: { ...failure, errorCode: "VWORK_NOT_CONNECTED" } },
    }));
    render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const vwork = await screen.findByRole("group", { name: "vWork data" });
    expect(within(vwork).getByText(/vWork is not connected/)).toBeInTheDocument();
    expect(
      within(screen.getByRole("group", { name: "Xero data" })).getByText(/could not be completed/),
    ).toBeInTheDocument();
  });

  it("runs an explicit entity-scoped refresh and reloads work", async () => {
    const changed = vi.fn();
    const { api, fetcher } = testApi(() => ({ entityId: entity.id, xero: {}, vwork: {} }));
    render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={changed}
        onAccessDenied={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: /Refresh sync/ }));
    await waitFor(() => expect(changed).toHaveBeenCalledOnce());
    expect(
      fetcher.mock.calls.some(
        ([url, init]) =>
          String(url).includes(`/entities/${entity.id}/green-line/sync`) && init?.method === "POST",
      ),
    ).toBe(true);
  });

  it("rejects a status belonging to a different entity", async () => {
    const { api } = testApi(() => ({ entityId: entities[1]!.id, xero: {}, vwork: {} }));
    render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    expect(await screen.findByText("Sync status is unavailable. Try again.")).toBeInTheDocument();
    expect(screen.queryByText("Synced")).not.toBeInTheDocument();
  });

  it("reports a failed refresh and re-reads the last recorded status", async () => {
    const changed = vi.fn();
    const { api, fetcher } = testApi((_url, init) =>
      init?.method === "POST"
        ? new Response(JSON.stringify({ code: "QUOTE_SYNC_COMPLETENESS_UNVERIFIED" }), {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          })
        : {
            entityId: entity.id,
            xero: { latest: failure, lastSuccess: run, lastFailure: failure },
            vwork: {},
          },
    );
    render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={changed}
        onAccessDenied={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Refresh sync" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "could not confirm that all quotes were returned",
    );
    expect(changed).toHaveBeenCalledOnce();
    await waitFor(() =>
      expect(
        fetcher.mock.calls.filter(([, init]) => !init?.method || init.method === "GET").length,
      ).toBeGreaterThan(1),
    );
  });

  it("does not show a late response from the previous entity", async () => {
    let finishFirst!: (value: unknown) => void;
    const first = new Promise((resolve) => {
      finishFirst = resolve;
    });
    const { api, fetcher } = testApi((url) =>
      url.pathname.includes(entity.id) ? first : { entityId: entities[1]!.id, xero: {}, vwork: {} },
    );
    const view = render(
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    await waitFor(() => expect(fetcher).toHaveBeenCalled());
    view.rerender(
      <SyncFreshness
        api={api}
        entity={entities[1]!}
        profile={profile}
        onChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const xero = await screen.findByRole("group", { name: "Xero data" });
    expect(within(xero).getByText("Not synced")).toBeInTheDocument();
    finishFirst({ entityId: entity.id, xero: { lastSuccess: run }, vwork: {} });
    await waitFor(() => expect(screen.queryByText("Synced")).not.toBeInTheDocument());
    expect(within(xero).getByText("Not synced")).toBeInTheDocument();
  });
});
