import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { JobDetail } from "./JobDetail";

const ITEM = "00000000-0000-4000-8000-000000000010";
const QUOTE = "00000000-0000-4000-8000-000000000011";
const entity = entities[0]!;

function detail(overrides: {
  version?: number;
  notes?: string;
  followUpDate?: string | null;
  attention?: string[];
  allowedActions?: string[];
  invoice?: Record<string, unknown>;
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
      xeroQuoteStatus: "SENT",
      quoteTotal: "123.45",
      currency: "NZD",
      stage: "QUOTED",
      stageEnteredAt: "2026-11-19T00:00:00Z",
      daysInStage: 0,
      late: false,
      nextAction: "FOLLOW_UP",
      flags: [],
      attention: overrides.attention ?? [],
      version: overrides.version ?? 0,
      ...(overrides.followUpDate ? { followUpDate: overrides.followUpDate } : {}),
    },
    notes: overrides.notes ?? "Original note",
    vwork: { reportStatus: "NOT_APPLICABLE", searchState: "NOT_CHECKED" },
    invoice: overrides.invoice ?? {},
    links: {},
    events: [{ id: 1, at: "2026-11-19T00:00:00Z", source: "SYSTEM", eventType: "ITEM_CREATED" }],
    operations: [],
    allowedActions: overrides.allowedActions ?? ["EDIT_NOTES", "CLOSE_LOST"],
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

describe("job detail layout", () => {
  it("explains each attention reason in plain language", async () => {
    renderDetail(() => detail({ attention: ["NO_DEFAULT_OWNER", "OPERATION_FAILED"] }));
    const panel = await screen.findByRole("region", { name: "Needs attention" });
    expect(
      within(panel).getByText(
        "No default owner is set for this entity. A Director can set one in Workflow settings.",
      ),
    ).toBeInTheDocument();
    expect(
      within(panel).getByText(
        "The last acceptance in Xero did not apply. Review the quote, then try again.",
      ),
    ).toBeInTheDocument();
  });

  it("does not show an attention panel when nothing needs attention", async () => {
    renderDetail(() => detail({}));
    expect(await screen.findByRole("heading", { name: "Q-100" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Needs attention" })).toBeNull();
  });

  it("reports unsaved changes, discards them, and confirms a save", async () => {
    const { fetcher } = renderDetail((_url, init) => {
      if (init?.method === "PATCH" || init?.method === "POST") {
        const body = JSON.parse(String(init.body));
        return detail({ version: 1, notes: body.notes, followUpDate: body.followUpDate ?? null });
      }
      return detail({ followUpDate: "2026-12-01" });
    });
    const notes = (await screen.findByLabelText("Notes")) as HTMLTextAreaElement;
    fireEvent.change(notes, { target: { value: "Call back Monday" } });
    expect(await screen.findByText("Unsaved changes")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Discard changes" }));
    expect(notes.value).toBe("Original note");
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();

    fireEvent.change(notes, { target: { value: "Call back Monday" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("Changes saved.")).toBeInTheDocument();
    const write = fetcher.mock.calls.find(
      ([, init]) => init?.method === "PATCH" || init?.method === "POST",
    );
    expect(write).toBeDefined();
    expect(JSON.parse(String(write?.[1]?.body)).notes).toBe("Call back Monday");
  });

  it("shows a clear empty state when no invoice is linked", async () => {
    renderDetail(() => detail({}));
    expect(await screen.findByText("No invoice is linked to this job yet.")).toBeInTheDocument();
  });

  it("keeps Close as lost visually separate with a destructive style", async () => {
    renderDetail(() => detail({ allowedActions: ["CLOSE_LOST"] }));
    const close = await screen.findByRole("button", { name: "Close as lost" });
    expect(close).toHaveClass("danger");
  });
});
