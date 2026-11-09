import { useCallback, useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Profile, Role, Staff } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice, roleNames } from "../ui/Feedback";

interface Props {
  api: DashboardApi;
  profile: Profile;
  onDirty(value: boolean): void;
  onAccessDenied(): void;
}
export function UserSettings(props: Props) {
  const { api, onAccessDenied } = props;
  const [selected, setSelected] = useState<Staff | null>(null);
  const [success, setSuccess] = useState("");
  const [dirty, setDirty] = useState(false);
  const [formVersion, setFormVersion] = useState(0);
  function changeDirty(value: boolean) {
    setDirty(value);
    props.onDirty(value);
  }
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.users(signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, onAccessDenied],
  );
  const resource = useResource(`users:${props.profile.subject}`, load);
  return (
    <>
      <div className="section-heading">
        <h2>Users</h2>
        <button
          type="button"
          onClick={resource.refresh}
          disabled={resource.status === "loading" || resource.status === "refreshing"}
        >
          Refresh users
        </button>
      </div>
      <p>
        Only active registered staff can sign in. Roles apply across configured entities. Keep at
        least one active Director.
      </p>
      {success ? <Notice tone="success">{success}</Notice> : null}
      {resource.status === "loading" ? <Loading label="Loading registered staff…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} />
      ) : null}
      <div className="staff-list">
        {resource.data?.map((user) => (
          <article className="card staff-row" key={user.id}>
            <div>
              <h3>{user.displayName}</h3>
              <p>{user.email}</p>
              <p>
                {roleNames[user.role]} · {user.active ? "Active" : "Inactive"}
              </p>
            </div>
            <button
              type="button"
              aria-label={`Edit ${user.displayName}`}
              onClick={() => {
                if (dirty && !window.confirm("Discard unsaved changes to this user?")) return;
                setSelected(user);
                changeDirty(false);
              }}
            >
              Edit user
            </button>
          </article>
        ))}
      </div>
      <UserForm
        key={`${selected?.id ?? "new"}:${formVersion}`}
        {...props}
        onDirty={changeDirty}
        user={selected}
        onCancel={() => {
          setSelected(null);
          setFormVersion((value) => value + 1);
          changeDirty(false);
        }}
        onSaved={(user) => {
          setSuccess(`${user.displayName} saved.`);
          setSelected(null);
          setFormVersion((value) => value + 1);
          changeDirty(false);
          resource.refresh();
          if (user.id === props.profile.subject) props.onAccessDenied();
        }}
        onFailed={resource.refresh}
      />
    </>
  );
}
function UserForm({
  user,
  onSaved,
  onCancel,
  onFailed,
  ...props
}: Props & { user: Staff | null; onSaved(user: Staff): void; onCancel(): void; onFailed(): void }) {
  const [name, setName] = useState(user?.displayName ?? "");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>(user?.role ?? "STANDARD_USER");
  const [active, setActive] = useState(user?.active ?? true);
  const action = useAction(`staff-form:${user?.id ?? "new"}`);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim() || (!user && !email.trim())) return;
    if (
      user &&
      !window.confirm(
        `Save changes for ${user.displayName}? Role: ${roleNames[role]}. Account: ${active ? "Active" : "Inactive"}.${!active ? " This immediately removes dashboard access." : ""}`,
      )
    )
      return;
    const result = await action.run(
      (signal) =>
        user
          ? props.api.updateUser(user.id, { displayName: name.trim(), role, active }, signal)
          : props.api.createUser({ displayName: name.trim(), email: email.trim(), role }, signal),
      (error) => {
        if (isAccessFailure(error)) props.onAccessDenied();
      },
    );
    if (result) onSaved(result);
    else {
      onFailed();
    }
  }
  return (
    <form
      className="card settings-form"
      aria-label={user ? "Edit user" : "Register user"}
      onSubmit={submit}
    >
      <h3>{user ? `Edit ${user.displayName}` : "Register user"}</h3>
      {user ? (
        <p>{user.email}</p>
      ) : (
        <>
          <label htmlFor="user-email">User email</label>
          <input
            id="user-email"
            type="email"
            autoComplete="off"
            maxLength={254}
            required
            value={email}
            disabled={action.busy}
            onChange={(event) => {
              setEmail(event.target.value);
              props.onDirty(true);
            }}
          />
        </>
      )}
      <label htmlFor="user-name">User name</label>
      <input
        id="user-name"
        maxLength={160}
        required
        value={name}
        disabled={action.busy}
        onChange={(event) => {
          setName(event.target.value);
          props.onDirty(true);
        }}
      />
      <label htmlFor="user-role">User role</label>
      <select
        id="user-role"
        value={role}
        disabled={action.busy}
        onChange={(event) => {
          setRole(event.target.value as Role);
          props.onDirty(true);
        }}
      >
        {Object.entries(roleNames).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      {user ? (
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={active}
            disabled={action.busy}
            onChange={(event) => {
              setActive(event.target.checked);
              props.onDirty(true);
            }}
          />
          Account active
        </label>
      ) : null}
      <div className="action-row">
        <button
          type="submit"
          className="primary"
          disabled={action.busy || !name.trim() || (!user && !email.trim())}
        >
          {action.busy ? "Saving…" : user ? "Save user changes" : "Register user"}
        </button>
        <button type="button" disabled={action.busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
      {action.error ? (
        <>
          <Failure error={action.error} />
          <p className="supporting">
            Current user records have been refreshed. Check the active state before retrying an
            interrupted change.
          </p>
        </>
      ) : null}
    </form>
  );
}
