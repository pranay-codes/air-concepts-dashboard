import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile, STAFF_A } from "../test/fixtures";
import Settings from "./Settings";

describe("UC5 Director settings", () => {
  it.each(["read", "confirm"])(
    "FR-UC15-18 rechecks access after denied OAuth attempt %s",
    async (stage) => {
      const attemptId = "00000000-0000-4000-8000-000000000090";
      window.history.replaceState(null, "", `/?xeroAttempt=${attemptId}`);
      const denied = vi.fn();
      const { api } = testApi((url, init) => {
        if (url.pathname.includes("/attempts/")) {
          if (stage === "read" || init?.method === "PUT")
            return new Response("{}", {
              status: 403,
              headers: { "Content-Type": "application/json" },
            });
          return {
            id: attemptId,
            entityId: entities[0]!.id,
            state: "READY",
            choices: [{ connectionId: entities[1]!.id, name: "Authorised organisation" }],
          };
        }
        return { entityId: entities[0]!.id, status: "NOT_CONNECTED" };
      });
      try {
        render(
          <Settings
            api={api}
            profile={profile}
            entities={[entities[0]!]}
            onDirty={vi.fn()}
            onEntitiesChanged={vi.fn()}
            onAccessDenied={denied}
          />,
        );
        if (stage === "confirm") {
          fireEvent.change(await screen.findByLabelText("Xero organisation"), {
            target: { value: entities[1]!.id },
          });
          fireEvent.click(screen.getByRole("button", { name: "Confirm connection" }));
        }
        await waitFor(() => expect(denied).toHaveBeenCalledOnce());
      } finally {
        window.history.replaceState(null, "", "/");
      }
    },
  );
  it("FR-UC15-18 rechecks access immediately when entity creation is denied", async () => {
    const denied = vi.fn();
    const { api } = testApi((_url, init) =>
      init?.method === "POST"
        ? new Response("{}", { status: 403, headers: { "Content-Type": "application/json" } })
        : { entityId: entities[0]!.id, status: "NOT_CONNECTED" },
    );
    render(
      <Settings
        api={api}
        profile={profile}
        entities={[entities[0]!]}
        onDirty={vi.fn()}
        onEntitiesChanged={vi.fn()}
        onAccessDenied={denied}
      />,
    );
    fireEvent.change(screen.getByLabelText("New entity name"), { target: { value: "New entity" } });
    fireEvent.submit(screen.getByRole("form", { name: "Add entity" }));
    await waitFor(() => expect(denied).toHaveBeenCalledOnce());
  });
  it("FR-UC15-16 keeps unsaved user edits when a record switch is cancelled", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const users = [
      {
        id: STAFF_A,
        email: "alex@example.invalid",
        displayName: "Alex",
        role: "DIRECTOR",
        active: true,
      },
      {
        id: entities[1]!.id,
        email: "other@example.invalid",
        displayName: "Other staff",
        role: "STANDARD_USER",
        active: true,
      },
    ];
    const { api } = testApi((url) =>
      url.pathname.endsWith("/users")
        ? users
        : { entityId: entities[0]!.id, status: "NOT_CONNECTED" },
    );
    render(
      <Settings
        api={api}
        profile={profile}
        entities={[entities[0]!]}
        onDirty={vi.fn()}
        onEntitiesChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Users" }));
    fireEvent.click(await screen.findByRole("button", { name: "Edit Alex" }));
    fireEvent.change(screen.getByLabelText("User name"), { target: { value: "Unsaved name" } });
    fireEvent.click(screen.getByRole("button", { name: "Edit Other staff" }));
    expect(screen.getByLabelText("User name")).toHaveValue("Unsaved name");
    expect(screen.getByRole("form", { name: "Edit user" })).toHaveTextContent(
      "alex@example.invalid",
    );
  });
  it("FR-UC15-01_16 denies non-Directors before fetching administration data", () => {
    const { api, fetcher } = testApi(() => []);
    render(
      <Settings
        api={api}
        profile={{ ...profile, role: "STANDARD_USER" }}
        entities={entities}
        onDirty={vi.fn()}
        onEntitiesChanged={vi.fn()}
        onAccessDenied={vi.fn()}
      />,
    );
    expect(screen.getByText(/Only a Director/)).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("FR-UC15-01 creates an entity only after confirmation from the API", async () => {
    const changed = vi.fn();
    const { api, fetcher } = testApi((_url, init) =>
      init?.method === "POST"
        ? { id: "00000000-0000-4000-8000-000000000099", displayName: "Fourth entity" }
        : { entityId: entities[0]!.id, status: "NOT_CONNECTED" },
    );
    render(
      <Settings
        api={api}
        profile={profile}
        entities={[entities[0]!]}
        onDirty={vi.fn()}
        onEntitiesChanged={changed}
        onAccessDenied={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText("New entity name"), {
      target: { value: "Fourth entity" },
    });
    fireEvent.submit(screen.getByRole("form", { name: "Add entity" }));
    expect(await screen.findByText(/Added Fourth entity/)).toBeInTheDocument();
    expect(changed).toHaveBeenCalledOnce();
    expect(
      fetcher.mock.calls.some(
        (call) =>
          call[1]?.method === "POST" &&
          call[1]?.body === JSON.stringify({ displayName: "Fourth entity" }),
      ),
    ).toBe(true);
  });
  it("FR-UC15-16 rechecks the current session after changing its role", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const recheck = vi.fn();
    const staff = {
      id: STAFF_A,
      email: "alex@example.invalid",
      displayName: profile.displayName,
      role: "DIRECTOR",
      active: true,
    };
    const { api } = testApi((url, init) =>
      url.pathname.endsWith("/users")
        ? [staff]
        : init?.method === "PATCH"
          ? { ...staff, role: "FINANCIAL_ADMIN" }
          : { entityId: entities[0]!.id, status: "NOT_CONNECTED" },
    );
    render(
      <Settings
        api={api}
        profile={profile}
        entities={[entities[0]!]}
        onDirty={vi.fn()}
        onEntitiesChanged={vi.fn()}
        onAccessDenied={recheck}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Users" }));
    fireEvent.click(await screen.findByRole("button", { name: `Edit ${profile.displayName}` }));
    fireEvent.change(screen.getByLabelText("User role"), { target: { value: "FINANCIAL_ADMIN" } });
    fireEvent.submit(screen.getByRole("form", { name: "Edit user" }));
    await waitFor(() => expect(recheck).toHaveBeenCalled());
  });
});
