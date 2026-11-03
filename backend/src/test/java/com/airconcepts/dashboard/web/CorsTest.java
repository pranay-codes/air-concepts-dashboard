package com.airconcepts.dashboard.web;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void allowsDefaultViteOrigin() throws Exception {
    mockMvc.perform(options("/api/v1/me")
            .header(ORIGIN, "http://localhost:5173")
            .header(ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
  }

  @Test
  void rejectsOtherOrigins() throws Exception {
    mockMvc.perform(options("/api/v1/me")
            .header(ORIGIN, "https://evil.example")
            .header(ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}
