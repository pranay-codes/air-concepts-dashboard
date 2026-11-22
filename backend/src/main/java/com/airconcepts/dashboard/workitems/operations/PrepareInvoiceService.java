package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Explicit create action; provider calls occur outside the reservation and confirmation transactions. */
@Service
public class PrepareInvoiceService {
  @io.swagger.v3.oas.annotations.media.Schema(name="PrepareInvoiceResult",requiredProperties={"operationId","outcome"})
  public record Result(UUID operationId, OperationState.Result outcome) {}
  private final InvoiceDraftService drafts;
  private final InvoiceReservation reservations;
  private final InvoiceOperationCompletion completion;
  private final OperationOutcomeService outcomes;
  private final XeroConnectionService connections;
  private final InvoiceGateway gateway;
  public PrepareInvoiceService(InvoiceDraftService drafts, InvoiceReservation reservations, InvoiceOperationCompletion completion,
      OperationOutcomeService outcomes, XeroConnectionService connections, InvoiceGateway gateway) {
    this.drafts=drafts; this.reservations=reservations; this.completion=completion; this.outcomes=outcomes;
    this.connections=connections; this.gateway=gateway;
  }
  public Result submit(CurrentUser user, UUID entity, UUID item, int version, String fingerprint) {
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    if (!gateway.available()) throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_CREATE_UNAVAILABLE","Invoice creation is unavailable for this connection.");
    if (fingerprint==null || !fingerprint.matches("[0-9a-f]{64}")) throw changed();
    var prepared=drafts.prepare(user,entity,item);
    if (prepared.context().version()!=version || !prepared.draft().fingerprint().equals(fingerprint)) throw changed();
    var access=check(prepared,entity,connections.authorisedAccess(user,entity));
    var operation=reservations.reserve(user,entity,item,prepared.context(),fingerprint).id();
    XeroInvoiceClient.WorkflowInvoice existing=null;
    try {
      access=check(prepared,entity,connections.authorisedAccess(user,entity));
      var search=gateway.find(access,prepared.context().jobId(),prepared.context().quoteNumber());
      check(prepared,entity,connections.authorisedAccess(user,entity));
      if (search==null || !search.complete() || search.matches().size()>1) throw unavailable();
      if (!search.matches().isEmpty()) existing=search.matches().getFirst();
    } catch (RuntimeException failure) {
      outcomes.markFailed(entity,item,operation,OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE,null);
      throw unavailable();
    }
    if (existing!=null) {
      try { completion.complete(entity,item,prepared.context(),operation,existing,OperationState.Result.LINKED_EXISTING); }
      catch (RuntimeException failure) {
        outcomes.markFailed(entity,item,operation,OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE,null);
        throw unavailable();
      }
      return new Result(operation,OperationState.Result.LINKED_EXISTING);
    }
    try {
      access=check(prepared,entity,connections.authorisedAccess(user,entity));
    } catch (RuntimeException failure) {
      outcomes.markFailed(entity,item,operation,OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE,null);
      throw unavailable();
    }
    try {
      var invoice=gateway.create(access,prepared.draft(),operation);
      check(prepared,entity,connections.authorisedAccess(user,entity));
      completion.complete(entity,item,prepared.context(),operation,invoice,OperationState.Result.CREATED);
      return new Result(operation,OperationState.Result.CREATED);
    } catch (RuntimeException failure) {
      outcomes.markUnknown(entity,item,operation);
      throw new ApiFailure(HttpStatus.BAD_GATEWAY,"EXTERNAL_OUTCOME_UNKNOWN","The invoice outcome needs review before retrying.");
    }
  }
  private static TenantAccess check(InvoiceDraftService.Prepared expected, UUID entity, TenantAccess current) {
    if (current==null || !entity.equals(current.entityId()) || !expected.context().tenantId().equals(current.tenantId())
        || !expected.context().grantId().equals(current.grantId())) throw changed();
    XeroScopes.require(current.grantedScopes(),XeroScopes.QUOTES_READ);
    XeroScopes.require(current.grantedScopes(),XeroScopes.INVOICES_WRITE);
    return current;
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"WORK_ITEM_CHANGED","The quote or connection changed. Review the invoice again."); }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.CONFLICT,"INVOICE_LOOKUP_UNAVAILABLE","Existing invoices need review before creating another."); }
}
