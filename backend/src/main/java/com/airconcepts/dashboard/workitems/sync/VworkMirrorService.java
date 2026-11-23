package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.time.WorkingDays;
import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies one provider observation and its audit records in one transaction. */
@Service
public class VworkMirrorService {
  private final VworkMirrorRepository repository;
  private final Clock clock;
  private final WorkingDays workingDays = new WorkingDays(Set.of());
  public VworkMirrorService(VworkMirrorRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

  @Transactional
  public void apply(UUID entity, UUID itemId, String revision, VworkClient.Job job) {
    Objects.requireNonNull(job);
    if (job.id() == null || job.id().isBlank() || job.id().length() > 120 || job.status() == null) throw invalid();
    var item = repository.lock(entity, itemId, revision).orElseThrow(VworkMirrorService::changed);
    var before = item.state();
    if (before.stage() == WorkItemStage.CLOSED) throw changed();
    var linked = before;
    boolean newLink = item.jobId() == null;
    if (newLink) {
      if (item.reference() == null || job.reference() == null || !item.reference().strip().equalsIgnoreCase(job.reference().strip())
          || !repository.templateBelongsTo(entity, job.templateId()) || repository.jobLinkedElsewhere(itemId, job.id())) throw invalid();
      try { linked = StageMachine.apply(before, new StageMachine.LinkJob(), item.settings()).state(); }
      catch (IllegalArgumentException invalid) { throw changed(); }
    } else if (!item.jobId().equals(job.id())) throw changed();
    var observed = StageMachine.apply(linked, new StageMachine.JobObserved(job.workerName() != null && !job.workerName().isBlank(),
        job.plannedStart() != null, job.status() == VworkClient.Status.COMPLETED, job.status() == VworkClient.Status.CANCELED), item.settings());
    var next = observed.state();
    if (!newLink && next.equals(before) && Objects.equals(item.status(), job.status().name())
        && Objects.equals(item.worker(), job.workerName()) && Objects.equals(item.planned(), job.plannedStart())
        && Objects.equals(item.completed(), job.completedAt())) return;
    if (repository.hasUnresolvedOperation(entity, itemId))
      throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_SYNC_OPERATION_IN_PROGRESS", "Resolve the existing operation before syncing this job.");
    var now = clock.instant();
    LocalDate late = next.stage() == WorkItemStage.TO_INVOICE && before.stage() != next.stage()
        ? workingDays.addWorkingDays(BusinessTime.dateOf(now), item.threshold()) : null;
    try {
      if (repository.update(entity, itemId, job, next, before, item.version(), now, late) != 1) throw changed();
    } catch (DuplicateKeyException duplicate) { throw invalid(); }
    if (newLink) repository.audit(entity, itemId, "JOB_LINKED", before, linked);
    if (observed.event() != null) repository.audit(entity, itemId, observed.event(), linked, next);
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "VWORK_SYNC_CHANGED", "The job or connection changed. Retry sync."); }
  private static ApiFailure invalid() { return new ApiFailure(HttpStatus.CONFLICT, "VWORK_LINK_UNVERIFIED", "The vWork job cannot be safely linked to this entity."); }
}
