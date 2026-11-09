package com.airconcepts.dashboard.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcRoleLookupTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  @Test
  @SuppressWarnings("unchecked")
  void frUc15_18_readsActiveRoleAgainForEveryRequestInsteadOfCachingJwtRole() {
    var id = UUID.randomUUID();
    when(jdbc.query(anyString(), any(RowMapper.class), eq(id)))
        .thenReturn(List.of(Role.DIRECTOR), List.of());
    var lookup = new JdbcRoleLookup(jdbc);
    assertThat(lookup.findRole(id.toString())).contains(Role.DIRECTOR);
    assertThat(lookup.findRole(id.toString())).isEmpty();
    verify(jdbc, times(2)).query(contains("active = true"), any(RowMapper.class), eq(id));
  }

  @Test
  void frUc15_18_malformedSubjectCannotReachDatabase() {
    var lookup = new JdbcRoleLookup(jdbc);
    assertThat(lookup.findRole("not-a-uuid")).isEmpty();
    assertThat(lookup.findRole(null)).isEmpty();
    verifyNoInteractions(jdbc);
  }
}
