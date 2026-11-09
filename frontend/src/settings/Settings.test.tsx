import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { testApi } from "../test/api";
import { entities, profile, STAFF_A } from "../test/fixtures";
import Settings from "./Settings";

const ATTEMPT = "00000000-0000-4000-8000-000000000090";
const NEW_ENTITY = "00000000-0000-4000-8000-000000000099";
const authorization = {
  attemptId: ATTEMPT,
  authorizationUrl: `https://login.xero.com/identity/connect/authorize?response_type=code&client_id=synthetic&redirect_uri=https%3A%2F%2Fapi.example.invalid%2Fapi%2Fv1%2Fxero%2Fcallback&scope=offline_access%20accounting.invoices.read%20accounting.attachments.read&state=${"a".repeat(43)}`,
  expiresAt: "2030-01-01T00:00:00Z",
};
const connected = {
  entityId: entities[0]!.id,
  status: "CONNECTED",
  organisationName: "Air Concepts Service Ltd",
  maskedTenantId: "b41e…7c21",
  connectedBy: "Doc",
  lastConfirmedAt: "2026-10-14T03:00:00Z",
  lastCheckedAt: "2026-11-09T21:42:00Z",
};
const notConnected = { entityId: entities[0]!.id, status: "NOT_CONNECTED" };

function renderSettings(
  api: Parameters<typeof Settings>[0]["api"],
  overrides: Partial<Parameters<typeof Settings>[0]> = {},
) {
  const props = {
    api,
    profile,
    entities: [entities[0]!],
    onDirty: vi.fn(),
    onEntitiesChanged: vi.fn(),
    onAccessDenied: vi.fn(),
    openAuthorization: vi.fn(),
    ...overrides,
  };
  render(<Settings {...props} />);
  return props;
}

describe("UC5 Director settings", () => {
  it.each(["read", "confirm"])(
    "FR-UC15-18 rechecks access after denied OAuth attempt %s",
    async (stage) => {
      window.history.replaceState(null, "", `/?xeroAttempt=${ATTEMPT}`);
      const denied = vi.fn();
      const { api } = testApi((url, init) => {
        if (url.pathname.includes("/attempts/")) {
          if (stage === "read" || init?.method === "PUT")
            return new Response("{}", {
              status: 403,
              headers: { "Content-Type": "application/json" },
            });
          return {
            id: ATTEMPT,
            entityId: entities[0]!.id,
            state: "READY",
            choices: [{ connectionId: entities[1]!.id, name: "Authorised organisation" }],
          };
        }
        return notConnected;
      });
      try {
        renderSettings(api, { onAccessDenied: denied });
        if (stage === "confirm") {
          const dialog = await screen.findByRole("dialog", { name: /Connect First entity/ });
          fireEvent.change(within(dialog).getByLabelText("Xero organisation"), {
            target: { value: entities[1]!.id },
          });
          fireEvent.click(within(dialog).getByRole("button", { name: "Continue" }));
          fireEvent.click(within(dialog).getByRole("button", { name: "Confirm connection" }));
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
        : notConnected,
    );
    renderSettings(api, { onAccessDenied: denied });
    fireEvent.click(screen.getByRole("button", { name: "Add entity" }));
    fireEvent.change(screen.getByLabelText("Entity name"), { target: { value: "New entity" } });
    fireEvent.submit(screen.getByRole("form", { name: "Name the entity" }));
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
    const { api } = testApi((url) => (url.pathname.endsWith("/users") ? users : notConnected));
    renderSettings(api);
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
  it("FR-UC15-01 creates an entity, then starts Xero sign-in for that entity", async () => {
    const changed = vi.fn();
    const openAuthorization = vi.fn();
    const { api, fetcher } = testApi((url, init) => {
      if (init?.method === "POST" && url.pathname.endsWith("/xero/authorization"))
        return authorization;
      if (init?.method === "POST") return { id: NEW_ENTITY, displayName: "Fourth entity" };
      return notConnected;
    });
    renderSettings(api, { onEntitiesChanged: changed, openAuthorization });
    fireEvent.click(screen.getByRole("button", { name: "Add entity" }));
    fireEvent.change(screen.getByLabelText("Entity name"), {
      target: { value: "Fourth entity" },
    });
    fireEvent.submit(screen.getByRole("form", { name: "Name the entity" }));
    await waitFor(() =>
      expect(openAuthorization).toHaveBeenCalledWith(authorization.authorizationUrl),
    );
    expect(changed).toHaveBeenCalledOnce();
    expect(
      fetcher.mock.calls.some(
        (call) =>
          call[1]?.method === "POST" &&
          call[1]?.body === JSON.stringify({ displayName: "Fourth entity" }),
      ),
    ).toBe(true);
  });
  it("FR-UC15-01 does not add the entity twice when starting Xero sign-in fails", async () => {
    let starts = 0;
    const { api, fetcher } = testApi((url, init) => {
      if (init?.method === "POST" && url.pathname.endsWith("/xero/authorization")) {
        starts += 1;
        return starts === 1
          ? new Response("{}", { status: 503, headers: { "Content-Type": "application/json" } })
          : authorization;
      }
      if (init?.method === "POST") return { id: NEW_ENTITY, displayName: "Fourth entity" };
      return notConnected;
    });
    const openAuthorization = vi.fn();
    renderSettings(api, { openAuthorization });
    fireEvent.click(screen.getByRole("button", { name: "Add entity" }));
    fireEvent.change(screen.getByLabelText("Entity name"), { target: { value: "Fourth entity" } });
    fireEvent.submit(screen.getByRole("form", { name: "Name the entity" }));
    fireEvent.click(await screen.findByRole("button", { name: "Continue to Xero" }));
    await waitFor(() => expect(openAuthorization).toHaveBeenCalledOnce());
    const creations = fetcher.mock.calls.filter(
      (call) => call[1]?.method === "POST" && String(call[0]).endsWith("/api/v1/entities"),
    );
    expect(creations).toHaveLength(1);
  });
  it("FR-UC15-01 asks before discarding a typed entity name", async () => {
    const confirm = vi
      .spyOn(window, "confirm")
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);
    const { api } = testApi(() => notConnected);
    renderSettings(api);
    fireEvent.click(screen.getByRole("button", { name: "Add entity" }));
    fireEvent.change(screen.getByLabelText("Entity name"), { target: { value: "Typed" } });
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.getByRole("dialog", { name: "Add entity" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("dialog", { name: "Add entity" })).not.toBeInTheDocument();
  });
  it("FR-UC15-01 closes the add dialog with Escape when nothing is typed", async () => {
    const { api } = testApi(() => notConnected);
    renderSettings(api);
    const opener = screen.getByRole("button", { name: "Add entity" });
    opener.focus();
    fireEvent.click(opener);
    const dialog = screen.getByRole("dialog", { name: "Add entity" });
    fireEvent.keyDown(dialog, { key: "Escape" });
    expect(screen.queryByRole("dialog", { name: "Add entity" })).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });
  it("FR-UC15-18 completes a returned Xero attempt through organisation choice and confirmation", async () => {
    window.history.replaceState(null, "", `/?xeroAttempt=${ATTEMPT}`);
    const put = vi.fn();
    const { api } = testApi((url, init) => {
      if (url.pathname.includes("/attempts/")) {
        if (init?.method === "PUT") {
          put(JSON.parse(String(init.body)));
          return connected;
        }
        return {
          id: ATTEMPT,
          entityId: entities[0]!.id,
          state: "READY",
          choices: [{ connectionId: entities[1]!.id, name: "Authorised organisation" }],
        };
      }
      return notConnected;
    });
    try {
      renderSettings(api);
      const dialog = await screen.findByRole("dialog", { name: /Connect First entity/ });
      fireEvent.change(within(dialog).getByLabelText("Xero organisation"), {
        target: { value: entities[1]!.id },
      });
      fireEvent.click(within(dialog).getByRole("button", { name: "Continue" }));
      expect(within(dialog).getByText(/will use/)).toHaveTextContent("Authorised organisation");
      fireEvent.click(within(dialog).getByRole("button", { name: "Confirm connection" }));
      expect(
        await screen.findByText("Connected First entity to Authorised organisation."),
      ).toBeInTheDocument();
      expect(put).toHaveBeenCalledWith({ connectionId: entities[1]!.id });
      expect(window.location.search).toBe("");
    } finally {
      window.history.replaceState(null, "", "/");
    }
  });
  it("FR-UC15-02 renames an entity and keeps the typed name when saving fails", async () => {
    const patched = vi.fn();
    const { api } = testApi((url, init) => {
      if (init?.method === "PATCH") {
        patched(JSON.parse(String(init.body)));
        return { id: entities[0]!.id, displayName: "Renamed entity" };
      }
      return url.pathname.endsWith("/xero") ? connected : notConnected;
    });
    renderSettings(api);
    fireEvent.click(await screen.findByRole("button", { name: "Edit" }));
    const dialog = screen.getByRole("dialog", { name: "Rename entity" });
    fireEvent.change(within(dialog).getByLabelText("Entity name"), {
      target: { value: "  Renamed entity  " },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save name" }));
    expect(await screen.findByText("Entity name saved.")).toBeInTheDocument();
    expect(patched).toHaveBeenCalledWith({ displayName: "Renamed entity" });
  });
  it("FR-UC15-01 archives an entity after the review step without deleting Xero access", async () => {
    const archived = vi.fn();
    const changed = vi.fn();
    const { api } = testApi((url, init) => {
      if (init?.method === "POST" && url.pathname.endsWith("/archive")) {
        archived();
        return new Response(null, { status: 204 });
      }
      return url.pathname.endsWith("/xero") ? connected : notConnected;
    });
    renderSettings(api, { onEntitiesChanged: changed });
    fireEvent.click(await screen.findByRole("button", { name: "Remove" }));
    const dialog = screen.getByRole("dialog", { name: "Remove First entity?" });
    expect(dialog).toHaveTextContent("Xero access is not revoked");
    expect(dialog).toHaveTextContent("Air Concepts Service Ltd stays");
    fireEvent.click(within(dialog).getByRole("button", { name: "Remove entity" }));
    expect(
      await screen.findByText("First entity was removed from the entity list."),
    ).toBeInTheDocument();
    expect(archived).toHaveBeenCalledOnce();
    expect(changed).toHaveBeenCalledOnce();
  });
  it("FR-UC15-01_30 shows a masked tenant ID and a clear state for unrecorded details", async () => {
    const { api } = testApi((url) =>
      url.pathname.endsWith("/xero") ? { ...connected, connectedBy: undefined } : notConnected,
    );
    renderSettings(api);
    expect(await screen.findByText("b41e…7c21")).toBeInTheDocument();
    expect(screen.getByText("Not recorded", { selector: "dd" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Connect First entity/ })).not.toBeInTheDocument();
  });
  it("FR-UC15-18 offers Connect and Reconnect only where the entity needs them", async () => {
    const { api } = testApi((url) =>
      url.pathname.endsWith("/xero")
        ? { entityId: entities[0]!.id, status: "NEEDS_RECONNECTION" }
        : notConnected,
    );
    renderSettings(api);
    expect(
      await screen.findByRole("button", { name: "Reconnect First entity" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Needs reconnection")).toBeInTheDocument();
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
          : notConnected,
    );
    renderSettings(api, { onAccessDenied: recheck });
    fireEvent.click(screen.getByRole("button", { name: "Users" }));
    fireEvent.click(await screen.findByRole("button", { name: `Edit ${profile.displayName}` }));
    fireEvent.change(screen.getByLabelText("User role"), { target: { value: "FINANCIAL_ADMIN" } });
    fireEvent.submit(screen.getByRole("form", { name: "Edit user" }));
    await waitFor(() => expect(recheck).toHaveBeenCalled());
  });
});
