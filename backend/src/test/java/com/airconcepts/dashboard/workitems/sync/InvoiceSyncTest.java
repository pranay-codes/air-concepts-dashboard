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
  @Test void gl_status_04_longerIdentifiersDoNotCreateFalseAmbiguity() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"Q-1");
    var other=UUID.randomUUID();
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    var exact=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-1");
    var longer=new XeroInvoiceClient.WorkflowInvoice(other,"INV-2",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-10");
    when(source.read(access,id)).thenReturn(exact);
    when(source.read(access,other)).thenReturn(longer);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id,other),true);
    assertThat(withLookup.run(entity,() -> access)).isEqualTo(1);
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    verify(mirror).link(mapping,candidate,exact,true);
    verify(mirror,never()).multiple(any(),any());
  }
  /** GL-STATUS-02: job-first matching. An item with a Job ID links on the Job ID; the quote number is not a second route. */
  @Test void gl_status_02_jobIdLinksAnInvoiceThatNamesOnlyTheJobId() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"JO413","QU-0008");
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-0055",InvoiceStatus.DRAFT,
        BigDecimal.TEN,BigDecimal.TEN,null,false,"JO413");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(invoice);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(withLookup.run(entity,() -> access)).isEqualTo(1);
    verify(mirror).link(mapping,candidate,invoice,true);
  }
  @Test void gl_status_02_aJobLinkedItemIgnoresAnInvoiceThatNamesOnlyTheQuote() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"JO413","QU-0008");
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-0055",InvoiceStatus.DRAFT,
        BigDecimal.TEN,BigDecimal.TEN,null,false,"QU-0008");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(invoice);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(withLookup.run(entity,() -> access)).isZero();
    verify(mirror,never()).link(any(),any(),any(),anyBoolean());
  }
  @Test void gl_status_02_withoutAJobIdTheQuoteNumberLinks() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"QU-0008");
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-0055",InvoiceStatus.DRAFT,
        BigDecimal.TEN,BigDecimal.TEN,null,false,"QU-0008");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(invoice);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(withLookup.run(entity,() -> access)).isEqualTo(1);
    verify(mirror).link(mapping,candidate,invoice,true);
  }
  /** Conflict detection stays conservative: an invoice naming another item's quote is still a conflict under job-first matching. */
  @Test void gl_status_04_conflictDetectionStillCountsAnotherItemsQuoteWhenJobIdsMatch() {
    var duplicate=new InvoiceMirrorRepository.Unlinked(UUID.randomUUID(),"J-1","Q-3");
    assertThat(InvoiceSync.matchesAnyTerm("Q-1 / Q-3",duplicate,true)).isTrue();
    assertThat(InvoiceSync.matchesReference("Q-1 / Q-3",duplicate,true)).isFalse();
    assertThat(InvoiceSync.matchesAnyTerm("Q-1",duplicate,true)).isFalse();
  }
  @Test void gl_status_04_duplicateJobReferenceNeedsReviewWithoutLinking() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(repository.conflictingReferences(mapping,candidate)).thenReturn(true);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(withLookup.run(entity,() -> access)).isZero();
    verify(mirror).multiple(mapping,candidate);
    verifyNoInteractions(source);
  }
  @Test void gl_status_04_defaultLookupFlagsReferenceConflictsForReview() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.DRAFT,
        BigDecimal.TEN,BigDecimal.TEN,null,false,"J-1 / Q-1");
    when(repository.unlinked(entity)).thenReturn(List.of(candidate));
    when(repository.conflictingReferences(mapping,candidate)).thenReturn(true);
    when(source.read(access,id)).thenReturn(invoice);
    var defaultLookup=new InvoiceSync(repository,mirror,source,false,(a,j,q) -> Set.of(id));
    assertThat(defaultLookup.run(entity,() -> access)).isZero();
    verify(mirror).multiple(mapping,candidate);
    verify(mirror,never()).link(any(),any(),any(),anyBoolean());
  }
  @Test void gl_status_04_invoiceAlsoNamingAnotherQuoteNeedsReview() {
    var contact=UUID.randomUUID();
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"Q-1",contact);
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-1 / Q-2",contact);
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(invoice);
    when(repository.invoiceConflictsWithOtherWorkItems(mapping,candidate,invoice)).thenReturn(true);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true,false,true);
    assertThat(withLookup.run(entity,() -> access)).isZero();
    verify(mirror).multiple(mapping,candidate);
    verify(mirror,never()).link(any(),any(),any(),anyBoolean());
  }
  @Test void gl_status_01_oneUnusableReferenceDoesNotBlockOtherInvoices() {
    var broken=new InvoiceMirrorRepository.Unlinked(UUID.randomUUID(),"bad","Q-bad");
    var valid=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(broken,valid));
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"J-1 / Q-1");
    when(source.read(access,id)).thenReturn(invoice);
    InvoiceSync.Lookup lookup=(a,j,q) -> {
      if ("bad".equals(j)) throw new ApiFailure(org.springframework.http.HttpStatus.CONFLICT,"INVOICE_LOOKUP_INCOMPLETE","Incomplete");
      return Set.of(id);
    };
    var sync=new InvoiceSync(repository,mirror,source,true,lookup,true);
    assertThatThrownBy(() -> sync.run(entity,() -> access)).isInstanceOfSatisfying(ApiFailure.class,
        error -> assertThat(error.code()).isEqualTo("INVOICE_SYNC_PARTIAL"));
    verify(mirror).link(new QuoteMirrorService.Mapping(entity,tenant,grant),valid,invoice,true);
  }
  @Test void gl_status_05_budgetStopEndsOptionalLookupAsPartial() {
    var first=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    var second=new InvoiceMirrorRepository.Unlinked(UUID.randomUUID(),"J-2","Q-2");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(first,second));
    var calls=new int[1];
    InvoiceSync.Lookup lookup=(a,j,q) -> {
      calls[0]++;
      throw new ApiFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"XERO_DAILY_BUDGET_RESERVED","Reserved");
    };
    var withLookup=new InvoiceSync(repository,mirror,source,true,lookup,true);
    assertThatThrownBy(() -> withLookup.run(entity,() -> access)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("INVOICE_SYNC_PARTIAL"));
    assertThat(calls[0]).isEqualTo(1);
    verifyNoInteractions(source,mirror);
  }
  @Test void gl_status_05_tooManyPotentialMatchesDoNotTriggerUnboundedInvoiceReads() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    var many=Set.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    var sync=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> many,true);
    assertThatThrownBy(() -> sync.run(entity,() -> access)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("INVOICE_SYNC_PARTIAL"));
    verifyNoInteractions(source,mirror);
  }
  @Test void gl_status_04_documentedMatchRequiresTheSameXeroContact() {
    var quoteContact=UUID.randomUUID();
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"Q-1",quoteContact);
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-1",UUID.randomUUID()));
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true,false,true);
    assertThat(withLookup.run(entity,() -> access)).isZero();
    verifyNoInteractions(mirror);
  }
  @Test void gl_status_02_documentedCopiedReferenceAndContactLinkSentInvoice() {
    var contact=UUID.randomUUID();
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"QU-42",contact);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"QU-42",contact);
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(invoice);
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true,false,true);
    assertThat(withLookup.run(entity,() -> access)).isEqualTo(1);
    verify(mirror).link(new QuoteMirrorService.Mapping(entity,tenant,grant),candidate,invoice,true);
  }
  @Test void gl_status_02_unverifiedLookupDoesNotExpandToEarlierStages() {
    var withLookup=new InvoiceSync(repository,mirror,source,false,(a,j,q) -> Set.of());
    withLookup.run(entity,() -> access);
    verify(repository).unlinked(entity);
    verify(repository,never()).unlinkedAllOpen(entity);
    verify(repository,never()).paidCandidates(entity);
  }
  @Test void gl_status_02_verifiedLookupCanSearchEarlierStages() {
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(),true);
    withLookup.run(entity,() -> access);
    verify(repository).unlinkedAllOpen(entity);
    verify(repository).paidCandidates(entity);
    verify(repository,never()).unlinked(entity);
  }
  @Test void gl_status_04_doesNotLinkInvoiceWhoseReferenceOnlyContainsALongerIdentifier() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,null,"Q-1");
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(source.read(access,id)).thenReturn(new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-10"));
    var withLookup=new InvoiceSync(repository,mirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(withLookup.run(entity,() -> access)).isZero();
    verifyNoInteractions(mirror);
  }
  @Test void gl_status_02_sentInvoiceFlowsFromLookupThroughMirrorToInvoiced() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    var quotes=mock(QuoteMirrorRepository.class);
    var settings=new com.airconcepts.dashboard.workitems.StageMachine.Settings(null,false,null,false);
    var state=new com.airconcepts.dashboard.workitems.StageMachine.State(
        com.airconcepts.dashboard.workitems.WorkItemStage.TO_SCHEDULE,null,null,null,false,false,Set.of());
    when(quotes.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,settings)));
    when(quotes.lockMapping(mapping)).thenReturn(true);
    when(repository.unlinkedAllOpen(entity)).thenReturn(List.of(candidate));
    when(repository.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(1,null,state)));
    when(repository.referenceMatches(mapping,candidate)).thenReturn(true);
    when(repository.link(eq(mapping),eq(candidate),any(),any(),eq(1),any(),any())).thenReturn(1);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"INV-1",InvoiceStatus.AUTHORISED,BigDecimal.TEN,BigDecimal.TEN,null,true,"J-1 / Q-1");
    when(source.read(access,id)).thenReturn(invoice);
    var realMirror=new InvoiceMirrorService(repository,quotes,java.time.Clock.systemUTC());
    var verified=new InvoiceSync(repository,realMirror,source,true,(a,j,q) -> Set.of(id),true);
    assertThat(verified.run(entity,() -> access)).isEqualTo(1);
    verify(repository).link(eq(mapping),eq(candidate),eq(invoice),argThat(s -> s.invoiceLinked()
        && s.stage()==com.airconcepts.dashboard.workitems.WorkItemStage.INVOICED),eq(1),any(),any());
  }
}
