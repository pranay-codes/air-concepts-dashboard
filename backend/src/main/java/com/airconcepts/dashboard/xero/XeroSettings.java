package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

/** Server-only app credentials and exact redirect destinations. */
public final class XeroSettings {
  private final String clientId, clientSecret, redirectUri, frontendCallback;
  private final Set<String> requestedScopes;

  public XeroSettings(String clientId, String clientSecret, String redirectUri, String frontendCallback) {
    this(clientId, clientSecret, redirectUri, frontendCallback, false, false, false);
  }
  public XeroSettings(String clientId, String clientSecret, String redirectUri, String frontendCallback,
      boolean quoteWorkflow, boolean invoiceWrite, boolean attachmentWrite) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
    this.frontendCallback = frontendCallback;
    this.requestedScopes = XeroScopes.requested(quoteWorkflow, invoiceWrite, attachmentWrite);
  }
  public String clientId() { return clientId; }
  public String clientSecret() { return clientSecret; }
  public String redirectUri() { return redirectUri; }
  public boolean quoteWorkflowEnabled() { return requestedScopes.contains(XeroScopes.CONTACTS_READ); }

  public void requireConfigured() {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
        || !safeUri(redirectUri) || !safeUri(frontendCallback)
        || !"/api/v1/xero/callback".equals(URI.create(redirectUri).getPath())) {
      throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "XERO_NOT_CONFIGURED",
          "Xero connections are not configured. Ask the setup owner to check server settings.");
    }
  }

  public String authorizationUrl(String state) {
    requireConfigured();
    return UriComponentsBuilder.fromUriString("https://login.xero.com/identity/connect/authorize")
        .queryParam("response_type", "code").queryParam("client_id", clientId)
        .queryParam("redirect_uri", redirectUri).queryParam("scope", String.join(" ", requestedScopes))
        .queryParam("state", state).build().encode().toUriString();
  }

  public URI callbackDestination(java.util.UUID attempt) {
    requireConfigured();
    return UriComponentsBuilder.fromUriString(frontendCallback).queryParam("xeroAttempt", attempt)
        .build().encode().toUri();
  }

  private boolean safeUri(String value) {
    try {
      if (value == null || value.isBlank()) return false;
      URI uri = URI.create(value);
      return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
          && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
              && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())));
    } catch (IllegalArgumentException invalid) { return false; }
  }
  @Override public String toString() { return "XeroSettings[redacted]"; }
}
