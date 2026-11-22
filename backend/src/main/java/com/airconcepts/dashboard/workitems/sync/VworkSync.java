package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.vwork.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Sequential, server-side read of linked jobs and verified reference matches. */
@Service
public class VworkSync {
  public record Result(int observed, int linked, int ignored) {}
  private final VworkMirrorRepository repository;
  private final VworkMirrorService mirror;
  public VworkSync(VworkMirrorRepository repository, VworkMirrorService mirror) {
    this.repository = repository; this.mirror = mirror;
  }
  public Result run(UUID entity, Supplier<VworkConnectionService.Access> resolve) {
    var original = requireAccess(resolve.get(), null);
    int observed = 0, linked = 0, ignored = 0;
    for (var item : repository.candidates(entity)) {
      var current = requireAccess(resolve.get(), original);
      if (item.jobId() != null) {
        var job = current.provider().job(current.credential(), item.jobId());
        requireAccess(resolve.get(), original);
        mirror.apply(entity, item.id(), current.revision(), job);
        observed++;
      } else {
        if (!current.searchVerified()) throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_SEARCH_UNVERIFIED",
            "vWork reference search needs verification before automatic linking.");
        var jobs = current.provider().findByReference(current.credential(), item.reference());
        requireAccess(resolve.get(), original);
        var match = VworkJobMatcher.match(entity, item.reference(), jobs, repository.templateOwners(), repository.linkedJobs());
        if (match.outcome() == VworkJobMatcher.Outcome.MATCH) {
          mirror.apply(entity, item.id(), current.revision(), match.job());
          linked++; observed++;
        } else if (match.outcome() == VworkJobMatcher.Outcome.NO_MATCH || match.outcome() == VworkJobMatcher.Outcome.OTHER_ENTITY
            || match.outcome() == VworkJobMatcher.Outcome.UNMAPPED_TEMPLATE) ignored++;
        else throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_LINK_UNVERIFIED", "The vWork job needs review before linking.");
      }
    }
    requireAccess(resolve.get(), original);
    return new Result(observed, linked, ignored);
  }
  private static VworkConnectionService.Access requireAccess(VworkConnectionService.Access current, VworkConnectionService.Access original) {
    if (current == null || current.revision() == null || current.credential() == null || current.provider() == null
        || (original != null && (!Objects.equals(original.revision(), current.revision()) || original.provider() != current.provider())))
      throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_CONNECTION_CHANGED", "The vWork connection changed. Retry sync.");
    return current;
  }
}
