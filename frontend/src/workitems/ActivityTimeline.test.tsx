import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ActivityTimeline } from "./ActivityTimeline";

describe("GL-07 activity timeline", () => {
  it("lists each change with from and to values readable without the arrow", () => {
    render(
      <ActivityTimeline
        events={[
          {
            id: 1,
            at: "2026-11-24T01:30:00Z",
            source: "XERO_SYNC",
            eventType: "STAGE_CHANGED",
            fromStage: "JOB_COMPLETED",
            toStage: "INVOICE_CREATED",
          },
        ]}
      />,
    );
    const entry = screen.getByRole("listitem");
    expect(within(entry).getByText("Invoice created", { selector: "p" })).toBeInTheDocument();
    expect(within(entry).getByRole("definition")).toHaveTextContent(
      "Job completed → changed to Invoice created",
    );
    expect(within(entry).getByText("→")).toHaveAttribute("aria-hidden", "true");
    expect(within(entry).getByText("24 Nov 2026, 2:30 pm")).toHaveAttribute(
      "datetime",
      "2026-11-24T01:30:00Z",
    );
    expect(entry).toHaveTextContent("Xero sync");
    expect(
      screen.getByText("Times show when the dashboard recorded each change."),
    ).toBeInTheDocument();
  });

  it("states when there is no activity", () => {
    render(<ActivityTimeline events={[]} />);
    expect(screen.getByText("No activity recorded.")).toBeInTheDocument();
    expect(screen.queryByRole("list")).toBeNull();
  });
});
