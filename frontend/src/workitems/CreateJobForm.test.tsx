import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { entities, profile } from "../test/fixtures";
import { testApi } from "../test/api";
import { CreateJobForm } from "./CreateJobForm";

const entity = entities[0]!;
const item = "00000000-0000-4000-8000-000000000010";
const draft = {
  entityId: entity.id,
  itemId: item,
  version: 3,
  reference: "J-1",
  quoteNumber: "Q-1",
  customer: "Synthetic customer",
  contactPerson: "Alex",
  phone: "555123",
  scope: [{ description: "Install", quantity: "1" }],
  templates: [{ templateRef: "template-a", isDefault: true }],
  defaultTemplate: "template-a",
  missingFields: ["siteAddress"],
};
describe("GL-12 create job", () => {
  it("requires the site and submits a price-free entity-scoped request", async () => {
    let submitted: unknown;
    const { api } = testApi((_url, init) => {
      if (init?.method === "POST") {
        submitted = JSON.parse(String(init.body));
        return { operationId: "00000000-0000-4000-8000-000000000011", outcome: "CREATED" };
      }
      return draft;
    });
    const completed = vi.fn();
    render(
      <CreateJobForm
        api={api}
        entity={entity}
        itemId={item}
        profile={profile}
        onDirty={vi.fn()}
        onCancel={vi.fn()}
        onCompleted={completed}
        onAccessDenied={vi.fn()}
      />,
    );
    expect(await screen.findByDisplayValue("J-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create job" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Site address"), {
      target: { value: "Example Street" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create job" }));
    await waitFor(() => expect(completed).toHaveBeenCalledOnce());
    expect(submitted).toMatchObject({
      version: 3,
      reference: "J-1",
      siteAddress: "Example Street",
      scope: [{ description: "Install", quantity: "1" }],
    });
    expect(JSON.stringify(submitted)).not.toMatch(/unitAmount|lineAmount|tax|discount/);
  });
  it("retains entered values when creation fails", async () => {
    const { api } = testApi((_url, init) =>
      init?.method === "POST"
        ? new Response(JSON.stringify({ code: "EXTERNAL_OUTCOME_UNKNOWN" }), {
            status: 502,
            headers: { "Content-Type": "application/problem+json" },
          })
        : draft,
    );
    render(
      <CreateJobForm
        api={api}
        entity={entity}
        itemId={item}
        profile={profile}
        onDirty={vi.fn()}
        onCancel={vi.fn()}
        onCompleted={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    fireEvent.change(await screen.findByLabelText("Site address"), {
      target: { value: "Example Street" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create job" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("Site address")).toHaveValue("Example Street");
    expect(screen.getByRole("button", { name: "Create job" })).toBeDisabled();
  });
  it("rejects a draft from a different entity", async () => {
    const { api } = testApi(() => ({ ...draft, entityId: entities[1]!.id }));
    render(
      <CreateJobForm
        api={api}
        entity={entity}
        itemId={item}
        profile={profile}
        onDirty={vi.fn()}
        onCancel={vi.fn()}
        onCompleted={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("incomplete response");
    expect(screen.queryByRole("button", { name: "Create job" })).not.toBeInTheDocument();
  });
});
