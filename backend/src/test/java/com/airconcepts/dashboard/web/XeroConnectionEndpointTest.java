package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.xero.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"XERO_CLIENT_ID=synthetic-client", "XERO_CLIENT_SECRET=synthetic-client-secret",
    "XERO_REDIRECT_URI=https://api.example.invalid/api/v1/xero/callback",
    "XERO_FRONTEND_CALLBACK_URI=https://app.example.invalid/settings/entities",
    "XERO_TOKEN_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class XeroConnectionEndpointTest {
  private static final UUID DIRECTOR = UUID.randomUUID(), ENTITY = UUID.randomUUID(), ATTEMPT = UUID.randomUUID(), GRANT = UUID.randomUUID();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityRepository entities;
  @MockitoBean XeroConnectionRepository repository;
  @MockitoBean XeroOAuthClient provider;
  @MockitoBean XeroGrantService grants;

  private void as(Role role) {
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(DIRECTOR.toString()));
    when(roles.findRole(DIRECTOR.toString())).thenReturn(Optional.ofNullable(role));
    when(entities.findById(ENTITY)).thenReturn(Optional.of(new Entity(ENTITY, "Additional business")));
  }
  private String bearer() { return "Bearer " + TestJwts.TOKEN; }
  private OAuthAttempt attempt(OAuthAttempt.State state) {
    return new OAuthAttempt(ATTEMPT, DIRECTOR, ENTITY, state == OAuthAttempt.State.READY ? GRANT : null,
        Instant.now().plusSeconds(600), state, null, null);
  }

  @Test
  void frUc15_01_directorStartsAuthorisationWithoutReceivingAnySecret() throws Exception {
    as(Role.DIRECTOR);
    mvc.perform(post("/api/v1/entities/" + ENTITY + "/xero/authorization").header("Authorization", bearer()))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.startsWith("https://login.xero.com/identity/connect/authorize?")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-client-secret"))));
    verify(repository).createAttempt(any(), eq(DIRECTOR), eq(ENTITY), anyString(), any());
  }

  @Test
  void frUc15_01_otherRolesCannotStartInspectOrMap() throws Exception {
    for (Role role : Arrays.asList(Role.FINANCIAL_ADMIN, Role.STANDARD_USER, null)) {
      as(role);
      mvc.perform(post("/api/v1/entities/" + ENTITY + "/xero/authorization").header("Authorization", bearer()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/xero/attempts/" + ATTEMPT).header("Authorization", bearer()))
          .andExpect(status().isForbidden());
      mvc.perform(put("/api/v1/xero/attempts/" + ATTEMPT + "/connection").header("Authorization", bearer())
          .contentType(MediaType.APPLICATION_JSON).content("{\"connectionId\":\"" + UUID.randomUUID() + "\"}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(repository, provider, grants);
  }

  @Test
  void frUc15_10_publicCallbackUsesSingleUseStateAndRedirectsWithoutCodeOrTokens() throws Exception {
    when(repository.claimAttempt(anyString(), any())).thenReturn(Optional.of(attempt(OAuthAttempt.State.PROCESSING)), Optional.empty());
    when(provider.exchange("synthetic-code")).thenReturn(new XeroTokens("synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(1800)));
    mvc.perform(get("/api/v1/xero/callback").param("state", "a".repeat(43)).param("code", "synthetic-code"))
        .andExpect(status().isSeeOther()).andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Location", "https://app.example.invalid/settings/entities?xeroAttempt=" + ATTEMPT));
    mvc.perform(get("/api/v1/xero/callback").param("state", "a".repeat(43)).param("code", "synthetic-code"))
        .andExpect(status().isBadRequest());
    verify(provider, times(1)).exchange(anyString());
  }

  @Test
  void frUc15_10_unknownStateNeverReachesXeroAndCallbackSubpathsAreProtected() throws Exception {
    mvc.perform(get("/api/v1/xero/callback").param("state", "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/xero/callback/extra")).andExpect(status().isUnauthorized());
    verifyNoInteractions(repository, provider, grants);
  }

  @Test
  void frUc15_03_confirmationUsesDiscoveredTenantNotAnInjectedTenantId() throws Exception {
    as(Role.DIRECTOR);
    var tenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "New authorised organisation");
    var ownedAttempt = attempt(OAuthAttempt.State.READY);
    when(repository.ownedAttempt(ATTEMPT, DIRECTOR)).thenReturn(Optional.of(ownedAttempt));
    when(grants.access(GRANT)).thenReturn(new XeroAccess(GRANT, "synthetic-access"));
    when(provider.connections("synthetic-access")).thenReturn(List.of(tenant));
    when(repository.connection(ENTITY)).thenReturn(Optional.of(new StoredConnection(ENTITY, GRANT, tenant.tenantId(), tenant.name(), Instant.now(), GrantState.ACTIVE, null)));
    mvc.perform(put("/api/v1/xero/attempts/" + ATTEMPT + "/connection").header("Authorization", bearer())
        .contentType(MediaType.APPLICATION_JSON).content("{\"connectionId\":\"" + tenant.connectionId() + "\",\"tenantId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONNECTED"))
        .andExpect(jsonPath("$.organisationName").value(tenant.name())).andExpect(jsonPath("$.tenantId").doesNotExist())
        .andExpect(jsonPath("$.accessToken").doesNotExist());
    verify(repository).completeMapping(eq(ownedAttempt), eq(tenant), eq(List.of(tenant)), any());
  }

  @Test
  void frUc15_04_allActiveRolesCanSeeSafeConnectionStatus() throws Exception {
    as(Role.STANDARD_USER);
    mvc.perform(get("/api/v1/entities/" + ENTITY + "/xero").header("Authorization", bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_CONNECTED"));
  }
}
