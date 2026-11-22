package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class QuoteTransitionTest {
  @Test void gl08_acceptedQuoteHandsOffToScheduling() {
    assertThat(QuoteTransition.observe(WorkItemStage.QUOTED, QuoteTransition.Status.ACCEPTED))
        .isEqualTo(new QuoteTransition.Result(WorkItemStage.TO_SCHEDULE, null, null, true));
  }

  @Test void gl05_declinedAndDeletedQuotesCloseWithAnExplanation() {
    assertThat(QuoteTransition.observe(WorkItemStage.QUOTED, QuoteTransition.Status.DECLINED))
        .isEqualTo(new QuoteTransition.Result(WorkItemStage.CLOSED, "LOST", "Declined in Xero", false));
    assertThat(QuoteTransition.observe(WorkItemStage.QUOTED, QuoteTransition.Status.DELETED))
        .isEqualTo(new QuoteTransition.Result(WorkItemStage.CLOSED, "LOST", "Deleted in Xero", false));
  }

  @Test void gl05_sentQuoteDoesNotCloseFromInactivity() {
    assertThat(QuoteTransition.observe(WorkItemStage.QUOTED, QuoteTransition.Status.SENT))
        .isEqualTo(new QuoteTransition.Result(WorkItemStage.QUOTED, null, null, false));
  }

  @Test void gl05_userCloseRequiresAReasonAndQuotedStage() {
    assertThat(QuoteTransition.closeLost(WorkItemStage.QUOTED, "Customer declined"))
        .isEqualTo(new QuoteTransition.Result(WorkItemStage.CLOSED, "LOST", "Customer declined", false));
    assertThatIllegalArgumentException().isThrownBy(() -> QuoteTransition.closeLost(WorkItemStage.QUOTED, "  "));
    assertThatIllegalArgumentException().isThrownBy(() -> QuoteTransition.closeLost(WorkItemStage.TO_SCHEDULE, "Reason"));
    assertThatIllegalArgumentException().isThrownBy(() -> QuoteTransition.closeLost(WorkItemStage.QUOTED, "x".repeat(501)));
  }
}
