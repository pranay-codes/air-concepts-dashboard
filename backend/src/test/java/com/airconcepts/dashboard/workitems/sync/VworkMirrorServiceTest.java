package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkMirrorServiceTest {
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID(), manager = UUID.randomUUID(), owner = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-20T00:00:00Z");
  private final VworkMirrorRepository repository = mock(VworkMirrorRepository.class);
  private final VworkMirrorService service = new VworkMirrorService(repository, Clock.fixed(now, ZoneOffset.UTC));
  private final StageMachine.Settings settings = new StageMachine.Settings(owner, true, manager, true);
  private final StageMachine.State toSchedule = new StageMachine.State(WorkItemStage.TO_SCHEDULE, manager, null, null, false, false, Set.of());

  private VworkClient.Job job(VworkClient.Status status, String worker, Instant start, Instant completed) {
    return new VworkClient.Job("mock-1", "J-1", "template-a", null, status, worker, start, completed);
  }
  private VworkMirrorRepository.Item item(StageMachine.State state, String linked) {
    return new VworkMirrorRepository.Item(1, "J-1", linked, null, null, null, null, state, settings, 2);
  }
  @Test void gl14_gl15_linksAProvenEntityJobAndSchedulesIt() {
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(toSchedule, null)));
    when(repository.templateBelongsTo(entity, "template-a")).thenReturn(true);
    when(repository.update(eq(entity), eq(item), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
    var job = job(VworkClient.Status.ASSIGNED, "Worker", now, null);
    service.apply(entity, item, "revision", job);
    var scheduled = new StageMachine.State(WorkItemStage.SCHEDULED, manager, null, null, true, false, Set.of());
    verify(repository).update(eq(entity), eq(item), eq(job), eq(scheduled), eq(toSchedule), eq(1), eq(now), isNull());
    verify(repository).audit(eq(entity), eq(item), eq("JOB_LINKED"), any(), any());
    verify(repository).audit(eq(entity), eq(item), eq("STAGE_CHANGED"), any(), eq(scheduled));
  }
  @Test void gl06_neverLinksAJobWithUnverifiedOrOtherEntityTemplate() {
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(toSchedule, null)));
    assertThatThrownBy(() -> service.apply(entity, item, "revision", job(VworkClient.Status.UNASSIGNED, null, null, null)))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verify(repository, never()).update(any(), any(), any(), any(), any(), anyInt(), any(), any());
  }
  @Test void gl16_completionHandsOffToFinanceWithoutEmail() {
    var scheduled = new StageMachine.State(WorkItemStage.SCHEDULED, manager, null, null, true, false, Set.of());
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(scheduled, "mock-1")));
    when(repository.update(eq(entity), eq(item), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
    var completed = job(VworkClient.Status.COMPLETED, "Worker", now.minusSeconds(3600), now);
    service.apply(entity, item, "revision", completed);
    var invoicing = new StageMachine.State(WorkItemStage.JOB_COMPLETED, owner, null, null, true, false, Set.of());
    verify(repository).update(eq(entity), eq(item), eq(completed), eq(invoicing), eq(scheduled), eq(1), eq(now), any());
    verify(repository).audit(entity, item, "STAGE_CHANGED", scheduled, invoicing);
  }
  @Test void gl15_unchangedLinkedJobDoesNotWriteAnotherEvent() {
    var scheduled = new StageMachine.State(WorkItemStage.SCHEDULED, manager, null, null, true, false, Set.of());
    var job = job(VworkClient.Status.ASSIGNED, "Worker", now, null);
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(new VworkMirrorRepository.Item(
        1, "J-1", job.id(), "ASSIGNED", "Worker", now, null, scheduled, settings, 2)));
    service.apply(entity, item, "revision", job);
    verify(repository, never()).update(any(), any(), any(), any(), any(), anyInt(), any(), any());
    verify(repository, never()).audit(any(), any(), any(), any(), any());
  }
  @Test void gl22_doesNotChangeAnItemWhileAnExternalOperationIsUnresolved() {
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(toSchedule, null)));
    when(repository.templateBelongsTo(entity, "template-a")).thenReturn(true);
    when(repository.hasUnresolvedOperation(entity, item)).thenReturn(true);
    assertThatThrownBy(() -> service.apply(entity, item, "revision", job(VworkClient.Status.UNASSIGNED, null, null, null)))
        .isInstanceOfSatisfying(com.airconcepts.dashboard.web.ApiFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("VWORK_SYNC_OPERATION_IN_PROGRESS"));
    verify(repository, never()).update(any(), any(), any(), any(), any(), anyInt(), any(), any());
  }
  @Test void gl_status_03_scheduledVerifiedJobLinksFromQuoted() {
    var quoted = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(quoted, null)));
    when(repository.templateBelongsTo(entity, "template-a")).thenReturn(true);
    when(repository.update(eq(entity), eq(item), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
    service.apply(entity, item, "revision", job(VworkClient.Status.ASSIGNED, "Worker", now, null));
    verify(repository).update(eq(entity), eq(item), any(), argThat(s -> s.stage()==WorkItemStage.SCHEDULED && s.jobLinked()),
        eq(quoted), eq(1), eq(now), isNull());
  }
  @Test void gl_status_01_jobChangesRemainTrackedAfterInvoicingWithoutRegressingTheStage() {
    var invoiced = new StageMachine.State(WorkItemStage.INVOICED, owner, null, null, true, true, Set.of());
    when(repository.lock(entity, item, "revision")).thenReturn(Optional.of(item(invoiced, "mock-1")));
    when(repository.update(eq(entity), eq(item), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
    service.apply(entity, item, "revision", job(VworkClient.Status.COMPLETED, "Worker", now.minusSeconds(60), now));
    verify(repository).update(eq(entity), eq(item), any(), eq(invoiced), eq(invoiced), eq(1), eq(now), isNull());
  }
}
