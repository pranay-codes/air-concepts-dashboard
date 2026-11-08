package com.airconcepts.dashboard.invoices;

import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.airconcepts.dashboard.xero.*;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {
  private final XeroConnectionService connections;
  private final XeroInvoiceClient provider;
  private final XeroReadHealth health;
  private final InvoiceAssignmentRepository assignments;
  private final Clock clock;
  public InvoiceService(XeroConnectionService connections, XeroInvoiceClient provider, XeroReadHealth health,
      InvoiceAssignmentRepository assignments, Clock clock) {
    this.connections = connections; this.provider = provider; this.health = health; this.assignments = assignments; this.clock = clock;
  }

  public InvoiceViews.Page list(CurrentUser user, UUID entity, InvoiceQuery query) {
    requireRead(user, query.type());
    return read(user, entity, access -> {
      var page = provider.list(access, query);
      if (query.type() != InvoiceType.ACCREC || page.items().isEmpty()) return page;
      var owners = assignments.owners(entity, page.items().stream().map(InvoiceViews.Summary::id).toList());
      return new InvoiceViews.Page(page.items().stream().map(i -> i.withOwner(owners.get(i.id()))).toList(),
          page.page(), page.pageSize(), page.pageCount(), page.itemCount());
    });
  }

  public InvoiceViews.Detail detail(CurrentUser user, UUID entity, InvoiceType type, UUID id) {
    requireRead(user, type);
    return read(user, entity, access -> {
      var detail = provider.detail(access, type, id);
      if (type != InvoiceType.ACCREC) return detail;
      var owner = assignments.owners(entity, java.util.List.of(id)).get(id);
      return new InvoiceViews.Detail(detail.invoice().withOwner(owner), detail.subtotal(), detail.tax(),
          detail.amountPaid(), detail.amountCredited(), detail.lines(), detail.payments(), detail.taxBasis());
    });
  }

  public InvoiceViews.Assignment assign(CurrentUser user, UUID entity, InvoiceType type, UUID id, UUID target) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    if (type != InvoiceType.ACCREC) throw new org.springframework.security.access.AccessDeniedException("Supplier bills have no assignments.");
    var access = read(user, entity, context -> {
      provider.detail(context, InvoiceType.ACCREC, id); // A client-supplied UUID alone is never proof of a sales invoice.
      return context;
    });
    return new InvoiceViews.Assignment(id, assignments.assign(access, UUID.fromString(user.subject()), id, target));
  }

  public static void requireRead(CurrentUser user, InvoiceType type) {
    if (type == InvoiceType.ACCPAY) user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    else user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
  }

  <T> T readDocument(CurrentUser user, UUID entity, InvoiceType type, UUID id,
      BiFunction<TenantAccess, InvoiceViews.Detail, T> operation) {
    requireRead(user, type);
    return read(user, entity, access -> operation.apply(access, provider.detail(access, type, id)));
  }

  private <T> T read(CurrentUser user, UUID entity, Function<TenantAccess, T> operation) {
    var access = connections.authorisedAccess(user, entity);
    health.check(access);
    var started = clock.instant();
    try {
      var result = operation.apply(access);
      health.success(access, started);
      return result;
    } catch (ProviderFailure failure) {
      try { health.failure(access, started, failure); }
      catch (org.springframework.dao.DataAccessException persistenceFailure) {
        // Preserve the confirmed provider diagnostic even if persistence is unavailable; never retain the raw SQL cause.
        org.slf4j.LoggerFactory.getLogger(InvoiceService.class).error(
            "Xero read failure state could not be recorded. code={} upstreamStatus={}", failure.code(), failure.upstreamStatus());
      }
      throw failure;
    }
  }
}
