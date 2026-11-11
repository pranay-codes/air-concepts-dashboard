import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ForecastHeader } from "./ForecastHeader";

describe("financial-year navigation", () => {
  it("FR-UC04-22 shows the financial year between previous and next controls", () => {
    const onStep = vi.fn();
    render(<ForecastHeader entityName="Alpha" financialYearLabel="2026-2027" onStep={onStep} />);

    const group = screen.getByRole("group", { name: "Financial year" });
    expect(group.textContent).toContain("2026–2027");

    fireEvent.click(screen.getByRole("button", { name: "Previous financial year" }));
    fireEvent.click(screen.getByRole("button", { name: "Next financial year" }));
    expect(onStep.mock.calls).toEqual([[-1], [1]]);
  });

  it("FR-UC04-22 keeps the controls disabled until the server reports the financial year", () => {
    const onStep = vi.fn();
    render(<ForecastHeader entityName="Alpha" financialYearLabel={null} onStep={onStep} />);

    const previous = screen.getByRole("button", { name: "Previous financial year" });
    const next = screen.getByRole("button", { name: "Next financial year" });
    expect((previous as HTMLButtonElement).disabled).toBe(true);
    expect((next as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByRole("group", { name: "Financial year" }).textContent).toContain("Loading…");
  });

  it("FR-UC04-22 does not render year controls when navigation is not offered", () => {
    render(<ForecastHeader entityName="Alpha" financialYearLabel="2026-2027" />);
    expect(screen.queryByRole("group", { name: "Financial year" })).toBeNull();
  });
});

describe("forecast header subtitle", () => {
  it("shows the entity and forecast period without the currency", () => {
    const { container } = render(<ForecastHeader entityName="HotAs" />);
    expect(container.querySelector(".forecast-heading > p")?.textContent).toBe("HotAs · 12 months");
  });
});

describe("Xero sync time", () => {
  it("shows the sync time in Pacific/Auckland without a zone name or a page-load time", () => {
    const { container } = render(
      <ForecastHeader entityName="Alpha" onSync={vi.fn()} lastSync="2026-11-11T01:16:00Z" />,
    );

    const text = container.textContent ?? "";
    expect(text).toContain("Xero data as at 11 Nov 2026, 2:16 pm");
    expect(text).not.toMatch(/NZDT|NZST|Pacific\/Auckland|Page loaded/);
    expect(container.querySelector("time")?.getAttribute("datetime")).toBe(
      "2026-11-11T01:16:00.000Z",
    );
  });

  it("uses New Zealand standard time when daylight saving is not in effect", () => {
    const { container } = render(
      <ForecastHeader entityName="Alpha" onSync={vi.fn()} lastSync="2026-07-01T00:00:00Z" />,
    );
    expect(container.textContent).toContain("Xero data as at 1 Jul 2026, 12:00 pm");
  });

  it("says when the sync time is unknown instead of showing a placeholder date", () => {
    const { rerender, container } = render(
      <ForecastHeader entityName="Alpha" onSync={vi.fn()} lastSync={null} />,
    );
    expect(container.textContent).toContain("Xero sync time unknown");

    rerender(<ForecastHeader entityName="Alpha" onSync={vi.fn()} lastSync="not-a-date" />);
    expect(container.textContent).toContain("Xero sync time unknown");
    expect(container.querySelector("time")).toBeNull();
  });

  it("marks stale data with a text label", () => {
    render(
      <ForecastHeader entityName="Alpha" onSync={vi.fn()} lastSync="2026-11-11T01:16:00Z" stale />,
    );
    expect(screen.getByText("Stale or not synced")).toBeTruthy();
  });
});
