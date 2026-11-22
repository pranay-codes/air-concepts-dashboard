package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.vwork.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkSyncTest {
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID();
  private final VworkMirrorRepository repository = mock(VworkMirrorRepository.class);
  private final VworkMirrorService mirror = mock(VworkMirrorService.class);
  private final MockVworkClient client = new MockVworkClient();
  private final VworkCredential credential = new VworkCredential("synthetic");
  private final VworkConnectionService.Access access = new VworkConnectionService.Access("revision", credential, client, true);

  @Test void gl14_gl15_gl16_deterministicMockLinksSchedulesAndCompletes() {
    var job = client.create(credential, new VworkClient.NewJob("J-1", "template-a", "Synthetic job", List.of(new VworkClient.ScopeLine("Work", "1"))));
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", null)));
    when(repository.templateOwners()).thenReturn(Map.of("template-a", entity));
    when(repository.linkedJobs()).thenReturn(Set.of());
    var sync = new VworkSync(repository, mirror);
    sync.run(entity, () -> access);
    verify(mirror).apply(entity, item, "revision", job);
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", job.id())));
    var start = Instant.parse("2026-11-20T00:00:00Z");
    client.schedule(job.id(), "Worker", start);
    sync.run(entity, () -> access);
    client.complete(job.id(), start.plusSeconds(3600));
    sync.run(entity, () -> access);
    verify(mirror).apply(eq(entity), eq(item), eq("revision"), argThat(value -> value.status() == VworkClient.Status.ASSIGNED));
    verify(mirror).apply(eq(entity), eq(item), eq("revision"), argThat(value -> value.status() == VworkClient.Status.COMPLETED));
  }

  @Test void gl06_otherEntityTemplateNeverLinks() {
    client.create(credential, new VworkClient.NewJob("J-1", "template-b", "Synthetic job", List.of(new VworkClient.ScopeLine("Work", "1"))));
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", null)));
    when(repository.templateOwners()).thenReturn(Map.of("template-b", UUID.randomUUID()));
    when(repository.linkedJobs()).thenReturn(Set.of());
    new VworkSync(repository, mirror).run(entity, () -> access);
    verifyNoInteractions(mirror);
  }

  @Test void gl14_unverifiedLiveReferenceSearchIsNotTreatedAsComplete() {
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", null)));
    var unverified = new VworkConnectionService.Access("revision", credential, client, false);
    assertThatThrownBy(() -> new VworkSync(repository, mirror).run(entity, () -> unverified))
        .isInstanceOfSatisfying(com.airconcepts.dashboard.web.ApiFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("VWORK_SEARCH_UNVERIFIED"));
    verifyNoInteractions(mirror);
  }
  @Test void gl06_connectionRotationStopsBeforeAnyProviderReadOrMutation() {
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", "mock-1")));
    var changed = new VworkConnectionService.Access("different", credential, client, true);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    assertThatThrownBy(() -> new VworkSync(repository, mirror).run(entity,
        () -> calls.getAndIncrement() == 0 ? access : changed))
        .isInstanceOfSatisfying(com.airconcepts.dashboard.web.ApiFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("VWORK_CONNECTION_CHANGED"));
    verifyNoInteractions(mirror);
  }
  @Test void gl14_gl16_mockSyncFeedsRealMirrorAndStageMachine() {
    var manager = UUID.randomUUID(); var owner = UUID.randomUUID();
    var settings = new com.airconcepts.dashboard.workitems.StageMachine.Settings(owner, true, manager, true);
    var stage = com.airconcepts.dashboard.workitems.WorkItemStage.TO_SCHEDULE;
    var initial = new com.airconcepts.dashboard.workitems.StageMachine.State(stage, manager, null, null, false, false, Set.of());
    var linked = new com.airconcepts.dashboard.workitems.StageMachine.State(stage, manager, null, null, true, false, Set.of());
    var scheduled = new com.airconcepts.dashboard.workitems.StageMachine.State(com.airconcepts.dashboard.workitems.WorkItemStage.SCHEDULED,
        manager, null, null, true, false, Set.of());
    var ready = new com.airconcepts.dashboard.workitems.StageMachine.State(com.airconcepts.dashboard.workitems.WorkItemStage.TO_INVOICE,
        owner, null, null, true, false, Set.of());
    var job = client.create(credential, new VworkClient.NewJob("J-1", "template-a", "Synthetic", List.of(new VworkClient.ScopeLine("Work", "1"))));
    var at = Instant.parse("2026-11-20T00:00:00Z");
    when(repository.candidates(entity)).thenReturn(List.of(new VworkMirrorRepository.Candidate(item, "J-1", null)),
        List.of(new VworkMirrorRepository.Candidate(item, "J-1", job.id())));
    when(repository.templateOwners()).thenReturn(Map.of("template-a", entity));
    when(repository.linkedJobs()).thenReturn(Set.of());
    when(repository.templateBelongsTo(entity, "template-a")).thenReturn(true);
    when(repository.lock(entity, item, "revision")).thenReturn(
        Optional.of(new VworkMirrorRepository.Item(0, "J-1", null, null, null, null, null, initial, settings, 2)),
        Optional.of(new VworkMirrorRepository.Item(1, "J-1", job.id(), "UNASSIGNED", null, null, null, linked, settings, 2)),
        Optional.of(new VworkMirrorRepository.Item(2, "J-1", job.id(), "ASSIGNED", "Worker", at, null, scheduled, settings, 2)));
    when(repository.update(eq(entity), eq(item), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
    var realMirror = new VworkMirrorService(repository, java.time.Clock.fixed(at, java.time.ZoneOffset.UTC));
    var sync = new VworkSync(repository, realMirror);
    sync.run(entity, () -> access);
    client.schedule(job.id(), "Worker", at);
    sync.run(entity, () -> access);
    client.complete(job.id(), at.plusSeconds(3600));
    sync.run(entity, () -> access);
    verify(repository).audit(entity, item, "JOB_LINKED", initial, linked);
    verify(repository).audit(entity, item, "STAGE_CHANGED", linked, scheduled);
    verify(repository).audit(entity, item, "STAGE_CHANGED", scheduled, ready);
  }
}
