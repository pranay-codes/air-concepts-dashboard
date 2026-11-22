import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
    expect(await screen.findByText(/Xero last synced/)).toBeInTheDocument();
    expect(screen.getByText(/Xero refresh did not complete/)).toBeInTheDocument();
    expect(screen.getByText("vWork has not synced yet.")).toBeInTheDocument();
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
    expect(screen.queryByText(/Xero last synced/)).not.toBeInTheDocument();
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
    expect(await screen.findByText("Xero has not synced yet.")).toBeInTheDocument();
    finishFirst({ entityId: entity.id, xero: { lastSuccess: run }, vwork: {} });
    await waitFor(() => expect(screen.queryByText(/Xero last synced/)).not.toBeInTheDocument());
  });
});
