package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository.Settings;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GreenLineSettingsEndpointTest {
  private final UUID entity = UUID.randomUUID();
  private final String subject = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean GreenLineSettingsService settings;

  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
  }
  private String path() { return "/api/v1/entities/" + entity + "/green-line/settings"; }
  private String body(int threshold) {
    return "{\"enabled\":true,\"lateThresholdWorkingDays\":" + threshold + ",\"backfillDays\":90}";
  }

  @Test void gl08_directorReadsFullSettingsAndSavesWithNoStore() throws Exception {
    as(Role.DIRECTOR);
    var data = new Settings(entity, true, UUID.randomUUID(), UUID.randomUUID(), 2, 90);
    when(settings.get(any(), eq(entity))).thenReturn(data);
    when(settings.save(any(), any())).thenAnswer(call -> call.getArgument(1));
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.defaultOwnerId").value(data.defaultOwnerId().toString()))
        .andExpect(jsonPath("$.lateThresholdWorkingDays").value(2));
    mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body(2)))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.entityId").value(entity.toString()));
    verify(settings).save(any(), eq(new Settings(entity, true, null, null, 2, 90, null, null)));
  }

  @Test void gl09_otherActiveRolesSeeOnlyEnabledAndCannotWrite() throws Exception {
    var data = new Settings(entity, true, UUID.randomUUID(), UUID.randomUUID(), 2, 90);
    when(settings.get(any(), eq(entity))).thenReturn(data);
    for (var role : new Role[]{Role.FINANCIAL_ADMIN, Role.STANDARD_USER}) {
      as(role);
      mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
          .andExpect(jsonPath("$.defaultOwnerId").doesNotExist())
          .andExpect(jsonPath("$.serviceManagerId").doesNotExist())
          .andExpect(jsonPath("$.lateThresholdWorkingDays").doesNotExist());
      mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content(body(2))).andExpect(status().isForbidden());
    }
    verify(settings, never()).save(any(), any());
  }

  @Test void gl11_invalidThresholdAndMissingFieldsAreBadRequests() throws Exception {
    as(Role.DIRECTOR);
    for (var content : new String[]{body(0), body(31), "{\"enabled\":true}"}) {
      mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isBadRequest());
    }
    verify(settings, never()).save(any(), any());
  }

  @Test void gl06_inactiveAndUnauthenticatedUsersCannotRead() throws Exception {
    as(null);
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    verifyNoInteractions(settings);
  }
}
