package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Mock-only job creation. Each external call happens outside the reservation/completion transactions. */
@Service
public class CreateJobService {
  @io.swagger.v3.oas.annotations.media.Schema(name = "CreateJobResult", requiredProperties = {"operationId", "outcome"})
  public record Result(UUID operationId, OperationState.Result outcome) {}
  private final VworkJobReservation reservations;
  private final VworkOperationCompletion completion;
  private final OperationOutcomeService outcomes;
  private final VworkConnectionService connections;
  public CreateJobService(VworkJobReservation reservations, VworkOperationCompletion completion,
      OperationOutcomeService outcomes, VworkConnectionService connections) {
    this.reservations = reservations; this.completion = completion; this.outcomes = outcomes; this.connections = connections;
  }

  public Result submit(CurrentUser user, UUID entity, UUID item, int version, VworkClient.NewJob request) {
    return submitAttempt(user,entity,item,version,request,null);
  }
  public Result retry(CurrentUser user, UUID entity, UUID item, int version, VworkClient.NewJob request, UUID previous) {
    if (previous == null) throw new ApiFailure(HttpStatus.BAD_REQUEST,"OPERATION_INVALID","Choose an operation to retry.");
    return submitAttempt(user,entity,item,version,request,previous);
  }
  private Result submitAttempt(CurrentUser user, UUID entity, UUID item, int version, VworkClient.NewJob request, UUID previous) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    validate(request, version);
    var access = connections.systemAccess();
    if (!(access.provider() instanceof MockVworkClient) || !access.searchVerified())
      throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_CAPABILITY_UNVERIFIED", "Job creation is unavailable until vWork templates are verified.");
    var operation = (previous == null
        ? reservations.reserve(user, entity, item, version, access.revision(), request.templateId(), request.reference(), fingerprint(request))
        : reservations.retry(user, entity, item, version, access.revision(), request.templateId(), request.reference(), fingerprint(request), previous)).id();

    VworkClient.Job existing = null;
    try {
      var current = sameConnection(access);
      var found = current.provider().findByReference(current.credential(), request.reference());
      sameConnection(access);
      if (found == null || found.size() > 1) throw new IllegalStateException("Ambiguous lookup");
      if (!found.isEmpty()) existing = found.getFirst();
    } catch (RuntimeException failure) {
      outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
      throw unavailable();
    }
    if (existing != null) {
      try {
        completion.complete(entity, item, operation, access.revision(), existing, OperationState.Result.LINKED_EXISTING);
      } catch (RuntimeException failure) {
        outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
        throw unavailable();
      }
      return new Result(operation, OperationState.Result.LINKED_EXISTING);
    }

    VworkConnectionService.Access current;
    try { current = sameConnection(access); }
    catch (RuntimeException failure) {
      outcomes.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
      throw unavailable();
    }
    try {
      var created = current.provider().create(current.credential(), request);
      sameConnection(access);
      completion.complete(entity, item, operation, access.revision(), created, OperationState.Result.CREATED);
      return new Result(operation, OperationState.Result.CREATED);
    } catch (RuntimeException failure) {
      // A request or local confirmation may have succeeded despite an error: never retry automatically.
      outcomes.markUnknown(entity, item, operation);
      throw new ApiFailure(HttpStatus.BAD_GATEWAY, "EXTERNAL_OUTCOME_UNKNOWN", "The job outcome needs review before retrying.");
    }
  }

  private VworkConnectionService.Access sameConnection(VworkConnectionService.Access expected) {
    var actual = connections.systemAccess();
    if (actual == null || !expected.revision().equals(actual.revision()) || expected.provider() != actual.provider())
      throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_CONNECTION_CHANGED", "The vWork connection changed. Retry the request.");
    return actual;
  }
  private static void validate(VworkClient.NewJob request, int version) {
    if (request == null || version < 0 || !validIdentifier(request.reference(), 255)
        || !validIdentifier(request.templateId(), 120)
        || !valid(request.description(), 4000) || !valid(request.customer(), 500) || !valid(request.contactPerson(), 500)
        || !valid(request.phone(), 40) || !valid(request.siteAddress(), 2000)
        || (request.notes() != null && request.notes().length() > 2000)
        || request.scope().isEmpty() || request.scope().size() > 200
        || request.scope().stream().anyMatch(line -> line == null || !valid(line.description(), 4000) || !valid(line.quantity(), 80)))
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "CREATE_JOB_INVALID", "Complete the job reference, template, description and scope.");
  }
  private static boolean valid(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
  }
  private static boolean validIdentifier(String value, int max) {
    return valid(value, max) && value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl);
  }
  private static String fingerprint(VworkClient.NewJob request) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      part(digest, request.reference()); part(digest, request.templateId()); part(digest, request.description());
      part(digest, request.customer()); part(digest, request.contactPerson()); part(digest, request.phone());
      part(digest, request.siteAddress()); part(digest, request.notes() == null ? "" : request.notes());
      digest.update(ByteBuffer.allocate(4).putInt(request.scope().size()).array());
      for (var line : request.scope()) { part(digest, line.description()); part(digest, line.quantity()); }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
  }
  private static void part(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
  }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.BAD_GATEWAY, "VWORK_SEARCH_UNAVAILABLE", "The vWork job could not be checked. Review before retrying."); }
}
