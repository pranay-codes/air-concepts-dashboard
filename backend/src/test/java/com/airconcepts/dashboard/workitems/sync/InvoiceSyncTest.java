package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceSyncTest {
  private final UUID entity=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),item=UUID.randomUUID(),id=UUID.randomUUID();
  private final TenantAccess access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
  private final InvoiceMirrorRepository repository=mock(InvoiceMirrorRepository.class);
  private final InvoiceMirrorService mirror=mock(InvoiceMirrorService.class);
  private final InvoiceSync.Source source=mock(InvoiceSync.Source.class);
  private final InvoiceSync sync=new InvoiceSync(repository,mirror,source,false);
  @Test void gl21_readsAndMirrorsTheLinkedInvoiceForTheCorrectTenant() {
    when(repository.candidates(entity)).thenReturn(List.of(new InvoiceMirrorRepository.Candidate(item,id)));
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,new BigDecimal("100"),new BigDecimal("40"),null,true);
    when(source.read(access,id)).thenReturn(invoice);
    assertThat(sync.run(entity,() -> access)).isEqualTo(1);
    verify(mirror).apply(new QuoteMirrorService.Mapping(entity,tenant,grant),item,invoice,false);
  }
  @Test void gl06_changedTenantAndMissingScopeStopBeforeProvider() {
    var wrong=new TenantAccess(UUID.randomUUID(),tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
    assertThatThrownBy(() -> sync.run(entity,() -> wrong)).isInstanceOf(ApiFailure.class);
    var missing=new TenantAccess(entity,tenant,grant,"synthetic",Set.of());
    assertThatThrownBy(() -> sync.run(entity,() -> missing)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(repository,source,mirror);
  }
  @Test void gl06_connectionChangeAfterReadPreventsMirror() {
    when(repository.candidates(entity)).thenReturn(List.of(new InvoiceMirrorRepository.Candidate(item,id)));
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.PAID,BigDecimal.ONE,BigDecimal.ZERO,null,false);
    when(source.read(access,id)).thenReturn(invoice);
    var changed=new TenantAccess(entity,UUID.randomUUID(),grant,"synthetic",Set.of("accounting.invoices.read"));
    var calls=new int[1];
    assertThatThrownBy(() -> sync.run(entity,() -> ++calls[0] <= 2 ? access : changed)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(mirror);
  }
  @Test void gl19_oneManualInvoiceIsReadAndLinked() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(repository.unlinked(entity)).thenReturn(List.of(candidate));
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.DRAFT,BigDecimal.TEN,BigDecimal.TEN,null,false,"J-1 / Q-1");
    when(source.read(access,id)).thenReturn(invoice);
    var withLookup=new InvoiceSync(repository,mirror,source,false,(a,j,q) -> Set.of(id));
    assertThat(withLookup.run(entity,() -> access)).isEqualTo(1);
    verify(mirror).link(new QuoteMirrorService.Mapping(entity,tenant,grant),candidate,invoice,false);
  }
  @Test void gl19_multipleManualInvoicesRaiseAttentionWithoutLinking() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(repository.unlinked(entity)).thenReturn(List.of(candidate));
    var withLookup=new InvoiceSync(repository,mirror,source,false,(a,j,q) -> Set.of(id,UUID.randomUUID()));
    withLookup.run(entity,() -> access);
    verify(mirror).multiple(new QuoteMirrorService.Mapping(entity,tenant,grant),candidate);
    verifyNoInteractions(source);
  }
  @Test void gl19_changedInvoiceReferenceCannotBeLinked() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(repository.unlinked(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.DRAFT,BigDecimal.TEN,BigDecimal.TEN,null,false,"OTHER"));
    var withLookup=new InvoiceSync(repository,mirror,source,false,(a,j,q) -> Set.of(id));
    assertThatThrownBy(() -> withLookup.run(entity,() -> access)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(mirror);
  }
}
