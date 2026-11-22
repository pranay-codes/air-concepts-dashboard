package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorRepository;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holds the Xero mapping and item locks until the external operation is reserved. */
@Service
public class InvoiceReservation {
  private final InvoiceDraftContext contexts;
  private final QuoteMirrorRepository mappings;
  private final OperationReservationService reservations;
  public InvoiceReservation(InvoiceDraftContext contexts, QuoteMirrorRepository mappings, OperationReservationService reservations) {
    this.contexts=contexts; this.mappings=mappings; this.reservations=reservations;
  }
  @Transactional
  public OperationReservationService.Reservation reserve(CurrentUser user, UUID entity, UUID item,
      InvoiceDraftContext.Context expected, String fingerprint) {
    if (expected==null) throw changed();
    mappings.lockConfig(entity).orElseThrow(InvoiceReservation::changed);
    if (!mappings.lockMapping(new QuoteMirrorService.Mapping(entity,expected.tenantId(),expected.grantId()))) throw changed();
    if (!expected.equals(contexts.load(user,entity,item))) throw changed();
    String lookup=expected.jobId()==null || expected.jobId().isBlank()?expected.quoteNumber():expected.jobId();
    return reservations.reserve(user,entity,item,expected.version(),ExternalOperationRepository.Kind.XERO_CREATE_INVOICE,lookup,fingerprint);
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"WORK_ITEM_CHANGED","The job or Xero connection changed. Refresh and try again."); }
}
