import { useRef, useState } from "react";
import type { Entity } from "../api/contracts";

export function EntitySelector({
  entities,
  selectedId,
  onSelect,
}: {
  entities: Entity[];
  selectedId: string | null;
  onSelect(id: string): void;
}) {
  const [search, setSearch] = useState("");
  const control = useRef<HTMLSelectElement>(null);
  const current = entities.find((entity) => entity.id === selectedId);
  const matches = entities.filter((entity) =>
    entity.displayName
      .toLocaleLowerCase("en-NZ")
      .includes(search.trim().toLocaleLowerCase("en-NZ")),
  );
  return (
    <div className="entity-selector">
      <label htmlFor="entity-select">Entity</label>
      {entities.length > 1 ? (
        <>
          <label htmlFor="entity-search" className="visually-hidden">
            Find entity
          </label>
          <input
            id="entity-search"
            type="search"
            placeholder="Find entity"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            autoComplete="off"
          />
        </>
      ) : null}
      <select
        id="entity-select"
        ref={control}
        value={current?.id ?? ""}
        disabled={!entities.length}
        onChange={(event) => {
          const id = event.target.value;
          if (!entities.some((entity) => entity.id === id)) return;
          onSelect(id);
          setSearch("");
          control.current?.focus();
        }}
      >
        {!current ? (
          <option value="">{entities.length ? "Choose an entity" : "No entities available"}</option>
        ) : null}
        {current && !matches.some((entity) => entity.id === current.id) ? (
          <option value={current.id} disabled>
            {current.displayName} (Current selection)
          </option>
        ) : null}
        {matches.map((entity) => (
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
      {search && !matches.length ? (
        <p className="supporting" role="status">
          No entities match this search.
        </p>
      ) : null}
    </div>
  );
}
