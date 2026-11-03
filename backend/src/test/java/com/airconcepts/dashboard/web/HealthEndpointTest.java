package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.airconcepts.dashboard.TestJwts;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void healthIsPublicWithoutToken() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void otherActuatorEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/env"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void healthDoesNotLeakDetailsEvenWithToken() throws Exception {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject("health-user"));

    mockMvc.perform(get("/actuator/health").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components").doesNotExist());
  }
}
