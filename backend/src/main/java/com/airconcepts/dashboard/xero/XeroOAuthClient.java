package com.airconcepts.dashboard.xero;

import com.fasterxml.jackson.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;

/** Fixed-origin OAuth transport. All errors are rebuilt without raw bodies or credentials. */
public class XeroOAuthClient {
  private final RestClient identity, api;
  private final XeroSettings settings;
  private final Clock clock;

  public XeroOAuthClient(RestClient identity, RestClient api, XeroSettings settings, Clock clock) {
    this.identity = identity; this.api = api; this.settings = settings; this.clock = clock;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenBody(@JsonProperty("access_token") String access, @JsonProperty("refresh_token") String refresh,
      @JsonProperty("expires_in") Long expiry, @JsonProperty("token_type") String type,
      @JsonProperty("scope") String scope) {
    @Override public String toString() { return "TokenBody[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ConnectionBody(UUID id, UUID tenantId, String tenantType, String tenantName) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ErrorBody(String error) {}

  public XeroTokens exchange(String code) { return tokens("authorization_code", "code", code); }
  public XeroTokens refresh(String refreshToken) { return tokens("refresh_token", "refresh_token", refreshToken); }

  private XeroTokens tokens(String grantType, String field, String value) {
    settings.requireConfigured();
    if (value == null || value.isBlank() || value.length() > 32768) throw malformed(true);
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", grantType); form.add(field, value);
    if (grantType.equals("authorization_code")) form.add("redirect_uri", settings.redirectUri());
    var body = call(identity.post().uri("/connect/token").headers(h -> h.setBasicAuth(settings.clientId(), settings.clientSecret()))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form), TokenBody.class, true);
    if (body == null || !validToken(body.access()) || !validToken(body.refresh())
        || body.expiry() == null || body.expiry() < 1 || body.expiry() > 86400
        || !"Bearer".equalsIgnoreCase(body.type())) throw malformed(true);
    return new XeroTokens(body.access(), body.refresh(), clock.instant().plusSeconds(body.expiry()), parseScopes(body.scope()));
  }

  private static Set<String> parseScopes(String scope) {
    if (scope == null || scope.isBlank() || scope.length() > 8192) return null;
    var result = new LinkedHashSet<String>();
    for (String value : scope.strip().split(" +")) {
      if (!value.matches("[A-Za-z0-9._:-]{1,160}")) return null;
      result.add(value);
    }
    return Set.copyOf(result);
  }

  public List<XeroTenant> connections(String accessToken) {
    if (!validToken(accessToken)) throw malformed(false);
    var body = call(api.get().uri("/connections").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
        ConnectionBody[].class, false);
    if (body == null) throw malformed(false);
    var result = new ArrayList<XeroTenant>();
    var connectionIds = new HashSet<UUID>(); var tenantIds = new HashSet<UUID>();
    for (var row : body) {
      if (row == null || row.tenantType() == null) throw malformed(false);
      if (!"ORGANISATION".equalsIgnoreCase(row.tenantType())) continue;
      if (row.id() == null || row.tenantId() == null || row.tenantName() == null || row.tenantName().isBlank()
          || row.tenantName().length() > 500 || !connectionIds.add(row.id()) || !tenantIds.add(row.tenantId())) throw malformed(false);
      result.add(new XeroTenant(row.id(), row.tenantId(), row.tenantName()));
    }
    return List.copyOf(result);
  }

  private <T> T call(RestClient.RequestHeadersSpec<?> request, Class<T> type, boolean mutation) {
    try { return request.retrieve().body(type); }
    catch (RestClientResponseException response) {
      int status = response.getStatusCode().value();
      String providerError = null;
      try {
        var error = response.getResponseBodyAs(ErrorBody.class);
        providerError = error == null ? null : error.error();
      } catch (RuntimeException invalidBody) { /* Never propagate provider body or parse details. */ }
      boolean configuration = Set.of("invalid_client", "unauthorized_client", "invalid_scope").contains(providerError == null ? "" : providerError);
      boolean reconnect = !configuration && ("invalid_grant".equals(providerError) || status == 401 || status == 403);
      String code = configuration ? "XERO_CONFIGURATION_ERROR" : reconnect ? "XERO_RECONNECT_REQUIRED" : status == 429 ? "XERO_RATE_LIMITED" : "XERO_PROVIDER_FAILED";
      Integer retry = retryAfter(response.getResponseHeaders() == null ? null : response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER));
      throw new XeroProviderFailure(code, configuration ? "Xero app credentials or scopes were refused. Ask the setup owner to check server settings."
          : reconnect ? "Xero access was refused. Ask a Director to reconnect the organisation."
          : status == 429 ? "Xero has limited requests. Wait before retrying."
          : "Xero could not confirm this operation. Retry or reconnect through Settings.", status, reconnect, mutation && status >= 500, retry);
    } catch (RestClientException unavailable) {
      throw new XeroProviderFailure("XERO_UNAVAILABLE", "Xero did not confirm this operation. Retry or reconnect through Settings.",
          0, false, mutation, null);
    }
  }

  private Integer retryAfter(String value) {
    if (value == null) return null;
    try { return Math.max(0, Integer.parseInt(value.strip())); }
    catch (NumberFormatException dateHeader) {
      try {
        Duration duration = Duration.between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        long seconds = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        return Math.toIntExact(Math.max(0, seconds));
      } catch (RuntimeException invalid) { return null; }
    }
  }

  private static XeroProviderFailure malformed(boolean uncertain) {
    return new XeroProviderFailure("XERO_INVALID_RESPONSE", "Xero returned an incomplete response. Retry or reconnect through Settings.",
        200, false, uncertain, null);
  }

  private static boolean validToken(String value) {
    return value != null && !value.isEmpty() && value.length() <= 32768
        && value.chars().allMatch(c -> c >= 33 && c <= 126);
  }
}
