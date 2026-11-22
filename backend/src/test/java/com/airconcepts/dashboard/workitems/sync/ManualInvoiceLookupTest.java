package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.xero.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManualInvoiceLookupTest {
  private final UUID entity=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),item=UUID.randomUUID();
  private final TenantAccess access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
  private final XeroInvoiceClient client=mock(XeroInvoiceClient.class);
  private final ManualInvoiceLookup lookup=new ManualInvoiceLookup(client);
  private InvoiceViews.Summary invoice(UUID id,String reference) {
    return new InvoiceViews.Summary(id,InvoiceType.ACCREC,"INV-1",reference,"Synthetic",LocalDate.of(2026,11,21),null,
        null,null,"NZD",InvoiceStatus.AUTHORISED,false,null);
  }
  private InvoiceViews.Page page(InvoiceViews.Summary... rows) { return new InvoiceViews.Page(List.of(rows),1,100,1,rows.length); }
  @Test void gl19_uniqueInvoiceAcrossBothTermsIsReturnedOnce() {
    var id=UUID.randomUUID(); var row=invoice(id,"J-1 / Q-1");
    when(client.searchReference(access,"J-1",1)).thenReturn(page(row));
    when(client.searchReference(access,"Q-1",1)).thenReturn(page(row));
    assertThat(lookup.find(access,"J-1","Q-1")).containsExactly(id);
  }
  @Test void gl19_multipleMatchesAreNotCollapsed() {
    var a=UUID.randomUUID(); var b=UUID.randomUUID();
    when(client.searchReference(access,"J-1",1)).thenReturn(page(invoice(a,"J-1"),invoice(b,"J-1")));
    assertThat(lookup.find(access,"J-1",null)).containsExactlyInAnyOrder(a,b);
  }
  @Test void gl19_emptyLookupNeverInventsAnInvoice() {
    when(client.searchReference(access,"J-1",1)).thenReturn(page());
    assertThat(lookup.find(access,"J-1",null)).isEmpty();
  }
}
