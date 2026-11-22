import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile } from "../test/fixtures";
import { VworkTemplatesSection } from "./VworkTemplatesSection";

describe("GL-06 entity vWork templates", () => {
  it("saves the chosen default for the correct entity after review", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    const dirty = vi.fn();
    const { api, fetcher } = testApi((_url, init) =>
      init?.method === "PUT" ? JSON.parse(String(init.body)).templates : [],
    );
    render(
      <VworkTemplatesSection
        api={api}
        entity={entities[0]!}
        profile={profile}
        onDirty={dirty}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `vWork templates for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(form).getByLabelText("Template references"), {
      target: { value: "template-a\ntemplate-b" },
    });
    fireEvent.change(within(form).getByLabelText("Default template"), {
      target: { value: "template-b" },
    });
    expect(dirty).toHaveBeenLastCalledWith(true);
    fireEvent.submit(form);
    expect(await within(form).findByText("vWork templates saved.")).toBeInTheDocument();
    const calls = fetcher.mock.calls.filter(([, init]) => init?.method === "PUT");
    expect(calls).toHaveLength(1);
    expect(String(calls[0]![0])).toContain(`/entities/${entities[0]!.id}/vwork-templates`);
    expect(JSON.parse(String(calls[0]![1]?.body))).toEqual({
      templates: [
        { templateRef: "template-a", isDefault: false },
        { templateRef: "template-b", isDefault: true },
      ],
    });
    expect(dirty).toHaveBeenLastCalledWith(false);
    expect(confirm).toHaveBeenCalledOnce();
    confirm.mockRestore();
  });

  it("rejects duplicates and requires an explicit default before writing", async () => {
    const { api, fetcher } = testApi(() => []);
    render(
      <VworkTemplatesSection
        api={api}
        entity={entities[0]!}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `vWork templates for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(form).getByLabelText("Template references"), {
      target: { value: "same\nsame" },
    });
    fireEvent.submit(form);
    expect(await within(form).findByRole("alert")).toHaveTextContent("unique");
    fireEvent.change(within(form).getByLabelText("Template references"), {
      target: { value: "one\ntwo" },
    });
    fireEvent.submit(form);
    expect(within(form).getByRole("alert")).toHaveTextContent("default");
    expect(fetcher.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);
  });

  it("keeps the entered values on a cross-entity conflict", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const { api } = testApi((_url, init) =>
      init?.method === "PUT"
        ? new Response(JSON.stringify({ code: "VWORK_TEMPLATE_IN_USE" }), {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          })
        : [],
    );
    render(
      <VworkTemplatesSection
        api={api}
        entity={entities[0]!}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `vWork templates for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(form).getByLabelText("Template references"), {
      target: { value: "shared" },
    });
    fireEvent.change(within(form).getByLabelText("Default template"), {
      target: { value: "shared" },
    });
    fireEvent.submit(form);
    await waitFor(() =>
      expect(within(form).getByRole("alert")).toHaveTextContent("another entity"),
    );
    expect(within(form).getByLabelText("Template references")).toHaveValue("shared");
    vi.restoreAllMocks();
  });
});
