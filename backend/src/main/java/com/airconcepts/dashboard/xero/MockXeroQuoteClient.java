package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** Tenant-scoped synthetic quote source used only in explicit local/test mock mode. */
public final class MockXeroQuoteClient extends XeroQuoteClient {
  private final Map<UUID,LinkedHashMap<UUID,Quote>> rows=new HashMap<>();
  public MockXeroQuoteClient(RestClient unused, Clock clock, XeroForecastClient.Pacer pacer) { super(unused,clock,pacer); }
  public synchronized void seed(UUID tenant, Quote quote) {
    Objects.requireNonNull(tenant); Objects.requireNonNull(quote); Objects.requireNonNull(quote.id());
    rows.computeIfAbsent(tenant,ignored -> new LinkedHashMap<>()).put(quote.id(),quote);
  }
  @Override public synchronized List<Quote> page(TenantAccess access, int page, Status status, Instant modifiedSince) {
    if (page<1) throw new IllegalArgumentException("Page must be positive");
    var matches=rows.getOrDefault(access.tenantId(),new LinkedHashMap<>()).values().stream()
        .filter(q -> status==null || q.status()==status)
        .filter(q -> modifiedSince==null || (q.updatedAt()!=null && !q.updatedAt().isBefore(modifiedSince))).toList();
    long offset=(long)(page-1)*100;
    int start=(int)Math.min(matches.size(),offset);
    return List.copyOf(matches.subList(start,Math.min(matches.size(),start+100)));
  }
  @Override public synchronized Quote detail(TenantAccess access, UUID quoteId) {
    var quote=rows.getOrDefault(access.tenantId(),new LinkedHashMap<>()).get(quoteId);
    if (quote==null) throw new ApiFailure(HttpStatus.NOT_FOUND,"QUOTE_NOT_FOUND","The test quote is unavailable.");
    return quote;
  }
}
