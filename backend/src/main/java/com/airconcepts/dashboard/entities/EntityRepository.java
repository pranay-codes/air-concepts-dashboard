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

  public List<Entity> findAll() {
    return jdbc.query("select id, display_name from public.entities order by lower(display_name), id", ROW);
  }

  public Optional<Entity> findById(UUID id) {
    return jdbc.query("select id, display_name from public.entities where id = ?", ROW, id)
        .stream().findFirst();
  }

  public Entity create(String displayName) {
    UUID id = UUID.randomUUID();
    // Internal code is generated, not derived from a mutable business name or a fixed enum.
    jdbc.update("insert into public.entities(id, code, display_name) values (?, ?, ?)",
        id, "entity-" + id, displayName);
    return new Entity(id, displayName);
  }
}
