package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroConnectionService;
import com.airconcepts.dashboard.xero.XeroQuoteWriteClient;
import com.airconcepts.dashboard.xero.XeroScopes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Accepts a sent quote in Xero, then records the acceptance. The provider call sits between the reservation
 * and completion transactions. An uncertain outcome is never retried silently.
 */
@Service
public class QuoteAcceptService {
  public record Result(UUID operationId, OperationState.Result outcome) {}

  private record Context(UUID quoteId, UUID tenantId, UUID grantId, int version, String status) {}

  private static final ExternalOperationRepository.Kind KIND = ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE;

  private final OperationReservationService reservations;
  private final ExternalOperationRepository operations;
  private final OperationOutcomeService outcomes;
  private final XeroConnectionService connections;
  private final QuoteAcceptGateway gateway;
  private final QuoteAcceptCompletion completion;

  public QuoteAcceptService(OperationReservationService reservations, ExternalOperationRepository operations,
      OperationOutcomeService outcomes, XeroConnectionService connections, QuoteAcceptGateway gateway,
      QuoteAcceptCompletion completion) {
    this.reservations = reservations;
    this.operations = operations;
    this.outcomes = outcomes;
    this.connections = connections;
    this.gateway = gateway;
    this.completion = completion;
  }

  public Result submit(CurrentUser user, UUID entity, UUID item, int version) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    if (!gateway.available()) {
      throw new ApiFailure(HttpStatus.CONFLICT, "QUOTE_ACCEPT_UNAVAILABLE",
          "Accepting quotes in Xero is unavailable for this connection.");
    }
    UUID actor = actor(user);
    var context = load(entity, item, version);
    String fingerprint = fingerprint(context);
    var latest = operations.latest(entity, item, KIND);
    var reservation = latest.isPresent() && latest.get().status() == OperationState.Status.FAILED
        ? reservations.retry(user, entity, item, version, KIND, latest.get().id(), context.quoteId().toString(), fingerprint)
        : reservations.reserve(user, entity, item, version, KIND, context.quoteId().toString(), fingerprint);
    UUID operation = reservation.id();

    TenantAccess access;
    try {
      access = authorise(user, entity, context);
    } catch (RuntimeException failure) {
      // Nothing has been sent to Xero yet, so this attempt is a confirmed non-write.
      outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
      throw failure instanceof ApiFailure api ? api
          : new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CHANGED", "The Xero connection changed. Refresh and review the job.");
    }

    XeroQuoteWriteClient.Receipt receipt;
    try {
      receipt = gateway.accept(access, context.quoteId(), operation);
    } catch (ProviderFailure failure) {
      int status = failure.upstreamStatus();
      if (status == 429) {
        outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_RATE_LIMITED, 429);
      } else if (status >= 400 && status < 500) {
        outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_REJECTED, status);
      } else {
        outcomes.markUnknown(entity, item, operation);
      }
      throw failure;
    } catch (RuntimeException failure) {
      outcomes.markUnknown(entity, item, operation);
      throw new ApiFailure(HttpStatus.BAD_GATEWAY, "EXTERNAL_OUTCOME_UNKNOWN",
          "The quote outcome needs review in Xero before retrying.");
    }

    try {
      completion.complete(actor, entity, item, context.tenantId(), context.grantId(), operation, receipt);
    } catch (RuntimeException failure) {
      // Xero may already show the quote as accepted. Hold the item for review rather than retrying the write.
      outcomes.markUnknown(entity, item, operation);
      throw new ApiFailure(HttpStatus.CONFLICT, "QUOTE_ACCEPT_RECORD_FAILED",
          "Xero accepted the quote, but the dashboard could not record it. Review the job before trying again.");
    }
    return new Result(operation, OperationState.Result.ACCEPTED);
  }

  /** Read-only pre-check; the reservation transaction re-locks the item and repeats the stage and version checks. */
  private Context load(UUID entity, UUID item, int version) {
    var found = operations.acceptContext(entity, item).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable."));
    if (!WorkItemStage.QUOTED.name().equals(found.stage()) || !"SENT".equals(found.quoteStatus())) {
      throw new ApiFailure(HttpStatus.CONFLICT, "STAGE_ACTION_NOT_ALLOWED", "This action is not available at this stage.");
    }
    if (found.version() != version) {
      throw new ApiFailure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "This work item changed. Refresh and try again.");
    }
    if (found.quoteId() == null || found.tenantId() == null || found.grantId() == null) {
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CHANGED", "The Xero connection changed. Refresh and review the job.");
    }
    return new Context(found.quoteId(), found.tenantId(), found.grantId(), found.version(), found.quoteStatus());
  }

  private TenantAccess authorise(CurrentUser user, UUID entity, Context context) {
    var access = connections.authorisedAccess(user, entity);
    if (access == null || !entity.equals(access.entityId()) || !context.tenantId().equals(access.tenantId())
        || !context.grantId().equals(access.grantId())) {
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_CONNECTION_CHANGED", "The Xero connection changed. Refresh and review the job.");
    }
    XeroScopes.require(access.grantedScopes(), XeroScopes.INVOICES_WRITE);
    return access;
  }

  private static UUID actor(CurrentUser user) {
    try {
      return UUID.fromString(user.subject());
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw new AccessDeniedException("Active user access is required.");
    }
  }

  private static String fingerprint(Context context) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
          ("XERO_ACCEPT_QUOTE|" + context.quoteId() + "|" + context.version() + "|" + context.status())
              .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }
}
