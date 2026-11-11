import { useRef } from "react";
import type { Entity } from "../api/contracts";

export function EntitySelector({
  entities,
  selectedId,
  onSelect,
  allowAll = false,
}: {
  entities: Entity[];
  selectedId: string | null;
  onSelect(id: string): void;
  allowAll?: boolean;
}) {
  const control = useRef<HTMLSelectElement>(null);
  const current = entities.find((entity) => entity.id === selectedId);
  return (
    <div className="entity-selector">
      <label htmlFor="entity-select">Entity</label>
      <select
        id="entity-select"
        ref={control}
        value={allowAll && selectedId === "__all__" ? "__all__" : (current?.id ?? "")}
        disabled={!entities.length}
        onChange={(event) => {
          const id = event.target.value;
          if (!(allowAll && id === "__all__") && !entities.some((entity) => entity.id === id))
            return;
          onSelect(id);
          control.current?.focus();
        }}
      >
        {allowAll ? <option value="__all__">All entities</option> : null}
        {!current && selectedId !== "__all__" ? (
          <option value="">{entities.length ? "Choose an entity" : "No entities available"}</option>
        ) : null}
        {entities.map((entity) => (
          <option key={entity.id} value={entity.id}>
            {entity.displayName}
          </option>
        ))}
      </select>
      {current ? (
        <p className="entity-current">
          <strong>{current.displayName}</strong>
        </p>
      ) : null}
    </div>
  );
}
