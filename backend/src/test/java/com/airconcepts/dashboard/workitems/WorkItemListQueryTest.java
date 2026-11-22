package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorkItemListQueryTest {
  @Test void gl01_literalSearchEscapesSqlWildcardsAndEscapeCharacters() {
    assertThat(WorkItemListQuery.likePattern("a!_%\\b")).isEqualTo("%a!!!_!%\\b%");
    assertThat(query("  Q-1  ", 1, 25).search()).isEqualTo("Q-1");
  }
  @Test void gl01_pageAndSearchBoundsAreEnforced() {
    assertThatIllegalArgumentException().isThrownBy(() -> query("x".repeat(201), 1, 25));
    assertThatIllegalArgumentException().isThrownBy(() -> query("x\n", 1, 25));
    assertThatIllegalArgumentException().isThrownBy(() -> query("", 0, 25));
    assertThatIllegalArgumentException().isThrownBy(() -> query("", 1, 101));
    assertThat(query("", Integer.MAX_VALUE, 100).offset()).isEqualTo(214748364600L);
  }
  private WorkItemListQuery query(String search, int page, int size) {
    return new WorkItemListQuery(null, null, null, null, null, null, search,
        WorkItemListQuery.Sort.stageEnteredAt, WorkItemListQuery.Direction.ASC, page, size);
  }
}
