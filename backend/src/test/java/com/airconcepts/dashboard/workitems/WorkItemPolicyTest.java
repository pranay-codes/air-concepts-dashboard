package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;

import com.airconcepts.dashboard.security.Role;
import org.junit.jupiter.api.Test;

class WorkItemPolicyTest {
  private record Actor(Role role, boolean owner, boolean manager, boolean finance, boolean maySchedule) {}

  @Test void gl09_eachImplementedActionRespectsRoleStageAndLinkState() {
    var actors = new Actor[]{new Actor(Role.DIRECTOR, false, false, true, true),
        new Actor(Role.FINANCIAL_ADMIN, false, false, true, false),
        new Actor(Role.FINANCIAL_ADMIN, false, true, true, true),
        new Actor(Role.STANDARD_USER, false, true, false, true),
        new Actor(Role.STANDARD_USER, true, false, false, false),
        new Actor(Role.STANDARD_USER, false, false, false, false)};
    for (var actor : actors) for (var stage : WorkItemStage.values()) for (boolean linked : new boolean[]{false, true}) {
      assertThat(WorkItemPolicy.allowed(WorkItemAction.EDIT_NOTES, actor.role(), true, actor.owner(), actor.manager(), stage, linked, linked))
          .isEqualTo(stage != WorkItemStage.CLOSED && (actor.finance() || actor.owner()));
      assertThat(WorkItemPolicy.allowed(WorkItemAction.CLOSE_LOST, actor.role(), true, actor.owner(), actor.manager(), stage, linked, linked))
          .isEqualTo(stage == WorkItemStage.QUOTED && actor.finance());
      assertThat(WorkItemPolicy.allowed(WorkItemAction.CREATE_JOB, actor.role(), true, actor.owner(), actor.manager(), stage, linked, linked))
          .isEqualTo(stage == WorkItemStage.TO_SCHEDULE && !linked && actor.maySchedule());
      for (var action : new WorkItemAction[]{WorkItemAction.PREPARE_INVOICE, WorkItemAction.NO_INVOICE_NEEDED})
        assertThat(WorkItemPolicy.allowed(action, actor.role(), true, actor.owner(), actor.manager(), stage, linked, linked))
            .isEqualTo(stage == WorkItemStage.TO_INVOICE && !linked && actor.finance());
      for (var action : WorkItemAction.values())
        assertThat(WorkItemPolicy.allowed(action, actor.role(), false, actor.owner(), actor.manager(), stage, linked, linked)).isFalse();
    }
  }

  @Test void gl09_jobCreationRequiresDesignationAndAnUnlinkedSchedulableItem() {
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CREATE_JOB, Role.STANDARD_USER, true, false, true,
        WorkItemStage.TO_SCHEDULE, false, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CREATE_JOB, Role.FINANCIAL_ADMIN, true, false, false,
        WorkItemStage.TO_SCHEDULE, false, false)).isFalse();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CREATE_JOB, Role.DIRECTOR, true, false, false,
        WorkItemStage.TO_SCHEDULE, true, false)).isFalse();
  }

  @Test void gl09_invoiceActionsRequireFinanceRoleAndNoExistingInvoice() {
    assertThat(WorkItemPolicy.allowed(WorkItemAction.PREPARE_INVOICE, Role.FINANCIAL_ADMIN, true, false, false,
        WorkItemStage.TO_INVOICE, true, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.PREPARE_INVOICE, Role.STANDARD_USER, true, true, true,
        WorkItemStage.TO_INVOICE, true, false)).isFalse();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.NO_INVOICE_NEEDED, Role.DIRECTOR, true, false, false,
        WorkItemStage.TO_INVOICE, true, true)).isFalse();
  }

  @Test void gl04_ownerCanEditOpenItemButInactiveUserAndClosedItemCannot() {
    assertThat(WorkItemPolicy.allowed(WorkItemAction.EDIT_NOTES, Role.STANDARD_USER, true, true, false,
        WorkItemStage.QUOTED, false, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.EDIT_NOTES, Role.DIRECTOR, false, true, false,
        WorkItemStage.QUOTED, false, false)).isFalse();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.EDIT_NOTES, Role.DIRECTOR, true, false, false,
        WorkItemStage.CLOSED, false, false)).isFalse();
  }

  @Test void gl08_onlyFinanceCanAcceptASentQuoteAndOnlyWhileQuoted() {
    assertThat(WorkItemPolicy.allowed(WorkItemAction.MARK_ACCEPTED, Role.FINANCIAL_ADMIN, true, false, false,
        WorkItemStage.QUOTED, false, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.MARK_ACCEPTED, Role.DIRECTOR, true, false, false,
        WorkItemStage.QUOTED, false, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.MARK_ACCEPTED, Role.STANDARD_USER, true, true, true,
        WorkItemStage.QUOTED, false, false)).isFalse();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.MARK_ACCEPTED, Role.FINANCIAL_ADMIN, true, false, false,
        WorkItemStage.TO_SCHEDULE, false, false)).isFalse();
  }

  @Test void gl05_onlyFinanceCanCloseAQuotedItemAsLost() {
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CLOSE_LOST, Role.FINANCIAL_ADMIN, true, false, false,
        WorkItemStage.QUOTED, false, false)).isTrue();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CLOSE_LOST, Role.STANDARD_USER, true, true, true,
        WorkItemStage.QUOTED, false, false)).isFalse();
    assertThat(WorkItemPolicy.allowed(WorkItemAction.CLOSE_LOST, Role.DIRECTOR, true, false, false,
        WorkItemStage.TO_SCHEDULE, false, false)).isFalse();
  }
}
