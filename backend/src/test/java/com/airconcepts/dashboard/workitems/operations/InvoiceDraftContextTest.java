package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.workitems.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class InvoiceDraftContextTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),actor=UUID.randomUUID();
  private final WorkItemRepository items=mock(WorkItemRepository.class);
  private final ExternalOperationRepository operations=mock(ExternalOperationRepository.class);
  private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  private final InvoiceDraftContext context=new InvoiceDraftContext(items,operations,jdbc);
  @Test void gl06_currentDatabaseRolePreventsAnOldFinanceTokenFromAccessingQuote() {
    var user=new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN);
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    assertThatThrownBy(() -> context.load(user,entity,item)).isInstanceOf(AccessDeniedException.class);
    verify(items,never()).lockItem(entity,item);
    verifyNoInteractions(operations,jdbc);
  }
  @Test void gl06_standardUserCannotAccessInvoiceReview() {
    assertThatThrownBy(() -> context.load(new CurrentUser(actor.toString(),Role.STANDARD_USER),entity,item)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(items,operations,jdbc);
  }
}
