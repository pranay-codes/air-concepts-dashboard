package com.airconcepts.dashboard.entities;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class EntityRepository {
  private static final RowMapper<Entity> ROW = (row, index) ->
      new Entity(row.getObject("id", UUID.class), row.getString("display_name"));
  private final JdbcTemplate jdbc;

  public EntityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Archived entities are excluded from every read the API exposes. */
  public List<Entity> findAll() {
    return jdbc.query("select id, display_name from public.entities where archived_at is null order by lower(display_name), id", ROW);
  }

  public Optional<Entity> findById(UUID id) {
    return jdbc.query("select id, display_name from public.entities where id = ? and archived_at is null", ROW, id)
        .stream().findFirst();
  }

  public Entity create(String displayName) {
    UUID id = UUID.randomUUID();
    // Internal code is generated, not derived from a mutable business name or a fixed enum.
    jdbc.update("insert into public.entities(id, code, display_name) values (?, ?, ?)",
        id, "entity-" + id, displayName);
    return new Entity(id, displayName);
  }

  public Optional<Entity> rename(UUID id, String displayName) {
    return jdbc.query("update public.entities set display_name = ? where id = ? and archived_at is null "
        + "returning id, display_name", ROW, displayName, id).stream().findFirst();
  }

  /** Returns true only for the call that changes an active entity to archived. */
  public boolean archive(UUID id) {
    return jdbc.update("update public.entities set archived_at = now() where id = ? and archived_at is null", id) == 1;
  }

  /** Includes archived entities, so a repeated archive request can be recognised as already complete. */
  public boolean exists(UUID id) {
    Boolean found = jdbc.queryForObject("select exists(select 1 from public.entities where id = ?)", Boolean.class, id);
    return Boolean.TRUE.equals(found);
  }
}
