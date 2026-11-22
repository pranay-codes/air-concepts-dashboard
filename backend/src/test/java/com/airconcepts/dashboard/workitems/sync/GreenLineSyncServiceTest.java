package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroConnectionService;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.vwork.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GreenLineSyncServiceTest {
  private final UUID entity = UUID.randomUUID(), tenant = UUID.randomUUID(), grant = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID(), run = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-20T00:00:00Z");
  private final CurrentUser user = new CurrentUser(actor.toString(), Role.DIRECTOR);
  private final TenantAccess access = new TenantAccess(entity, tenant, grant, "synthetic", Set.of("accounting.invoices.read"));
  private final GreenLineSyncRunRepository repository = mock(GreenLineSyncRunRepository.class);
  private final XeroConnectionService connections = mock(XeroConnectionService.class);
  private final EntityService entities = mock(EntityService.class);
  private final QuoteSync sync = mock(QuoteSync.class);
  private final GreenLineSyncService service = new GreenLineSyncService(repository, sync, connections, entities,
      Clock.fixed(now, ZoneOffset.UTC));

  @Test void gl25_successRecordsCountsAndFirstSyncAgainstTheSameMapping() {
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(sync.run(eq(entity), any(), isNull())).thenReturn(new QuoteSync.Result(2, 1, 1, 0, 0, 0, true));
    service.runManual(user, entity);
    verify(entities).get(user, entity);
    verify(repository).succeed(eq(entity), eq(run), eq(access), any(QuoteSync.Result.class), eq(now));
    verify(repository, never()).fail(any(), any(), any(), any(), any());
  }

  @Test void gl25_failedSyncIsRecordedWithoutAdvancingTheSuccessMarker() {
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(sync.run(eq(entity), any(), isNull())).thenThrow(new ApiFailure(HttpStatus.CONFLICT,
        "QUOTE_SYNC_COMPLETENESS_UNVERIFIED", "Completeness is unverified."));
    assertThatThrownBy(() -> service.runManual(user, entity)).isInstanceOf(ApiFailure.class);
    verify(repository).fail(entity, run, "QUOTE_SYNC_COMPLETENESS_UNVERIFIED", null, now);
    verify(repository, never()).succeed(any(), any(), any(), any(), any());
  }

  @Test void gl21_linkedInvoicesRefreshBeforeAnIncompleteQuoteRun() {
    var invoices=mock(InvoiceSync.class);
    var connected=new GreenLineSyncService(repository,sync,connections,entities,Clock.fixed(now,ZoneOffset.UTC),null,null,invoices);
    when(repository.begin(entity,"MANUAL",actor,now)).thenReturn(run);
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    when(sync.run(eq(entity),any(),isNull())).thenThrow(new ApiFailure(HttpStatus.CONFLICT,"QUOTE_SYNC_COMPLETENESS_UNVERIFIED","Not verified."));
    assertThatThrownBy(() -> connected.runManual(user,entity)).isInstanceOf(ApiFailure.class);
    var order=inOrder(invoices,sync);
    order.verify(invoices).run(eq(entity),any());
    order.verify(sync).run(eq(entity),any(),isNull());
    verify(repository).fail(entity,run,"QUOTE_SYNC_COMPLETENESS_UNVERIFIED",null,now);
    verify(repository,never()).succeed(any(),any(),any(),any(),any());
  }

  @Test void gl25_unexpectedErrorsAreRedactedAndRecorded() {
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(sync.run(eq(entity), any(), isNull())).thenThrow(new IllegalStateException("private upstream content"));
    assertThatThrownBy(() -> service.runManual(user, entity)).isInstanceOfSatisfying(ApiFailure.class,
        error -> assertThat(error.getMessage()).doesNotContain("private upstream content"));
    verify(repository).fail(entity, run, "GREEN_LINE_SYNC_FAILED", null, now);
  }

  @Test void gl25_designatedServiceManagerCanRefreshButOtherStandardUsersCannot() {
    var standard = new CurrentUser(actor.toString(), Role.STANDARD_USER);
    assertThatThrownBy(() -> service.runManual(standard, entity))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(repository, never()).begin(any(), any(), any(), any());
    when(repository.isServiceManager(entity, actor)).thenReturn(true);
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(connections.authorisedAccess(standard, entity)).thenReturn(access);
    when(sync.run(eq(entity), any(), isNull())).thenReturn(new QuoteSync.Result(0, 0, 0, 0, 0, 0, true));
    assertThat(service.runManual(standard, entity)).isEqualTo(run);
  }

  @Test void gl25_statusRequiresActiveEntityAccess() {
    var status = new GreenLineSyncViews.Status(entity, new GreenLineSyncViews.SystemStatus(null, null, null),
        new GreenLineSyncViews.SystemStatus(null, null, null));
    when(repository.status(entity)).thenReturn(status);
    assertThat(service.status(user, entity)).isEqualTo(status);
    verify(entities).get(user, entity);
    assertThatThrownBy(() -> service.status(new CurrentUser(actor.toString(), null), entity))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test void gl01_gl08_manualRunConnectsTheRealQuoteMirrorToRunHistory() {
    var mirrorRepository = mock(QuoteMirrorRepository.class);
    var mapping = new QuoteMirrorService.Mapping(entity, tenant, grant);
    var owner = UUID.randomUUID(); var manager = UUID.randomUUID(); var item = UUID.randomUUID();
    var quote = new XeroQuoteClient.Quote(UUID.randomUUID(), "Q-1", "J-1", XeroQuoteClient.Status.ACCEPTED,
        null, "Synthetic customer", null, null, now, null, null, null, List.of());
    when(mirrorRepository.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(true, 90, 2,
        new StageMachine.Settings(owner, true, manager, true))));
    when(mirrorRepository.lockMapping(mapping)).thenReturn(true);
    when(mirrorRepository.provenanceMatches(mapping)).thenReturn(true);
    when(mirrorRepository.lockQuote(entity, quote.id())).thenReturn(Optional.empty());
    when(mirrorRepository.insert(any(), any(), any(), any(), any(), anyString())).thenReturn(item);
    QuoteSync.Source source = (ctx, status, page, since) -> new QuoteSync.Page(
        status == XeroQuoteClient.Status.ACCEPTED ? List.of(quote) : List.of(), QuoteSync.Continuation.COMPLETE);
    var realSync = new QuoteSync(new QuoteMirrorService(mirrorRepository, Clock.fixed(now, ZoneOffset.UTC)), source,
        (ctx, row) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    var connected = new GreenLineSyncService(repository, realSync, connections, entities, Clock.fixed(now, ZoneOffset.UTC));
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    assertThat(connected.runManual(user, entity)).isEqualTo(run);
    var state = new StageMachine.State(WorkItemStage.TO_SCHEDULE, manager, null, null, false, false, Set.of());
    verify(mirrorRepository).insert(eq(mapping), eq(quote), eq(state), eq(now), isNull(), eq("BACKFILL"));
    verify(repository).succeed(eq(entity), eq(run), eq(access), argThat(result -> result.created() == 1), eq(now));
  }

  @Test void gl25_xeroFailureDoesNotPreventIndependentVworkRun() {
    var vwork = mock(VworkSync.class);
    var vworkConnections = mock(VworkConnectionService.class);
    var jobRun = UUID.randomUUID();
    var jobAccess = new VworkConnectionService.Access("1", new VworkCredential("synthetic"), new MockVworkClient(), true);
    var both = new GreenLineSyncService(repository, sync, connections, entities, Clock.fixed(now, ZoneOffset.UTC), vwork, vworkConnections);
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(repository.beginVwork(entity, "MANUAL", actor, now)).thenReturn(jobRun);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(vworkConnections.systemAccess()).thenReturn(jobAccess);
    when(sync.run(eq(entity), any(), isNull())).thenThrow(new ApiFailure(HttpStatus.CONFLICT,
        "QUOTE_SYNC_COMPLETENESS_UNVERIFIED", "Not verified."));
    when(vwork.run(eq(entity), any())).thenReturn(new VworkSync.Result(1, 1, 0));
    assertThatThrownBy(() -> both.runManual(user, entity)).isInstanceOf(ApiFailure.class);
    verify(repository).fail(entity, run, "QUOTE_SYNC_COMPLETENESS_UNVERIFIED", null, now);
    verify(repository).succeedVwork(entity, jobRun, jobAccess, new VworkSync.Result(1, 1, 0), now);
  }

  @Test void gl25_vworkFailureDoesNotEraseACompletedXeroRun() {
    var vwork = mock(VworkSync.class);
    var vworkConnections = mock(VworkConnectionService.class);
    var jobRun = UUID.randomUUID();
    var both = new GreenLineSyncService(repository, sync, connections, entities, Clock.fixed(now, ZoneOffset.UTC), vwork, vworkConnections);
    when(repository.begin(entity, "MANUAL", actor, now)).thenReturn(run);
    when(repository.beginVwork(entity, "MANUAL", actor, now)).thenReturn(jobRun);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(sync.run(eq(entity), any(), isNull())).thenReturn(new QuoteSync.Result(1, 1, 0, 0, 0, 0, true));
    when(vworkConnections.systemAccess()).thenThrow(new VworkFailure("VWORK_PROVIDER_FAILED", "private body", 503, null));
    assertThatThrownBy(() -> both.runManual(user, entity)).isInstanceOf(ApiFailure.class).hasMessageNotContaining("private body");
    verify(repository).succeed(eq(entity), eq(run), eq(access), any(), eq(now));
    verify(repository).failVwork(entity, jobRun, "VWORK_PROVIDER_FAILED", 503, now);
  }
}
