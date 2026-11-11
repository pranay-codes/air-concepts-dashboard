package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.forecast.ForecastNoteRepository.TransactionNote;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Runs the repository's SQL through a recording JdbcTemplate, without a database. Each statement is
 * checked so that every placeholder receives exactly one bound value; a short argument list fails
 * on Postgres and was previously reported to the user as a record conflict.
 */
class ForecastNoteRepositoryTest {
  private final UUID entity = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final Instant savedAt = Instant.parse("2026-11-12T01:00:00Z");

  @Test
  void upsert_bindsOneValuePerPlaceholderAndBothAuditColumnsToTheActor() throws Exception {
    var jdbc = new RecordingJdbcTemplate(noteRow("Chase by phone", savedAt));
    var saved = new ForecastNoteRepository(jdbc).upsert(entity, "DOCUMENT", item, "Chase by phone", actor);

    assertThat(placeholders(jdbc.sql)).as("placeholders in: " + jdbc.sql).isEqualTo(jdbc.args.length);
    assertThat(jdbc.args).containsExactly(entity, "DOCUMENT", item, "Chase by phone", actor, actor);
    assertThat(jdbc.sql).contains("on conflict(entity_id,source,source_id)");
    assertThat(saved).isEqualTo(new TransactionNote("DOCUMENT", item, "Chase by phone", savedAt));
  }

  @Test
  void upsert_updateDoesNotRewriteTheOriginalCreator() {
    var jdbc = new RecordingJdbcTemplate(noteRow("Second edit", savedAt));
    new ForecastNoteRepository(jdbc).upsert(entity, "MANUAL", item, "Second edit", actor);

    assertThat(jdbc.sql).contains("do update set note=excluded.note,updated_by=excluded.updated_by,updated_at=now()");
    assertThat(jdbc.sql).doesNotContain("created_by=excluded");
  }

  @Test
  void delete_removesOnlyTheNoteForThatTransactionInTheEntity() {
    var jdbc = new RecordingJdbcTemplate(null);
    new ForecastNoteRepository(jdbc).delete(entity, "STAFFING", item);

    assertThat(placeholders(jdbc.updateSql)).isEqualTo(jdbc.updateArgs.length);
    assertThat(jdbc.updateArgs).containsExactly(entity, "STAFFING", item);
    assertThat(jdbc.updateSql).contains("where entity_id=? and source=? and source_id=?");
  }

  private static long placeholders(String sql) {
    return sql.chars().filter(c -> c == '?').count();
  }

  private ResultSet noteRow(String note, Instant updatedAt) {
    try {
      var row = mock(ResultSet.class);
      when(row.getString("source")).thenReturn("DOCUMENT");
      when(row.getObject("source_id", UUID.class)).thenReturn(item);
      when(row.getString("note")).thenReturn(note);
      when(row.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));
      return row;
    } catch (Exception impossible) {
      throw new AssertionError(impossible);
    }
  }

  /** Records the SQL and arguments of each call. Query results come from the supplied row, when given. */
  private static final class RecordingJdbcTemplate extends JdbcTemplate {
    private final ResultSet row;
    String sql;
    Object[] args = new Object[0];
    String updateSql;
    Object[] updateArgs = new Object[0];

    RecordingJdbcTemplate(ResultSet row) {
      this.row = row;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      this.sql = sql;
      this.args = args;
      try {
        return List.of(rowMapper.mapRow(row, 0));
      } catch (Exception failure) {
        throw new AssertionError("Row mapping failed for test fixture", failure);
      }
    }

    @Override
    public int update(String sql, Object... args) {
      this.updateSql = sql;
      this.updateArgs = args;
      return 1;
    }
  }
}
