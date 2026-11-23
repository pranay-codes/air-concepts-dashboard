package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroConnectionService;
import com.airconcepts.dashboard.vwork.VworkConnectionService;
import com.airconcepts.dashboard.vwork.VworkFailure;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/** Coordinates entity-scoped sync and records failures without advancing its successful state. */
public class GreenLineSyncService {
  private final GreenLineSyncRunRepository repository;
  private final QuoteSync quotes;
  private final XeroConnectionService connections;
  private final EntityService entities;
  private final Clock clock;
  private final VworkSync vwork;
  private final VworkConnectionService vworkConnections;
  private final InvoiceSync invoices;
  public GreenLineSyncService(GreenLineSyncRunRepository repository, QuoteSync quotes, XeroConnectionService connections,
      EntityService entities, Clock clock) {
    this(repository, quotes, connections, entities, clock, null, null, null);
  }
  public GreenLineSyncService(GreenLineSyncRunRepository repository, QuoteSync quotes, XeroConnectionService connections,
      EntityService entities, Clock clock, VworkSync vwork, VworkConnectionService vworkConnections) {
    this(repository,quotes,connections,entities,clock,vwork,vworkConnections,null);
  }
  public GreenLineSyncService(GreenLineSyncRunRepository repository, QuoteSync quotes, XeroConnectionService connections,
      EntityService entities, Clock clock, VworkSync vwork, VworkConnectionService vworkConnections, InvoiceSync invoices) {
    this.repository = repository; this.quotes = quotes; this.connections = connections; this.entities = entities; this.clock = clock;
    this.vwork = vwork; this.vworkConnections = vworkConnections;
    this.invoices = invoices;
  }

  public UUID runManual(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    entities.get(user, entity);
    UUID actor;
    try { actor = UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    if (user.role() == Role.STANDARD_USER && !repository.isServiceManager(entity, actor))
      throw new AccessDeniedException("Service Manager access is required.");
    return runBoth(entity, "MANUAL", actor, () -> connections.authorisedAccess(user, entity));
  }

  public GreenLineSyncViews.Status status(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    entities.get(user, entity);
    return repository.status(entity);
  }

  public void runScheduled() {
    for (UUID entity : repository.enabledEntities()) {
      try { runBoth(entity, "SCHEDULED", null, () -> connections.systemAccess(entity)); }
      catch (RuntimeException failure) { /* Safe run diagnostics are persisted when the run has started. */ }
    }
  }

  private UUID runBoth(UUID entity, String trigger, UUID actor, Supplier<TenantAccess> resolve) {
    UUID xeroRun = null;
    ApiFailure xeroFailure = null, vworkFailure = null;
    try { xeroRun = run(entity, trigger, actor, resolve); }
    catch (ApiFailure failure) {
      if ("SYNC_IN_PROGRESS".equals(failure.code()) || "SYNC_RATE_LIMITED".equals(failure.code())
          || "GREEN_LINE_DISABLED".equals(failure.code())) throw failure;
      xeroFailure = failure;
    }
    if (vwork != null && vworkConnections != null) {
      try { runVwork(entity, trigger, actor); }
      catch (ApiFailure failure) { vworkFailure = failure; }
    }
    if (xeroFailure != null) throw xeroFailure;
    if (vworkFailure != null) throw vworkFailure;
    return xeroRun;
  }

  private void runVwork(UUID entity, String trigger, UUID actor) {
    UUID run = repository.beginVwork(entity, trigger, actor, clock.instant());
    try {
      var access = vworkConnections.systemAccess();
      var result = vwork.run(entity, vworkConnections::systemAccess);
      repository.succeedVwork(entity, run, access, result, clock.instant());
    } catch (ApiFailure failure) {
      repository.failVwork(entity, run, failure.code(), null, clock.instant());
      throw failure;
    } catch (VworkFailure failure) {
      String code = failure.code() != null && failure.code().matches("[A-Z0-9_]{1,80}") ? failure.code() : "VWORK_PROVIDER_FAILED";
      Integer upstream = failure.upstreamStatus() >= 100 && failure.upstreamStatus() <= 599 ? failure.upstreamStatus() : null;
      repository.failVwork(entity, run, code, upstream, clock.instant());
      throw new ApiFailure(HttpStatus.BAD_GATEWAY, code, "vWork refresh did not complete. Check the connection and retry.");
    } catch (RuntimeException failure) {
      repository.failVwork(entity, run, "VWORK_SYNC_FAILED", null, clock.instant());
      throw new ApiFailure(HttpStatus.INTERNAL_SERVER_ERROR, "VWORK_SYNC_FAILED", "vWork refresh did not complete. Check history and retry.");
    }
  }

  private UUID run(UUID entity, String trigger, UUID actor, Supplier<TenantAccess> resolve) {
    UUID run = repository.begin(entity, trigger, actor, clock.instant());
    try {
      var access = resolve.get();
      ApiFailure invoiceFailure = null;
      if (invoices != null) {
        try { invoices.run(entity,resolve); }
        catch (ApiFailure failure) {
          if (!"INVOICE_SYNC_PARTIAL".equals(failure.code())) throw failure;
          invoiceFailure = failure;
        }
      }
      var result = quotes.run(entity, resolve, null);
      if (invoiceFailure != null) throw invoiceFailure;
      repository.succeed(entity, run, access, result, clock.instant());
      return run;
    } catch (ApiFailure failure) {
      Integer upstream = failure instanceof ProviderFailure p && p.upstreamStatus() >= 100 ? p.upstreamStatus() : null;
      repository.fail(entity, run, failure.code(), upstream, clock.instant());
      throw failure;
    } catch (RuntimeException failure) {
      repository.fail(entity, run, "GREEN_LINE_SYNC_FAILED", null, clock.instant());
      throw new ApiFailure(HttpStatus.INTERNAL_SERVER_ERROR, "GREEN_LINE_SYNC_FAILED", "Sync did not complete. Check history and retry.");
    }
  }
}
