package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.vwork.*;
import com.airconcepts.dashboard.workitems.*;
import com.airconcepts.dashboard.workitems.operations.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Main journey with real domain/services and in-memory providers; persistence is a mock. */
class GreenLineMainJourneyTest {
  @Test void gl_main_acceptedQuoteJobCompletionAndDraftInvoice() {
    var entity=UUID.randomUUID(); var item=UUID.randomUUID(); var tenant=UUID.randomUUID(); var grant=UUID.randomUUID();
    var actor=UUID.randomUUID(); var quote=UUID.randomUUID(); var jobOperation=UUID.randomUUID(); var invoiceOperation=UUID.randomUUID();
    var user=new CurrentUser(actor.toString(),Role.DIRECTOR);
    var settings=new StageMachine.Settings(actor,true,actor,true);
    var now=Instant.parse("2026-11-21T00:00:00Z"); var clock=Clock.fixed(now,ZoneOffset.UTC);
    var state=new AtomicReference<StageMachine.State>();
    var jobId=new AtomicReference<String>(); var invoiceId=new AtomicReference<UUID>();
    var access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read","accounting.invoices"));
    var quoteClient=new MockXeroQuoteClient(RestClient.builder().build(),clock,t -> {});
    var line=new XeroQuoteClient.Line("Install",BigDecimal.ONE,BigDecimal.TEN,null,null,null,"200","OUTPUT2",null,List.of());
    var quoteData=new XeroQuoteClient.Quote(quote,"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,UUID.randomUUID(),"Customer",
        LocalDate.of(2026,11,21),null,now,BigDecimal.TEN,"NZD","Exclusive",List.of(line));
    quoteClient.seed(tenant,quoteData);
    var quoteRepository=mock(QuoteMirrorRepository.class); var quoteMapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    when(quoteRepository.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(true,90,2,settings)));
    when(quoteRepository.lockMapping(quoteMapping)).thenReturn(true);
    when(quoteRepository.provenanceMatches(quoteMapping)).thenReturn(true);
    when(quoteRepository.lockQuote(entity,quote)).thenReturn(Optional.empty());
    when(quoteRepository.insert(eq(quoteMapping),eq(quoteData),any(),any(),any(),anyString())).thenAnswer(call -> {
      state.set(call.getArgument(2)); return item;
    });
    QuoteSync.Source source=(current,status,page,since) -> new QuoteSync.Page(quoteClient.page(current,page,status,since),QuoteSync.Continuation.COMPLETE);
    var quoteSync=new QuoteSync(new QuoteMirrorService(quoteRepository,clock),source,(current,observed) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    assertThat(quoteSync.run(entity,() -> access,null).created()).isEqualTo(1);
    assertThat(state.get().stage()).isEqualTo(WorkItemStage.TO_SCHEDULE);

    var vwork=new MockVworkClient(); var credential=new VworkCredential("synthetic");
    var vworkAccess=new VworkConnectionService.Access("revision",credential,vwork,true);
    var vworkConnections=mock(VworkConnectionService.class);
    when(vworkConnections.systemAccess()).thenReturn(vworkAccess);
    var jobs=mock(VworkMirrorRepository.class);
    when(jobs.lock(entity,item,"revision")).thenAnswer(call -> Optional.of(new VworkMirrorRepository.Item(
        1,"J-1",jobId.get(),null,null,null,null,state.get(),settings,2)));
    when(jobs.templateBelongsTo(entity,"template-a")).thenReturn(true);
    when(jobs.update(eq(entity),eq(item),any(),any(),any(),anyInt(),any(),any())).thenAnswer(call -> {
      state.set(call.getArgument(3)); return 1;
    });
    var operations=mock(ExternalOperationRepository.class);
    when(operations.lockOperation(entity,item,jobOperation)).thenReturn(Optional.of(new ExternalOperationRepository.Locked(
        ExternalOperationRepository.Kind.VWORK_CREATE_JOB,OperationState.Status.PENDING,"J-1")));
    when(operations.linkVwork(eq(entity),eq(item),anyInt(),eq("J-1"),any())).thenAnswer(call -> {
      VworkClient.Job job=call.getArgument(4); jobId.set(job.id());
      state.set(StageMachine.apply(state.get(),new StageMachine.LinkJob(),settings).state()); return 1;
    });
    when(operations.complete(eq(entity),eq(item),any(),any(),any())).thenReturn(1);
    var jobReservation=mock(VworkJobReservation.class);
    when(jobReservation.reserve(eq(user),eq(entity),eq(item),eq(0),eq("revision"),eq("template-a"),eq("J-1"),anyString()))
        .thenReturn(new OperationReservationService.Reservation(jobOperation,1));
    var outcomes=mock(OperationOutcomeService.class);
    var jobService=new CreateJobService(jobReservation,new VworkOperationCompletion(jobs,operations),outcomes,vworkConnections);
    var jobRequest=new VworkClient.NewJob("J-1","template-a","Work",List.of(new VworkClient.ScopeLine("Install","1")),
        "Customer","Alex","555123","Example Street","");
    assertThat(jobService.submit(user,entity,item,0,jobRequest).outcome()).isEqualTo(OperationState.Result.CREATED);
    var mirror=new VworkMirrorService(jobs,clock);
    vwork.schedule(jobId.get(),"Technician",now);
    mirror.apply(entity,item,"revision",vwork.job(credential,jobId.get()));
    assertThat(state.get().stage()).isEqualTo(WorkItemStage.SCHEDULED);
    vwork.complete(jobId.get(),now.plusSeconds(3600));
    mirror.apply(entity,item,"revision",vwork.job(credential,jobId.get()));
    assertThat(state.get().stage()).isEqualTo(WorkItemStage.TO_INVOICE);

    var context=new InvoiceDraftContext.Context(quote,"Q-1","J-1",4,tenant,grant,"Example Xero");
    var draft=DraftInvoice.fromQuote(quoteData,"J-1",LocalDate.of(2026,11,21));
    var draftContexts=mock(InvoiceDraftContext.class);
    when(draftContexts.load(user,entity,item)).thenReturn(context);
    var reservation=mock(InvoiceReservation.class);
    when(reservation.reserve(user,entity,item,context,draft.fingerprint())).thenReturn(new OperationReservationService.Reservation(invoiceOperation,1));
    var connections=mock(XeroConnectionService.class);
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    var drafts=new InvoiceDraftService(draftContexts,connections,quoteClient,clock);
    var mappings=mock(QuoteMirrorRepository.class); var mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
    when(mappings.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,settings)));
    when(mappings.lockMapping(mapping)).thenReturn(true);
    var invoices=mock(InvoiceMirrorRepository.class);
    when(invoices.lock(mapping,item)).thenAnswer(call -> Optional.of(new InvoiceMirrorRepository.Stored(5,null,state.get())));
    when(invoices.referenceMatches(eq(mapping),any())).thenReturn(true);
    when(invoices.link(eq(mapping),any(),any(),any(),eq(5),any(),any())).thenAnswer(call -> {
      XeroInvoiceClient.WorkflowInvoice invoice=call.getArgument(2); invoiceId.set(invoice.id());
      state.set(call.getArgument(3)); return 1;
    });
    when(operations.lockOperation(entity,item,invoiceOperation)).thenReturn(Optional.of(new ExternalOperationRepository.Locked(
        ExternalOperationRepository.Kind.XERO_CREATE_INVOICE,OperationState.Status.PENDING,"J-1")));
    var invoiceCompletion=new InvoiceOperationCompletion(mappings,invoices,operations,clock);
    var prepare=new PrepareInvoiceService(drafts,reservation,invoiceCompletion,outcomes,connections,new MockInvoiceGateway());
    assertThat(prepare.submit(user,entity,item,4,draft.fingerprint()).outcome()).isEqualTo(OperationState.Result.CREATED);
    assertThat(state.get().stage()).isEqualTo(WorkItemStage.INVOICE_DRAFTED);
    assertThat(invoiceId.get()).isNotNull();
    verify(operations).complete(entity,item,invoiceOperation,OperationState.Result.CREATED,invoiceId.get().toString());
  }
}
