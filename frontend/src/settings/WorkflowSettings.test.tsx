import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile, STAFF_A } from "../test/fixtures";
import { WorkflowSettings } from "./WorkflowSettings";

const manager = "00000000-0000-4000-8000-000000000071";
const staff = [
  {
    id: STAFF_A,
    email: "finance@example.invalid",
    displayName: "Finance",
    role: "FINANCIAL_ADMIN",
    active: true,
  },
  {
    id: manager,
    email: "manager@example.invalid",
    displayName: "Manager",
    role: "STANDARD_USER",
    active: true,
  },
];
const initial = {
  entityId: entities[0]!.id,
  enabled: false,
  lateThresholdWorkingDays: 2,
  backfillDays: 90,
};

describe("GL workflow settings", () => {
  it("GL-AUTO-SYNC-01 saves an automatic sync interval for the entity", async () => {
    const saved = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/users")) return staff;
      if (url.pathname.endsWith("/vwork-templates")) return [];
      if (url.pathname.endsWith("/vwork-connection")) return { status: "DISCONNECTED" };
      if (init?.method === "PUT") {
        const body = JSON.parse(String(init.body));
        saved(body);
        return { entityId: entities[0]!.id, ...body, automaticSyncAvailable: true };
      }
      return {
        ...initial,
        automaticSyncEnabled: false,
        automaticSyncIntervalMinutes: 10,
        automaticSyncAvailable: true,
      };
    });
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    fireEvent.click(within(form).getByLabelText("Enable automatic sync"));
    fireEvent.change(within(form).getByLabelText("Sync interval in minutes"), {
      target: { value: "1" },
    });
    fireEvent.submit(form);
    await waitFor(() =>
      expect(saved).toHaveBeenCalledWith(
        expect.objectContaining({ automaticSyncEnabled: true, automaticSyncIntervalMinutes: 1 }),
      ),
    );
  });

  it("GL-AUTO-SYNC-01 rejects an interval below the minimum without saving", async () => {
    const saved = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/users")) return staff;
      if (url.pathname.endsWith("/vwork-templates")) return [];
      if (url.pathname.endsWith("/vwork-connection")) return { status: "DISCONNECTED" };
      if (init?.method === "PUT") saved();
      return initial;
    });
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(form).getByLabelText("Sync interval in minutes"), {
      target: { value: "0" },
    });
    fireEvent.submit(form);
    expect(
      await within(form).findByText("Enter a sync interval from 1 to 1440 minutes."),
    ).toBeInTheDocument();
    expect(saved).not.toHaveBeenCalled();
  });
  it("GL-06 keeps template edits dirty when other workflow settings are saved", async () => {
    const dirty = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/users")) return staff;
      if (url.pathname.endsWith("/vwork-templates")) return [];
      if (url.pathname.endsWith("/vwork-connection")) return { status: "DISCONNECTED" };
      if (init?.method === "PUT")
        return { entityId: entities[0]!.id, ...JSON.parse(String(init.body)) };
      return initial;
    });
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={dirty}
        onAccessDenied={vi.fn()}
      />,
    );
    const templates = await screen.findByRole("form", {
      name: `vWork templates for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(templates).getByLabelText("Template references"), {
      target: { value: "unsaved-template" },
    });
    const workflow = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(workflow).getByLabelText("Backfill window in days"), {
      target: { value: "30" },
    });
    fireEvent.submit(workflow);
    expect(await within(workflow).findByText("Workflow settings saved.")).toBeInTheDocument();
    expect(dirty).toHaveBeenLastCalledWith(true);
    expect(within(templates).getByLabelText("Template references")).toHaveValue("unsaved-template");
  });

  it("GL-08 and GL-11 loads entity defaults, limits owner choices and saves confirmed changes", async () => {
    const saved = vi.fn();
    const dirty = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/users")) return staff;
      if (init?.method === "PUT") {
        const body = JSON.parse(String(init.body));
        saved(body);
        return { entityId: entities[0]!.id, ...body };
      }
      return initial;
    });
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={dirty}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    const owner = within(form).getByLabelText("Default owner");
    expect(within(owner).getByRole("option", { name: "Finance" })).toBeInTheDocument();
    expect(within(owner).queryByRole("option", { name: "Manager" })).not.toBeInTheDocument();
    fireEvent.change(owner, { target: { value: STAFF_A } });
    fireEvent.change(within(form).getByLabelText("Service Manager"), {
      target: { value: manager },
    });
    fireEvent.change(within(form).getByLabelText("Late after working days"), {
      target: { value: "3" },
    });
    fireEvent.click(within(form).getByLabelText("Enable quote-to-invoice workflow"));
    expect(dirty).toHaveBeenCalledWith(true);
    fireEvent.submit(form);
    await waitFor(() => expect(saved).toHaveBeenCalledOnce());
    expect(saved).toHaveBeenCalledWith({
      enabled: true,
      automaticSyncEnabled: false,
      automaticSyncIntervalMinutes: 10,
      defaultOwnerId: STAFF_A,
      serviceManagerId: manager,
      lateThresholdWorkingDays: 3,
      backfillDays: 90,
    });
    expect(await within(form).findByText("Workflow settings saved.")).toBeInTheDocument();
    expect(dirty).toHaveBeenLastCalledWith(false);
  });

  it("GL-11 rejects an out-of-range threshold without writing", async () => {
    const { api, fetcher } = testApi((url) => (url.pathname.endsWith("/users") ? staff : initial));
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    const form = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    fireEvent.change(within(form).getByLabelText("Late after working days"), {
      target: { value: "31" },
    });
    fireEvent.submit(form);
    expect(await within(form).findByText("Enter a whole number from 1 to 30.")).toBeInTheDocument();
    expect(within(form).getByLabelText("Late after working days")).toHaveFocus();
    expect(within(form).getByLabelText("Late after working days")).toHaveAccessibleDescription(
      "Enter a whole number from 1 to 30.",
    );
    expect(fetcher.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);
  });

  it("GL-06 keeps another entity's unsaved changes protected after one entity is saved", async () => {
    const dirty = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.endsWith("/users")) return staff;
      const entityId = url.pathname.includes(entities[1]!.id) ? entities[1]!.id : entities[0]!.id;
      if (init?.method === "PUT") return { entityId, ...JSON.parse(String(init.body)) };
      return { ...initial, entityId };
    });
    render(
      <WorkflowSettings
        api={api}
        entities={entities.slice(0, 2)}
        profile={profile}
        onDirty={dirty}
        onAccessDenied={vi.fn()}
      />,
    );
    const first = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[0]!.displayName}`,
    });
    const second = await screen.findByRole("form", {
      name: `Workflow settings for ${entities[1]!.displayName}`,
    });
    fireEvent.change(within(first).getByLabelText("Backfill window in days"), {
      target: { value: "60" },
    });
    fireEvent.change(within(second).getByLabelText("Backfill window in days"), {
      target: { value: "30" },
    });
    fireEvent.submit(first);
    expect(await within(first).findByText("Workflow settings saved.")).toBeInTheDocument();
    expect(dirty).toHaveBeenLastCalledWith(true);
    expect(within(second).getByLabelText("Backfill window in days")).toHaveValue(30);
  });

  it("GL-08 starts entity settings reads while the staff list is still loading", async () => {
    let finishStaff: (value: typeof staff) => void = () => {};
    const delayedStaff = new Promise<typeof staff>((resolve) => {
      finishStaff = resolve;
    });
    const { api, fetcher } = testApi((url) =>
      url.pathname.endsWith("/users") ? delayedStaff : initial,
    );
    render(
      <WorkflowSettings
        api={api}
        entities={[entities[0]!]}
        profile={profile}
        onDirty={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(([input]) => String(input).includes("/green-line/settings")),
      ).toBe(true),
    );
    finishStaff(staff);
    expect(
      await screen.findByRole("form", {
        name: `Workflow settings for ${entities[0]!.displayName}`,
      }),
    ).toBeInTheDocument();
  });
});
