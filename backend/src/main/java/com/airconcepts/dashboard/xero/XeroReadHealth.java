package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.*;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable per-mapping read failures. Conditional updates cannot poison a replacement connection. */
@Repository
public class XeroReadHealth {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  public XeroReadHealth(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

  public void check(TenantAccess access) {
    var rows = jdbc.query("select read_error_code,read_upstream_status,read_retry_at from public.xero_entity_connections "
        + "where entity_id=? and grant_id=? and tenant_id=?", (r, n) -> {
      String error = r.getString("read_error_code");
      Timestamp retry = r.getTimestamp("read_retry_at");
      Integer wait = retry == null ? null : secondsUntil(retry.toInstant());
      int status = r.getInt("read_upstream_status");
      if (wait != null && wait > 0) throw new ProviderFailure("XERO_RATE_LIMITED", "Xero has limited requests. Wait before retrying.", 429, wait);
      if (status == 401 || status == 403) throw new ApiFailure(HttpStatus.CONFLICT, error,
          "Ask a Director to reconnect this organisation in Settings.");
      return true;
    }, access.entityId(), access.grantId(), access.tenantId());
    if (rows.isEmpty()) throw changed();
  }

  public void success(TenantAccess access, Instant started) {
    int changed = jdbc.update("update public.xero_entity_connections set read_updated_at=case when read_error_code is not null "
        + "then ? else read_updated_at end,read_error_code=null,read_upstream_status=null,"
        + "read_retry_at=null where entity_id=? and grant_id=? and tenant_id=? and verified_at<=? "
        + "and (read_updated_at is null or read_updated_at<?) and (read_retry_at is null or read_retry_at<=?) "
        + "and (read_upstream_status is null or read_upstream_status not in (401,403))",
        Timestamp.from(clock.instant()), access.entityId(), access.grantId(), access.tenantId(), Timestamp.from(started),
        Timestamp.from(started), Timestamp.from(clock.instant()));
    if (changed == 0) throw changed();
  }

  public void failure(TenantAccess access, Instant started, ProviderFailure failure) {
    Integer seconds = failure.retryAfterSeconds();
    // Missing Retry-After on 429 still gets a conservative bounded pause, never a tight retry loop.
    Instant retry = failure.upstreamStatus() == 429 ? clock.instant().plusSeconds(seconds == null ? 60 : seconds) : null;
    jdbc.update("update public.xero_entity_connections set read_error_code=?,read_upstream_status=?,read_retry_at=?,read_updated_at=? "
        + "where entity_id=? and grant_id=? and tenant_id=? and verified_at<=? and (read_updated_at is null or read_updated_at<?)",
        failure.code(), failure.upstreamStatus(), retry == null ? null : Timestamp.from(retry), Timestamp.from(clock.instant()),
        access.entityId(), access.grantId(), access.tenantId(), Timestamp.from(started), Timestamp.from(started));
  }

  private int secondsUntil(Instant target) {
    var remaining = Duration.between(clock.instant(), target);
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0)));
  }
  private static ApiFailure changed() {
    return new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CHANGED", "The connection or request state changed. Refresh and try again.");
  }
}
