package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holds template and connection locks through the existing role-checked reservation transaction. */
@Service
public class VworkJobReservation {
  private final VworkMirrorRepository mirror;
  private final OperationReservationService reservations;
  public VworkJobReservation(VworkMirrorRepository mirror, OperationReservationService reservations) {
    this.mirror = mirror; this.reservations = reservations;
  }
  @Transactional
  public OperationReservationService.Reservation reserve(CurrentUser user, UUID entity, UUID item, int version,
      String connectionRevision, String template, String reference, String fingerprint) {
    return reserveAttempt(user,entity,item,version,connectionRevision,template,reference,fingerprint,null);
  }
  @Transactional
  public OperationReservationService.Reservation retry(CurrentUser user, UUID entity, UUID item, int version,
      String connectionRevision, String template, String reference, String fingerprint, UUID previous) {
    if (previous == null) throw invalid();
    return reserveAttempt(user,entity,item,version,connectionRevision,template,reference,fingerprint,previous);
  }
  private OperationReservationService.Reservation reserveAttempt(CurrentUser user, UUID entity, UUID item, int version,
      String connectionRevision, String template, String reference, String fingerprint, UUID previous) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    if (connectionRevision == null || connectionRevision.isBlank() || template == null || template.isBlank()
        || template.length() > 120 || !template.equals(template.strip())) throw invalid();
    var current = mirror.lock(entity, item, connectionRevision).orElseThrow(VworkJobReservation::changed);
    if (current.version() != version || current.state().stage() != WorkItemStage.TO_SCHEDULE || current.jobId() != null) throw changed();
    if (!mirror.templateBelongsTo(entity, template)) throw invalid();
    return previous == null
        ? reservations.reserve(user, entity, item, version, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, reference, fingerprint)
        : reservations.retry(user, entity, item, version, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, previous, reference, fingerprint);
  }
  private static ApiFailure invalid() { return new ApiFailure(HttpStatus.BAD_REQUEST, "CREATE_JOB_INVALID", "Choose a template assigned to this entity."); }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "The job or connection changed. Refresh and try again."); }
}
