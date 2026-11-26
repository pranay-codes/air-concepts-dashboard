package com.airconcepts.dashboard.vwork;

import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Connection changes and status-only audit entries commit together. */
@Repository
public class VworkConnectionRepository {
  public record Stored(String revision, String ciphertext, String status, String errorCode, UUID configuredBy,
      Instant configuredAt, Instant verifiedAt, Instant retryAt) {
    @Override public String toString() { return "VworkConnection[redacted]"; }
  }
  public record Change(String ciphertext, String status, String errorCode, Instant verifiedAt, Instant retryAt) {
    @Override public String toString() { return "VworkConnectionChange[redacted]"; }
  }

  private final JdbcTemplate jdbc;
  public VworkConnectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Stored> read() { return read(false); }

  public void recordRateLimit(String revision, Instant retryAt) {
    jdbc.update("""
        insert into public.vwork_sync_rate_limit(connection_id,connection_revision,retry_at)
        select id,xmin::text,? from public.vwork_connection where id=1 and status='ACTIVE' and xmin::text=?
        on conflict (connection_id) do update set connection_revision=excluded.connection_revision,
          retry_at=case when vwork_sync_rate_limit.connection_revision=excluded.connection_revision
            then greatest(vwork_sync_rate_limit.retry_at,excluded.retry_at) else excluded.retry_at end
        """, offset(retryAt), revision);
  }

  private Optional<Stored> read(boolean lock) {
    return jdbc.query("""
        select c.xmin::text as revision,c.key_ciphertext,c.status,c.error_code,c.configured_by,c.configured_at,c.verified_at,
          greatest(c.retry_at, (select l.retry_at from public.vwork_sync_rate_limit l
            where l.connection_id=c.id and l.connection_revision=c.xmin::text)) as retry_at
        from public.vwork_connection c where c.id=1
        """ + (lock ? " for update of c" : ""), (row, index) -> new Stored(row.getString("revision"),
        row.getString("key_ciphertext"), row.getString("status"), row.getString("error_code"),
        row.getObject("configured_by", UUID.class), instant(row.getObject("configured_at", OffsetDateTime.class)),
        instant(row.getObject("verified_at", OffsetDateTime.class)), instant(row.getObject("retry_at", OffsetDateTime.class))))
        .stream().findFirst();
  }

  @Transactional
  public void change(UUID actor, String expectedRevision, Change change) {
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    var role = jdbc.query("select role from public.staff_users where id=? and active",
        (row, index) -> Role.valueOf(row.getString("role")), actor).stream().findFirst();
    if (role.orElse(null) != Role.DIRECTOR) throw new AccessDeniedException("Director access is required.");
    // Serialise even the first insert, when no singleton row exists to lock.
    jdbc.execute("select pg_advisory_xact_lock(150022)");
    String actual = read(true).map(Stored::revision).orElse(null);
    if (!Objects.equals(expectedRevision, actual)) {
      throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_CONNECTION_CHANGED", "The vWork connection changed. Refresh and try again.");
    }
    jdbc.update("""
        insert into public.vwork_connection(id,key_ciphertext,status,error_code,configured_by,configured_at,verified_at,retry_at,updated_at)
        values(1,?,?,?,?,now(),?,?,now())
        on conflict (id) do update set key_ciphertext=excluded.key_ciphertext,status=excluded.status,
          error_code=excluded.error_code,configured_by=excluded.configured_by,configured_at=excluded.configured_at,
          verified_at=excluded.verified_at,retry_at=excluded.retry_at,updated_at=now()
        """, change.ciphertext(), change.status(), change.errorCode(), actor,
        offset(change.verifiedAt()), offset(change.retryAt()));
    jdbc.update("insert into public.vwork_connection_events(actor_id,status) values (?,?)", actor, change.status());
  }

  private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
  private static OffsetDateTime offset(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
}
