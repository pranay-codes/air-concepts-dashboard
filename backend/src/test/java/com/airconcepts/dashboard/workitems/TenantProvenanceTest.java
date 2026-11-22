package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.web.ApiFailure;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantProvenanceTest {
  @Test void gl06_mismatchedOrIndeterminateOriginFailsClosed() {
    var jdbc = mock(JdbcTemplate.class);
    var entity = UUID.randomUUID();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(entity))).thenReturn(true, null, false);
    for (int i = 0; i < 2; i++) assertThatThrownBy(() -> TenantProvenance.requireValid(jdbc, entity))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
    assertThatCode(() -> TenantProvenance.requireValid(jdbc, entity)).doesNotThrowAnyException();
  }

  @Test void gl06_allEntityCheckDoesNotNeedAClientSuppliedTenant() {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
    assertThatCode(() -> TenantProvenance.requireValid(jdbc, null)).doesNotThrowAnyException();
    verify(jdbc).queryForObject(contains("is distinct from"), eq(Boolean.class));
  }

  @Test void gl06_readLocksCurrentMappingBeforeCheckingProvenance() {
    var jdbc = mock(JdbcTemplate.class);
    var entity = UUID.randomUUID();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(entity))).thenReturn(false);
    TenantProvenance.requireValid(jdbc, entity);
    var order = inOrder(jdbc);
    order.verify(jdbc).queryForList(contains("for share of c"), eq(entity));
    order.verify(jdbc).queryForObject(contains("is distinct from"), eq(Boolean.class), eq(entity));
  }
}
