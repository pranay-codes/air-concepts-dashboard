package com.airconcepts.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.vwork.VworkConnectionService;
import java.time.Instant;
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
class VworkConnectionEndpointTest {
  private static final String PATH = "/api/v1/vwork-connection";
  private static final String KEY = "synthetic-secret-key";
  private final String subject = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean VworkConnectionService service;
  private void as(Role role) {
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
  }
  private String body() { return "{\"apiKey\":\"" + KEY + "\"}"; }

  @Test void gl26_directorCanConnectAndDisconnectWithoutReturningKey() throws Exception {
    as(Role.DIRECTOR);
    when(service.get(any())).thenReturn(new VworkConnectionService.View("ACTIVE", UUID.randomUUID(),
        Instant.parse("2026-11-19T00:00:00Z"), null, null));
    var response = mvc.perform(put(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.status").value("ACTIVE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain(KEY, "apiKey", "ciphertext");
    verify(service).connect(any(), eq(KEY));
    mvc.perform(delete(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isOk());
    verify(service).disconnect(any());
    assertThat(new VworkConnectionController.ConnectionRequest(KEY).toString()).doesNotContain(KEY);
  }

  @Test void gl26_staffCanReadStatusButCannotChangeConnection() throws Exception {
    when(service.get(any())).thenReturn(new VworkConnectionService.View("ACTIVE", UUID.randomUUID(), Instant.now(), Instant.now(), "CODE"));
    for (var role : new Role[] { Role.FINANCIAL_ADMIN, Role.STANDARD_USER }) {
      as(role);
      var response = mvc.perform(get(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.configuredBy").doesNotExist()).andExpect(jsonPath("$.errorCode").doesNotExist())
          .andReturn().getResponse().getContentAsString();
      assertThat(response).doesNotContain(KEY, "apiKey", "ciphertext", "configuredAt", "verifiedAt");
      mvc.perform(put(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
      mvc.perform(delete(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    }
    verify(service, never()).connect(any(), any());
    verify(service, never()).disconnect(any());
  }

  @Test void gl26_rejectionAndMalformedRequestsDoNotEchoKey() throws Exception {
    as(Role.DIRECTOR);
    doThrow(new ApiFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "VWORK_KEY_REJECTED", "vWork rejected the API key."))
        .when(service).connect(any(), any());
    var response = mvc.perform(put(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VWORK_KEY_REJECTED")).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain(KEY);
    response = mvc.perform(put(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\": [\"" + KEY + "\"]}"))
        .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain(KEY);
  }

  @Test void gl26_inactiveAndUnauthenticatedUsersCannotRead() throws Exception {
    as(null);
    mvc.perform(get(PATH).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }
}
