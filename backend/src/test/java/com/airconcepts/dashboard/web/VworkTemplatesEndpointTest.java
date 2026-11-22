package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository.Template;
import com.airconcepts.dashboard.vwork.VworkTemplatesService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VworkTemplatesEndpointTest {
  private final UUID entity = UUID.randomUUID();
  private final String subject = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean VworkTemplatesService service;
  private String path() { return "/api/v1/entities/" + entity + "/vwork-templates"; }
  private void as(Role role) {
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
  }
  private static String body() { return "{\"templates\":[{\"templateRef\":\"synthetic-template\",\"isDefault\":true}]}"; }

  @Test void gl06_directorCanSaveAndReceiveEntityTemplates() throws Exception {
    as(Role.DIRECTOR);
    var items = List.of(new Template("synthetic-template", true));
    when(service.save(any(), eq(entity), eq(items))).thenReturn(items);
    mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$[0].templateRef").value("synthetic-template"))
        .andExpect(jsonPath("$[0].isDefault").value(true));
    verify(service).save(any(), eq(entity), eq(items));
  }

  @Test void gl06_allActiveRolesCanReadButOnlyDirectorsCanWrite() throws Exception {
    when(service.get(any(), eq(entity))).thenReturn(List.of(new Template("synthetic-template", true)));
    for (var role : new Role[]{Role.FINANCIAL_ADMIN, Role.STANDARD_USER}) {
      as(role);
      mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].isDefault").value(true));
      mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
    }
    verify(service, never()).save(any(), any(), any());
    as(null);
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
  }

  @Test void gl06_conflictsAndMissingDefaultAreReportedWithoutWriting() throws Exception {
    as(Role.DIRECTOR);
    when(service.save(any(), eq(entity), any())).thenThrow(new ApiFailure(HttpStatus.CONFLICT, "VWORK_TEMPLATE_IN_USE", "Template in use."));
    mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VWORK_TEMPLATE_IN_USE"));
    clearInvocations(service);
    mvc.perform(put(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content("{\"templates\":[{\"templateRef\":\"synthetic-template\"}]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
