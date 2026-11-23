import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { JobDetail } from "./JobDetail";

const ITEM = "00000000-0000-4000-8000-000000000010";
const QUOTE = "00000000-0000-4000-8000-000000000011";
const entity = entities[0]!;

function detail(overrides: {
  version?: number;
  stage?: string;
  xeroQuoteStatus?: string;
  allowedActions?: string[];
}) {
  return {
    summary: {
      id: ITEM,
      entityId: entity.id,
      entityName: entity.displayName,
      xeroQuoteId: QUOTE,
      quoteNumber: "Q-100",
      jobId: "J-100",
      customerName: "Example customer",
      xeroQuoteStatus: overrides.xeroQuoteStatus ?? "SENT",
      quoteTotal: "123.45",
      currency: "NZD",
      stage: overrides.stage ?? "QUOTED",
      stageEnteredAt: "2026-11-19T00:00:00Z",
      daysInStage: 0,
      late: false,
      nextAction: "ACCEPT",
      flags: [],
      attention: [],
      version: overrides.version ?? 0,
    },
    notes: "",
    vwork: { reportStatus: "NOT_APPLICABLE", searchState: "NOT_CHECKED" },
    invoice: {},
    links: {},
    events: [{ id: 1, at: "2026-11-19T00:00:00Z", source: "SYSTEM", eventType: "ITEM_CREATED" }],
    operations: [],
    allowedActions: overrides.allowedActions ?? ["MARK_ACCEPTED", "EDIT_NOTES", "CLOSE_LOST"],
  };
}

function renderDetail(handler: Parameters<typeof testApi>[0]) {
  const { api, fetcher } = testApi(handler);
  render(
    <JobDetail
      api={api}
      entity={entity}
      profile={profile}
      itemId={ITEM}
      onBack={vi.fn()}
      onDirty={vi.fn()}
      onChanged={vi.fn()}
      onAccessDenied={vi.fn()}
    />,
  );
  return { fetcher };
}

const isAccept = (url: unknown) => String(url).endsWith("/mark-accepted");

describe("GL-08 accept a sent quote from the job", () => {
  it("shows the accept action only when the server allows it", async () => {
    renderDetail(() => detail({ allowedActions: ["EDIT_NOTES"] }));
    expect(await screen.findByRole("heading", { name: "Q-100" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept quote in Xero" })).toBeNull();
  });

  it("asks for confirmation with the organisation, quote and total before writing to Xero", async () => {
    const { fetcher } = renderDetail(() => detail({}));
    fireEvent.click(await screen.findByRole("button", { name: "Accept quote in Xero" }));
    const dialog = await screen.findByRole("dialog", { name: "Accept quote in Xero" });
    expect(within(dialog).getByText(entity.displayName)).toBeInTheDocument();
    expect(within(dialog).getByText("Q-100")).toBeInTheDocument();
    expect(within(dialog).getByText("Example customer")).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => isAccept(url))).toBe(false);
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(fetcher.mock.calls.some(([url]) => isAccept(url))).toBe(false);
  });

  it("moves the job to To schedule only after the service confirms, and sends the version it saw", async () => {
    const { fetcher } = renderDetail((url, init) => {
      if (url.pathname.endsWith("/mark-accepted")) {
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({ version: 0 });
        return detail({
          version: 1,
          stage: "TO_SCHEDULE",
          xeroQuoteStatus: "ACCEPTED",
          allowedActions: ["CLOSE_LOST"],
        });
      }
      return detail({});
    });
    fireEvent.click(await screen.findByRole("button", { name: "Accept quote in Xero" }));
    fireEvent.click(await screen.findByRole("button", { name: "Accept in Xero" }));
    expect(
      await screen.findByText("Quote accepted in Xero. The job is now To schedule."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept quote in Xero" })).toBeNull();
    expect(screen.getByText("To schedule")).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([url]) => isAccept(url))).toHaveLength(1);
  });

  it("holds the job for review when Xero's outcome is unknown and offers no second write", async () => {
    const { fetcher } = renderDetail((url) => {
      if (url.pathname.endsWith("/mark-accepted")) {
        return new Response(JSON.stringify({ code: "EXTERNAL_OUTCOME_UNKNOWN" }), {
          status: 502,
          headers: { "Content-Type": "application/json" },
        });
      }
      return detail({});
    });
    fireEvent.click(await screen.findByRole("button", { name: "Accept quote in Xero" }));
    fireEvent.click(await screen.findByRole("button", { name: "Accept in Xero" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Accept in Xero" })).toBeDisabled(),
    );
    expect(fetcher.mock.calls.filter(([url]) => isAccept(url))).toHaveLength(1);
  });
});
