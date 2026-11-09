package com.airconcepts.dashboard.invoices;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InvoiceAssignmentRepository {
  private final JdbcTemplate jdbc;
  public InvoiceAssignmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<UUID, InvoiceViews.Owner> owners(UUID entity, List<UUID> invoices) {
    if (invoices.isEmpty()) return Map.of();
    if (invoices.size() > 100) throw new IllegalArgumentException("Ownership lookup exceeds page limit");
    var parameters = new ArrayList<Object>(); parameters.add(entity); parameters.addAll(invoices);
    var result = new HashMap<UUID, InvoiceViews.Owner>();
    jdbc.query("select a.invoice_id,u.id,u.display_name,u.active from public.invoice_assignments a "
        + "join public.staff_users u on u.id=a.staff_id where a.entity_id=? and a.invoice_id in ("
        + String.join(",", Collections.nCopies(invoices.size(), "?")) + ")", r -> {
          result.put(r.getObject("invoice_id", UUID.class), new InvoiceViews.Owner(r.getObject("id", UUID.class),
              r.getString("display_name"), r.getBoolean("active")));
        }, parameters.toArray());
    return Map.copyOf(result);
  }

  @Transactional
  public InvoiceViews.Owner assign(TenantAccess access, UUID actor, UUID invoice, UUID target) {
    // Same lock as staff administration: target deactivation and actor demotion cannot race this commit.
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    Integer authorised = jdbc.queryForObject("select count(*) from public.staff_users where id=? and active "
        + "and role in ('DIRECTOR','FINANCIAL_ADMIN')", Integer.class, actor);
    if (authorised == null || authorised != 1) throw new AccessDeniedException("Assignment access is required.");
    var mapping = jdbc.query("select entity_id from public.xero_entity_connections where entity_id=? and grant_id=? and tenant_id=? for share",
        (r, n) -> r.getObject("entity_id", UUID.class), access.entityId(), access.grantId(), access.tenantId());
    if (mapping.isEmpty()) throw new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CHANGED", "The connection changed. Refresh the invoice before assigning it.");
    InvoiceViews.Owner owner = null;
    if (target != null) {
      owner = jdbc.query("select id,display_name,active from public.staff_users where id=? and active",
          (r, n) -> new InvoiceViews.Owner(r.getObject("id", UUID.class), r.getString("display_name"), r.getBoolean("active")), target)
          .stream().findFirst().orElseThrow(() -> new ApiFailure(HttpStatus.CONFLICT, "ASSIGNEE_UNAVAILABLE", "Choose a currently active dashboard user."));
    }
    jdbc.update("insert into public.invoice_assignments(entity_id,invoice_id,staff_id,assigned_by) values(?,?,?,?) "
        + "on conflict(entity_id,invoice_id) do update set staff_id=excluded.staff_id,assigned_by=excluded.assigned_by,updated_at=now()",
        access.entityId(), invoice, target, actor);
    return owner;
  }
}
