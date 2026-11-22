package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceDraftServiceTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),quoteId=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  private final InvoiceDraftContext contexts=mock(InvoiceDraftContext.class);
  private final XeroConnectionService connections=mock(XeroConnectionService.class);
  private final XeroQuoteClient quotes=mock(XeroQuoteClient.class);
  private final TenantAccess access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
  private final InvoiceDraftContext.Context context=new InvoiceDraftContext.Context(quoteId,"Q-1","J-1",4,tenant,grant,"Example Xero organisation");
  private final InvoiceDraftService service=new InvoiceDraftService(contexts,connections,quotes,Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC));
  private void setup() {
    when(contexts.load(user,entity,item)).thenReturn(context);
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    var line=new XeroQuoteClient.Line("Work",BigDecimal.ONE,new BigDecimal("123.4567"),null,null,null,null,null,null,List.of());
    when(quotes.detail(access,quoteId)).thenReturn(new XeroQuoteClient.Quote(quoteId,"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,
        UUID.randomUUID(),"Example customer",null,null,Instant.EPOCH,new BigDecimal("123.4567"),"NZD","Exclusive",List.of(line)));
  }
  @Test void gl17_returnsFinanceReviewFromTheAuthorisedOrganisation() {
    setup();
    var review=service.review(user,entity,item);
    assertThat(review.organisationName()).isEqualTo("Example Xero organisation");
    assertThat(review.reference()).isEqualTo("J-1 / Q-1");
    assertThat(review.total()).isEqualTo("123.4567");
    assertThat(review.lineCount()).isEqualTo(1);
    assertThat(review.fingerprint()).matches("[0-9a-f]{64}");
    verify(contexts,times(2)).load(user,entity,item);
  }
  @Test void gl06_tenantChangeIsRejectedBeforeQuoteRead() {
    setup();
    when(connections.authorisedAccess(user,entity)).thenReturn(new TenantAccess(entity,UUID.randomUUID(),grant,"synthetic",Set.of("accounting.invoices.read")));
    assertThatThrownBy(() -> service.review(user,entity,item)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(quotes);
  }
  @Test void gl06_changedItemAfterReadCannotBeReviewed() {
    setup();
    when(contexts.load(user,entity,item)).thenReturn(context,new InvoiceDraftContext.Context(quoteId,"Q-1","J-2",5,tenant,grant,"Example Xero organisation"));
    assertThatThrownBy(() -> service.review(user,entity,item)).isInstanceOf(ApiFailure.class);
  }
}
