package com.airconcepts.dashboard.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** QA-001, AC-4. /api/hello is public. Only that exact path is public; everything else still needs a token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HelloEndpointTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void helloIsPublicWithoutToken() throws Exception {
    mockMvc.perform(get("/api/hello"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Hello from the Air Concepts API"));
  }

  @Test
  void subPathsOfHelloStillRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/hello/extra"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void otherMethodsOnHelloStillRequireAuthentication() throws Exception {
    mockMvc.perform(post("/api/hello"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedMeEndpointStillRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized());
  }
}
