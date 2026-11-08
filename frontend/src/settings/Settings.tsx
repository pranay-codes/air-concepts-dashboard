import { useState } from "react";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { Notice } from "../ui/Feedback";
import { EntitySettings } from "./EntitySettings";
import { UserSettings } from "./UserSettings";

export interface SettingsProps {
  api: DashboardApi;
  profile: Profile;
  entities: Entity[];
  onDirty(value: boolean): void;
  onEntitiesChanged(): void;
  onAccessDenied(): void;
  openAuthorization?(url: string): void;
}
export default function Settings(props: SettingsProps) {
  if (props.profile.role !== "DIRECTOR")
    return <Notice tone="warning">Only a Director can manage entities and users.</Notice>;
  return <DirectorSettings {...props} />;
}
function DirectorSettings(props: SettingsProps) {
  const [tab, setTab] = useState<"entities" | "users">("entities");
  const [dirty, setDirty] = useState(false);
  function changeDirty(value: boolean) {
    setDirty(value);
    props.onDirty(value);
  }
  return (
    <section aria-labelledby="settings-title">
      <h1 id="settings-title">Settings</h1>
      <div className="action-row settings-tabs" aria-label="Settings sections">
        {(["entities", "users"] as const).map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={tab === value}
            onClick={() => {
              if (
                dirty &&
                !window.confirm("Discard unsaved changes before switching Settings section?")
              )
                return;
              changeDirty(false);
              setTab(value);
            }}
          >
            {value === "entities" ? "Entities" : "Users"}
          </button>
        ))}
      </div>
      {tab === "entities" ? (
        <EntitySettings {...props} onDirty={changeDirty} />
      ) : (
        <UserSettings
          api={props.api}
          profile={props.profile}
          onDirty={changeDirty}
          onAccessDenied={props.onAccessDenied}
        />
      )}
    </section>
  );
}
