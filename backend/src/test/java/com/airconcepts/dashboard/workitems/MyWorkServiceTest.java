package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;

import com.airconcepts.dashboard.security.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyWorkServiceTest {
  private final UUID actor = UUID.randomUUID(), other = UUID.randomUUID();
  private final LocalDate today = LocalDate.of(2026, 11, 19);
  private MyWorkService.Candidate item(WorkItemStage stage, UUID owner, LocalDate followUp,
      LocalDate invoiceDue, BigDecimal amount, Set<String> attention, boolean designated) {
    return new MyWorkService.Candidate(null, stage, owner, followUp, invoiceDue, amount, attention, designated);
  }

  @Test void gl10_quotedAndInvoiceWorkAreClassifiedByAucklandDateAndAmount() {
    assertThat(MyWorkService.classify(item(WorkItemStage.QUOTED, actor, today, null, null, Set.of(), false), actor, Role.STANDARD_USER, today))
        .isEqualTo(MyWorkService.Bucket.NEEDS_ACTION);
    assertThat(MyWorkService.classify(item(WorkItemStage.QUOTED, actor, today.plusDays(1), null, null, Set.of(), false), actor, Role.STANDARD_USER, today))
        .isEqualTo(MyWorkService.Bucket.WAITING);
    assertThat(MyWorkService.classify(item(WorkItemStage.INVOICED, actor, null, today.minusDays(1), BigDecimal.ONE, Set.of(), false), actor, Role.STANDARD_USER, today))
        .isEqualTo(MyWorkService.Bucket.NEEDS_ACTION);
    assertThat(MyWorkService.classify(item(WorkItemStage.INVOICED, actor, null, today.minusDays(1), BigDecimal.ZERO, Set.of(), false), actor, Role.STANDARD_USER, today))
        .isEqualTo(MyWorkService.Bucket.WAITING);
    assertThat(MyWorkService.classify(item(WorkItemStage.SCHEDULED, actor, null, null, null, Set.of(), false), actor, Role.STANDARD_USER, today))
        .isEqualTo(MyWorkService.Bucket.WAITING);
  }

  @Test void gl10_attentionRequiresOwnerDirectorOrUnownedDesignation() {
    var alert = Set.of("NO_SERVICE_MANAGER");
    assertThat(MyWorkService.classify(item(WorkItemStage.TO_SCHEDULE, other, null, null, null, alert, false), actor, Role.DIRECTOR, today)).isEqualTo(MyWorkService.Bucket.ATTENTION);
    assertThat(MyWorkService.classify(item(WorkItemStage.TO_SCHEDULE, null, null, null, null, alert, true), actor, Role.STANDARD_USER, today)).isEqualTo(MyWorkService.Bucket.ATTENTION);
    assertThat(MyWorkService.classify(item(WorkItemStage.TO_SCHEDULE, other, null, null, null, alert, true), actor, Role.STANDARD_USER, today)).isEqualTo(MyWorkService.Bucket.IGNORE);
    assertThat(MyWorkService.classify(item(WorkItemStage.CLOSED, actor, null, null, null, alert, false), actor, Role.DIRECTOR, today)).isEqualTo(MyWorkService.Bucket.IGNORE);
    assertThat(MyWorkService.classify(item(WorkItemStage.JOB_COMPLETED, other, null, null, null, Set.of(), false), actor, Role.STANDARD_USER, today)).isEqualTo(MyWorkService.Bucket.IGNORE);
  }
}
