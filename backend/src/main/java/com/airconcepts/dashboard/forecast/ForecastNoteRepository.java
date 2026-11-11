package com.airconcepts.dashboard.forecast;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists internal forecast notes keyed by the stable transaction identity; never reaches Xero. */
@Repository
public class ForecastNoteRepository {
  public record TransactionNote(String source, UUID sourceId, String note, Instant updatedAt) {}

  private final JdbcTemplate jdbc;

  public ForecastNoteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<TransactionNote> notes(UUID entity) {
    return jdbc.query(
        "select source,source_id,note,updated_at from public.forecast_transaction_notes where entity_id=?",
        (r, n) -> new TransactionNote(r.getString("source"), r.getObject("source_id", UUID.class),
            r.getString("note"), r.getTimestamp("updated_at").toInstant()),
        entity);
  }

  /** True when the transaction exists for this entity, so a note can never be attached to another entity's record. */
  public boolean targetExists(UUID entity, String source, UUID sourceId) {
    String sql = switch (source) {
      case "DOCUMENT" -> "select exists(select 1 from public.forecast_documents where entity_id=? and xero_document_id=?)";
      case "MANUAL" -> "select exists(select 1 from public.forecast_manual_items where entity_id=? and id=?)";
      case "STAFFING" -> "select exists(select 1 from public.forecast_staffing where entity_id=? and id=?)";
      default -> throw new IllegalArgumentException("Unknown forecast source");
    };
    Boolean found = jdbc.queryForObject(sql, Boolean.class, entity, sourceId);
    return Boolean.TRUE.equals(found);
  }

  public TransactionNote upsert(UUID entity, String source, UUID sourceId, String note, UUID actor) {
    return jdbc.query(
        "insert into public.forecast_transaction_notes(entity_id,source,source_id,note,created_by,updated_by) values(?,?,?,?,?,?) "
            + "on conflict(entity_id,source,source_id) do update set note=excluded.note,updated_by=excluded.updated_by,updated_at=now() "
            + "returning source,source_id,note,updated_at",
        (r, n) -> new TransactionNote(r.getString("source"), r.getObject("source_id", UUID.class),
            r.getString("note"), r.getTimestamp("updated_at").toInstant()),
        entity, source, sourceId, note, actor).stream().findFirst().orElseThrow();
  }

  public void delete(UUID entity, String source, UUID sourceId) {
    jdbc.update("delete from public.forecast_transaction_notes where entity_id=? and source=? and source_id=?", entity, source, sourceId);
  }
}
