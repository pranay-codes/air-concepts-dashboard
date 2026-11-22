package com.airconcepts.dashboard.vwork;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Decides whether a complete reference lookup has one safely linkable result. */
public final class VworkJobMatcher {
  private VworkJobMatcher() {}

  public enum Outcome {
    MATCH, NO_MATCH, INVALID_REFERENCE, INVALID_RESPONSE, AMBIGUOUS,
    UNVERIFIED, UNMAPPED_TEMPLATE, OTHER_ENTITY, ALREADY_LINKED
  }

  /** Only MATCH contains a job. NO_MATCH is safe to use only with a proven complete lookup. */
  public record Result(Outcome outcome, VworkClient.Job job) {}

  public static Result match(UUID entity, String reference, List<VworkClient.Job> candidates,
      Map<String, UUID> verifiedTemplates, Set<String> linkedJobIds) {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(candidates, "candidates");
    Objects.requireNonNull(verifiedTemplates, "verifiedTemplates");
    Objects.requireNonNull(linkedJobIds, "linkedJobIds");
    if (reference == null || reference.isBlank() || reference.strip().length() > 255)
      return result(Outcome.INVALID_REFERENCE);
    String expected = reference.strip();
    for (var job : candidates) {
      if (job == null || job.id() == null || job.id().isBlank() || job.reference() == null
          || !expected.equalsIgnoreCase(job.reference().strip())) return result(Outcome.INVALID_RESPONSE);
    }
    if (candidates.isEmpty()) return result(Outcome.NO_MATCH);
    if (candidates.size() != 1) return result(Outcome.AMBIGUOUS);
    var job = candidates.getFirst();
    if (linkedJobIds.contains(job.id())) return result(Outcome.ALREADY_LINKED);
    if (job.templateId() == null || job.templateId().isBlank()) return result(Outcome.UNVERIFIED);
    var owner = verifiedTemplates.get(job.templateId());
    if (owner == null) return result(Outcome.UNMAPPED_TEMPLATE);
    if (!entity.equals(owner)) return result(Outcome.OTHER_ENTITY);
    return new Result(Outcome.MATCH, job);
  }

  private static Result result(Outcome outcome) { return new Result(outcome, null); }
}
