package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.OverviewService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OverviewEndpointTest {
  private static final String PATH = "/api/v1/work-items/overview";
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean OverviewService overview;
  private final String subject = UUID.randomUUID().toString();

  private void signedInAs(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
  }

  @Test void glDir06_directorGetsNoStoreOverviewForACheckedEntityAndPeriod() throws Exception {
    var entity = UUID.randomUUID();
    var from = LocalDate.parse("2026-11-01");
    var to = LocalDate.parse("2026-11-25");
    signedInAs(Role.DIRECTOR);
    when(overview.get(any(), eq(entity), eq(from), eq(to))).thenReturn(new OverviewService.Overview(Instant.EPOCH,
        from, to, List.of(), List.of(), List.of(), true, List.of(), false, List.of(), false, List.of(), 0, 0,
        List.of(), List.of(), new OverviewService.OverviewInvoicesSent(0, List.of(), List.of(), false, 0)));
    mvc.perform(get(PATH).param("entityId", entity.toString()).param("from", "2026-11-01").param("to", "2026-11-25")
            .header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.openTruncated").value(true)).andExpect(jsonPath("$.from").value("2026-11-01"));
    verify(entities).get(any(), eq(entity));
  }

  @ParameterizedTest @EnumSource(value = Role.class, names = {"FINANCIAL_ADMIN", "STANDARD_USER"})
  void glDir06_otherRolesAreForbidden(Role role) throws Exception {
    signedInAs(role);
    mvc.perform(get(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    verifyNoInteractions(overview, entities);
  }

  @Test void glDir06_unauthenticatedOrInactiveUserCannotRead() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    signedInAs(null);
    mvc.perform(get(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    verifyNoInteractions(overview, entities);
  }

  @Test void glDir06_malformedDateIsRejectedBeforeReading() throws Exception {
    signedInAs(Role.DIRECTOR);
    mvc.perform(get(PATH).param("from", "yesterday").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(overview);
  }
}
