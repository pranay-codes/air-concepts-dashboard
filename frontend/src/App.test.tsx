import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("App", () => {
  it("shows the dashboard heading and the placeholder layout region", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Air Concepts Financial Admin Dashboard" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Dashboard content" })).toBeInTheDocument();
  });
});
