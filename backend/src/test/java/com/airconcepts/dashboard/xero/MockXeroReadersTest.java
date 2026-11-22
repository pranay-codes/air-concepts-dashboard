package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MockXeroReadersTest {
  @Test void gl01_syntheticQuoteAndContactAreScopedToTheirTenant() {
    var clock=Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC);
    var quotes=new MockXeroQuoteClient(RestClient.builder().build(),clock,tenant -> {});
    var contacts=new MockXeroContactClient(RestClient.builder().build(),clock,tenant -> {});
    var first=new TenantAccess(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"synthetic");
    var second=new TenantAccess(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"synthetic");
    var quoteId=UUID.randomUUID(); var contactId=UUID.randomUUID();
    var line=new XeroQuoteClient.Line("Install",BigDecimal.ONE,BigDecimal.TEN,null,null,null,"200","OUTPUT2",null,List.of());
    var quote=new XeroQuoteClient.Quote(quoteId,"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,contactId,"Customer",
        LocalDate.of(2026,11,21),null,clock.instant(),BigDecimal.TEN,"NZD","Exclusive",List.of(line));
    var contact=new XeroContactClient.Contact(contactId,"Customer","Alex","Smith",null,null,null,null);
    quotes.seed(first.tenantId(),quote); contacts.seed(first.tenantId(),contact);
    assertThat(quotes.page(first,1,XeroQuoteClient.Status.ACCEPTED,null)).containsExactly(quote);
    assertThat(quotes.detail(first,quoteId)).isEqualTo(quote);
    assertThat(contacts.detail(first,contactId)).isEqualTo(contact);
    assertThat(quotes.page(second,1,XeroQuoteClient.Status.ACCEPTED,null)).isEmpty();
    assertThatThrownBy(() -> contacts.detail(second,contactId)).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
  }
}
