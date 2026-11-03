package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Fails when the controllers and the committed OpenAPI snapshot in docs/api/openapi.json differ. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSnapshotTest {

  private static final Path SNAPSHOT = Path.of("..", "docs", "api", "openapi.json");

  private static final String REGENERATE = "docs/api/openapi.json is out of date with the controllers. "
      + "If this API change is intended, regenerate it from /backend with: "
      + "./mvnw -q test -Dopenapi.update=true . Then run npm run api:generate in /frontend "
      + "and commit both files together. Never edit either file by hand.";

  private static final JsonMapper MAPPER = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .build();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void apiDocsMatchCommittedSnapshot() throws Exception {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject("snapshot-user"));

    String body = mockMvc.perform(get("/v3/api-docs").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    JsonNode actual = MAPPER.readTree(body);

    if (Boolean.getBoolean("openapi.update")) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, MAPPER.writeValueAsString(actual) + "\n");
      return;
    }

    assertThat(SNAPSHOT).as("Snapshot file is missing. " + REGENERATE).exists();
    JsonNode expected = MAPPER.readTree(Files.readString(SNAPSHOT));
    assertThat(MAPPER.writeValueAsString(actual))
        .as(REGENERATE)
        .isEqualTo(MAPPER.writeValueAsString(expected));
  }
}
