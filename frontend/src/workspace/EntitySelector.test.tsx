import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { entities } from "../test/fixtures";
import { EntitySelector } from "./EntitySelector";

describe("UC5 authorised entity selector", () => {
  it("FR-UC15-02_30 searches dynamic server data while keeping the full current name visible", () => {
    const select = vi.fn();
    render(<EntitySelector entities={entities} selectedId={entities[0]!.id} onSelect={select} />);
    fireEvent.change(screen.getByLabelText("Find entity"), { target: { value: "Additional" } });
    expect(screen.getByRole("option", { name: entities[1]!.displayName })).toBeInTheDocument();
    expect(screen.getByText(entities[0]!.displayName, { selector: "strong" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Entity"), { target: { value: entities[1]!.id } });
    expect(select).toHaveBeenCalledWith(entities[1]!.id);
    expect(screen.getByLabelText("Find entity")).toHaveValue("");
  });
  it("FR-UC15-30 never selects an ID outside the authorised list", () => {
    const select = vi.fn();
    render(<EntitySelector entities={entities} selectedId={entities[0]!.id} onSelect={select} />);
    fireEvent.change(screen.getByLabelText("Entity"), { target: { value: "not-authorised" } });
    expect(select).not.toHaveBeenCalled();
  });
  it("FR-UC15-30 identifies the no-entity state without an aggregate option", () => {
    render(<EntitySelector entities={[]} selectedId={null} onSelect={vi.fn()} />);
    expect(screen.getByLabelText("Entity")).toBeDisabled();
    expect(screen.queryByRole("option", { name: "All entities" })).not.toBeInTheDocument();
  });
});
