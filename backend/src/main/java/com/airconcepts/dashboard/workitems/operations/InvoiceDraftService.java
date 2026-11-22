package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Read-only review built from the current server-authorised quote. */
@Service
public class InvoiceDraftService {
  @Schema(name="InvoiceDraftReview",requiredProperties={"entityId","itemId","version","quoteNumber","organisationName","customer","reference","lineCount","total","currency","fingerprint"})
  public record Review(UUID entityId, UUID itemId, int version, String quoteNumber, String organisationName,
      String customer, String reference, int lineCount, String total, String currency, String fingerprint) {
    @Override public String toString() { return "InvoiceDraftReview[redacted]"; }
  }
  public record Prepared(InvoiceDraftContext.Context context, TenantAccess access, DraftInvoice draft) {
    @Override public String toString() { return "PreparedInvoice[redacted]"; }
  }
  private final InvoiceDraftContext contexts;
  private final XeroConnectionService connections;
  private final XeroQuoteClient quotes;
  private final Clock clock;
  public InvoiceDraftService(InvoiceDraftContext contexts, XeroConnectionService connections, XeroQuoteClient quotes, Clock clock) {
    this.contexts=contexts; this.connections=connections; this.quotes=quotes; this.clock=clock;
  }
  public Review review(CurrentUser user, UUID entity, UUID item) {
    var prepared=prepare(user,entity,item);
    var context=prepared.context();
    var draft=prepared.draft();
    return new Review(entity,item,context.version(),draft.quoteNumber(),context.organisationName(),draft.customer(),draft.reference(),
        draft.lines().size(),draft.quoteTotal().toPlainString(),draft.currency(),draft.fingerprint());
  }
  public Prepared prepare(CurrentUser user, UUID entity, UUID item) {
    var context=contexts.load(user,entity,item);
    var access=check(context,entity,connections.authorisedAccess(user,entity));
    var quote=quotes.detail(access,context.quoteId());
    if (!context.quoteId().equals(quote.id()) || !context.quoteNumber().equals(quote.number())
        || (quote.status()!=XeroQuoteClient.Status.ACCEPTED && quote.status()!=XeroQuoteClient.Status.INVOICED)) throw changed();
    var draft=DraftInvoice.fromQuote(quote,context.jobId(),BusinessTime.today(clock));
    var current=check(context,entity,connections.authorisedAccess(user,entity));
    if (!context.equals(contexts.load(user,entity,item))) throw changed();
    return new Prepared(context,current,draft);
  }
  private static TenantAccess check(InvoiceDraftContext.Context context, UUID entity, TenantAccess access) {
    if (access==null || !entity.equals(access.entityId()) || !context.tenantId().equals(access.tenantId())
        || !context.grantId().equals(access.grantId())) throw changed();
    XeroScopes.require(access.grantedScopes(),XeroScopes.QUOTES_READ);
    return access;
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"WORK_ITEM_CHANGED","The quote or Xero connection changed. Refresh and try again."); }
}
