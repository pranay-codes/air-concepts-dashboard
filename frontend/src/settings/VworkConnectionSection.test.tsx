import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { profile } from "../test/fixtures";
import { VworkConnectionSection } from "./VworkConnectionSection";

describe("GL-26 vWork connection", () => {
  it("uses an empty password field, submits once and clears the key", async () => {
    let status = "DISCONNECTED";
    const { api, fetcher } = testApi((_url, init) => {
      if (init?.method === "PUT") status = "ACTIVE";
      return { status };
    });
    render(
      <VworkConnectionSection
        api={api}
        profile={profile}
        onAccessDenied={vi.fn()}
        onDirty={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Connect vWork" }));
    const dialog = screen.getByRole("dialog", { name: "Connect vWork" });
    const input = within(dialog).getByLabelText("vWork API key");
    expect(input).toHaveAttribute("type", "password");
    expect(input).toHaveValue("");
    fireEvent.change(input, { target: { value: "synthetic-vwork-key" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save key" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await screen.findByText("Connected");
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === "PUT")).toHaveLength(1);
    expect(document.body.textContent).not.toContain("synthetic-vwork-key");
    fireEvent.click(screen.getByRole("button", { name: "Replace key" }));
    expect(screen.getByLabelText("vWork API key")).toHaveValue("");
  });

  it("shows a saved but unverified connection and disconnects only after confirmation", async () => {
    let status = "UNVERIFIED";
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    const { api, fetcher } = testApi((_url, init) => {
      if (init?.method === "DELETE") status = "DISCONNECTED";
      return { status };
    });
    render(
      <VworkConnectionSection
        api={api}
        profile={profile}
        onAccessDenied={vi.fn()}
        onDirty={vi.fn()}
      />,
    );
    expect(
      await screen.findByText("Saved, but we couldn't reach vWork to check it."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Disconnect vWork" }));
    await screen.findByText("Not connected");
    expect(confirm).toHaveBeenCalledOnce();
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === "DELETE")).toHaveLength(1);
    confirm.mockRestore();
  });

  it("clears a rejected key and shows a useful error without revealing it", async () => {
    const { api } = testApi((_url, init) =>
      init?.method === "PUT"
        ? new Response(JSON.stringify({ code: "VWORK_KEY_REJECTED" }), {
            status: 400,
            headers: { "Content-Type": "application/problem+json" },
          })
        : { status: "DISCONNECTED" },
    );
    render(
      <VworkConnectionSection
        api={api}
        profile={profile}
        onAccessDenied={vi.fn()}
        onDirty={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Connect vWork" }));
    const input = screen.getByLabelText("vWork API key");
    fireEvent.change(input, { target: { value: "synthetic-rejected-key" } });
    fireEvent.click(screen.getByRole("button", { name: "Save key" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("vWork rejected this key");
    expect(input).toHaveValue("");
    expect(document.body.textContent).not.toContain("synthetic-rejected-key");
  });
});
