package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class XeroConnectionRepository {
  private final JdbcTemplate jdbc;
  private static final RowMapper<OAuthAttempt> ATTEMPT = (r, n) -> new OAuthAttempt(r.getObject("id", UUID.class),
      r.getObject("director_id", UUID.class), r.getObject("entity_id", UUID.class), r.getObject("grant_id", UUID.class),
      r.getTimestamp("expires_at").toInstant(), OAuthAttempt.State.valueOf(r.getString("status")),
      r.getString("error_code"), r.getObject("upstream_status", Integer.class));

  public XeroConnectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public OAuthAttempt createAttempt(UUID id, UUID director, UUID entity, String hash, Instant expires) {
    int inserted = jdbc.update("insert into public.xero_oauth_attempts(id,director_id,entity_id,state_hash,expires_at,status) "
        + "select ?,?,?,?,?, 'AWAITING_CALLBACK' where exists(select 1 from public.staff_users where id=? and active and role='DIRECTOR')",
        id, director, entity, hash, Timestamp.from(expires), director);
    if (inserted != 1) throw new org.springframework.security.access.AccessDeniedException("Director access is required.");
    return new OAuthAttempt(id, director, entity, null, expires, OAuthAttempt.State.AWAITING_CALLBACK, null, null);
  }

  public Optional<OAuthAttempt> claimAttempt(String hash, Instant now) {
    return jdbc.query("update public.xero_oauth_attempts a set status='PROCESSING' where state_hash=? "
        + "and status='AWAITING_CALLBACK' and expires_at>? and exists(select 1 from public.staff_users u "
        + "where u.id=a.director_id and u.active and u.role='DIRECTOR') returning a.*",
        ATTEMPT, hash, Timestamp.from(now)).stream().findFirst();
  }

  public Optional<OAuthAttempt> ownedAttempt(UUID id, UUID director) {
    return jdbc.query("select * from public.xero_oauth_attempts where id=? and director_id=?", ATTEMPT, id, director).stream().findFirst();
  }

  @Transactional
  public void saveGrant(OAuthAttempt attempt, StoredGrant grant, Instant now) {
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values(?,?,?,?,'ACTIVE')",
        grant.id(), grant.accessCiphertext(), grant.refreshCiphertext(), Timestamp.from(grant.expiresAt()));
    int updated = jdbc.update("update public.xero_oauth_attempts set status='READY', grant_id=? where id=? "
        + "and status='PROCESSING' and expires_at>?", grant.id(), attempt.id(), Timestamp.from(now));
    if (updated != 1) throw conflict("The authorisation attempt expired. Connect Xero again.");
  }

  public void failAttempt(UUID id, String code, Integer upstreamStatus) {
    jdbc.update("update public.xero_oauth_attempts set status='FAILED', error_code=?, upstream_status=? where id=? and status='PROCESSING'",
        code, upstreamStatus, id);
  }

  public StoredGrant lockGrant(UUID id) {
    return jdbc.query("select * from public.xero_grants where id=? for update", (r, n) -> new StoredGrant(
        r.getObject("id", UUID.class), r.getString("access_ciphertext"), r.getString("refresh_ciphertext"),
        r.getTimestamp("expires_at").toInstant(), GrantState.valueOf(r.getString("status")),
        instant(r, "refresh_pending_since"), instant(r, "retry_at"), r.getString("error_code")), id)
        .stream().findFirst().orElseThrow(() -> conflict("This Xero grant is unavailable. Connect Xero again."));
  }

  public void saveRotated(StoredGrant grant) {
    jdbc.update("update public.xero_grants set access_ciphertext=?,refresh_ciphertext=?,expires_at=?,status='ACTIVE',"
        + "refresh_pending_since=null,retry_at=null,error_code=null,updated_at=now() where id=?",
        grant.accessCiphertext(), grant.refreshCiphertext(), Timestamp.from(grant.expiresAt()), grant.id());
  }

  public void recordGrantFailure(UUID id, GrantState state, String code, Instant pendingSince, Instant retryAt) {
    jdbc.update("update public.xero_grants set status=?,error_code=?,refresh_pending_since=?,retry_at=?,updated_at=now() where id=?",
        state.name(), code, timestamp(pendingSince), timestamp(retryAt), id);
  }

  public void clearReadFailure(UUID id, Instant requestStarted, Instant now) {
    jdbc.update("update public.xero_grants set error_code=null,retry_at=null where id=? and status='ACTIVE' "
        + "and updated_at<=? and (retry_at is null or retry_at<=?) and (error_code is not null or retry_at is not null)",
        id, Timestamp.from(requestStarted), Timestamp.from(now));
  }

  public Optional<StoredConnection> connection(UUID entity) {
    return jdbc.query("select c.*,case when c.read_upstream_status in (401,403) then 'NEEDS_RECONNECTION' else g.status end as status,"
        + "coalesce(g.error_code,c.read_error_code) as error_code from public.xero_entity_connections c join public.xero_grants g on g.id=c.grant_id where c.entity_id=?",
        (r, n) -> new StoredConnection(r.getObject("entity_id", UUID.class), r.getObject("grant_id", UUID.class),
            r.getObject("tenant_id", UUID.class), r.getString("tenant_name"), r.getTimestamp("verified_at").toInstant(),
            GrantState.valueOf(r.getString("status")), r.getString("error_code")), entity).stream().findFirst();
  }

  @Transactional
  public void completeMapping(OAuthAttempt attempt, XeroTenant selected, List<XeroTenant> authorised, Instant now) {
    // Serialise mapping changes across grants as well as entities; never let two Directors claim the same tenant.
    jdbc.execute("select pg_advisory_xact_lock(150003)");
    var current = jdbc.query("select * from public.xero_oauth_attempts where id=? and director_id=? for update",
        ATTEMPT, attempt.id(), attempt.directorId()).stream().findFirst().orElseThrow(() -> conflict("Authorisation is unavailable."));
    if (current.state() != OAuthAttempt.State.READY || !current.expiresAt().isAfter(now)
        || !Objects.equals(current.grantId(), attempt.grantId()) || !current.entityId().equals(attempt.entityId())) {
      throw conflict("The authorisation attempt changed or expired. Connect Xero again.");
    }
    Integer directors = jdbc.queryForObject("select count(*) from public.staff_users where id=? and active and role='DIRECTOR'", Integer.class, attempt.directorId());
    if (directors == null || directors != 1) throw new org.springframework.security.access.AccessDeniedException("Director access is required.");
    Integer duplicate = jdbc.queryForObject("select count(*) from public.xero_entity_connections where tenant_id=? and entity_id<>?",
        Integer.class, selected.tenantId(), attempt.entityId());
    if (duplicate != null && duplicate > 0) throw conflict("This Xero organisation is already mapped to another entity.");
    jdbc.update("insert into public.xero_entity_connections(entity_id,grant_id,tenant_id,connection_id,tenant_name,verified_at) values(?,?,?,?,?,?) "
        + "on conflict(entity_id) do update set grant_id=excluded.grant_id,tenant_id=excluded.tenant_id,connection_id=excluded.connection_id,"
        + "tenant_name=excluded.tenant_name,verified_at=excluded.verified_at,read_error_code=null,read_upstream_status=null,"
        + "read_retry_at=null,read_updated_at=null", attempt.entityId(), attempt.grantId(), selected.tenantId(),
        selected.connectionId(), selected.name(), Timestamp.from(now));
    for (var tenant : authorised) {
      jdbc.update("update public.xero_entity_connections set grant_id=?,connection_id=?,tenant_name=?,verified_at=?,"
          + "read_error_code=null,read_upstream_status=null,read_retry_at=null,read_updated_at=null where tenant_id=?",
          attempt.grantId(), tenant.connectionId(), tenant.name(), Timestamp.from(now), tenant.tenantId());
    }
    jdbc.update("update public.xero_oauth_attempts set status='COMPLETED' where id=?", attempt.id());
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column); return value == null ? null : value.toInstant();
  }
  private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
  private static ApiFailure conflict(String detail) { return new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CONFLICT", detail); }
}
