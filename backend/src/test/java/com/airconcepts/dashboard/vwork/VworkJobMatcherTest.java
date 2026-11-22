package com.airconcepts.dashboard.vwork;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VworkJobMatcherTest {
  private final UUID entity = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final Map<String, UUID> templates = Map.of("ours", entity, "theirs", other);

  private VworkClient.Job job(String id, String ref, String template) {
    return new VworkClient.Job(id, ref, template, "free text", VworkClient.Status.UNASSIGNED, null, null, null);
  }

  @Test void acceptsOnlyAnUnlinkedJobWithVerifiedEntityTemplate() {
    var candidate = job("1", "job-1", "ours");
    var result = VworkJobMatcher.match(entity, " JOB-1 ", List.of(candidate), templates, Set.of());
    assertEquals(VworkJobMatcher.Outcome.MATCH, result.outcome());
    assertEquals(candidate, result.job());
  }

  @Test void distinguishesNoMatchFromAnUnsafeExistingJob() {
    assertEquals(VworkJobMatcher.Outcome.NO_MATCH, VworkJobMatcher.match(entity, "ref", List.of(), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.OTHER_ENTITY, VworkJobMatcher.match(entity, "ref", List.of(job("1", "ref", "theirs")), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.UNMAPPED_TEMPLATE, VworkJobMatcher.match(entity, "ref", List.of(job("1", "ref", "unknown")), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.UNVERIFIED, VworkJobMatcher.match(entity, "ref", List.of(job("1", "ref", null)), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.ALREADY_LINKED, VworkJobMatcher.match(entity, "ref", List.of(job("1", "ref", "ours")), templates, Set.of("1")).outcome());
  }

  @Test void multipleCandidatesNeverAutoLinkEvenWhenOneIsValid() {
    var result = VworkJobMatcher.match(entity, "ref", List.of(job("1", "ref", "ours"), job("2", "ref", null)), templates, Set.of());
    assertEquals(VworkJobMatcher.Outcome.AMBIGUOUS, result.outcome());
    assertNull(result.job());
  }

  @Test void rejectsInvalidReferenceAndUnexpectedProviderResults() {
    assertEquals(VworkJobMatcher.Outcome.INVALID_REFERENCE, VworkJobMatcher.match(entity, " ", List.of(), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.INVALID_REFERENCE, VworkJobMatcher.match(entity, "x".repeat(256), List.of(), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.INVALID_RESPONSE, VworkJobMatcher.match(entity, "ref", List.of(job("1", "another", "ours")), templates, Set.of()).outcome());
    assertEquals(VworkJobMatcher.Outcome.INVALID_RESPONSE, VworkJobMatcher.match(entity, "ref", List.of(job(" ", "ref", "ours")), templates, Set.of()).outcome());
  }
}
