package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class MockInvoiceGatewayTest {
  @Test void gl06_createdInvoiceIsFoundOnlyInItsOwnTenant() {
    var gateway=new MockInvoiceGateway();
    var first=new TenantAccess(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"synthetic");
    var second=new TenantAccess(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"synthetic");
    var draft=new DraftInvoice(UUID.randomUUID(),"Customer","Q-1","J-1 / Q-1",LocalDate.of(2026,11,21),"NZD","Exclusive",null,BigDecimal.TEN,
        List.of(new DraftInvoice.Line("Work",BigDecimal.ONE,BigDecimal.TEN,null,"200","OUTPUT2",null,List.of())));
    var invoice=gateway.create(first,draft,UUID.randomUUID());
    assertThat(gateway.read(first,invoice.id())).isEqualTo(invoice);
    assertThat(gateway.find(first,"J-1","Q-1").matches()).containsExactly(invoice);
    assertThat(gateway.find(second,"J-1","Q-1").matches()).isEmpty();
    assertThatThrownBy(() -> gateway.read(second,invoice.id())).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    assertThatThrownBy(() -> gateway.create(first,draft,UUID.randomUUID())).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
  }
}
