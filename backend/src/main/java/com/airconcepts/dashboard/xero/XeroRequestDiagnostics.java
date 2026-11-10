package com.airconcepts.dashboard.xero;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Diagnostics for every Xero request made through the accounting RestClient. Off by default; enable only in the
 * Test environment (see docs/xero-diagnostics.md).
 *
 * <p>Logs method, path, safe query values, status, timing and response structure (envelope keys, array sizes and
 * pagination numbers). Headers and tokens are never read or logged. Query values outside the safe list (for
 * example {@code where}, which can hold search text) are logged by name only. Response values are logged only when
 * include-bodies is on, which is for synthetic Test data only.
 */
public final class XeroRequestDiagnostics implements ClientHttpRequestInterceptor {
  static final String LOGGER_NAME = "XERO_DIAGNOSTIC";
  private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> SAFE_QUERY_VALUES = Set.of("page", "pageSize", "summaryOnly", "order", "unitdp");
  private static final List<String> ENVELOPE_KEYS = List.of("pagination", "Pagination", "Invoices", "Contacts",
      "Accounts", "Attachments", "Elements", "ErrorNumber", "Type", "Message", "Detail", "Title");
  private static final List<String> PAGINATION_KEYS = List.of("page", "pageSize", "pageCount", "itemCount");

  private final boolean enabled;
  private final boolean includeBodies;
  private final int maxBodyChars;

  public XeroRequestDiagnostics(boolean enabled, boolean includeBodies, int maxBodyChars) {
    this.enabled = enabled;
    this.includeBodies = enabled && includeBodies;
    this.maxBodyChars = Math.max(0, maxBodyChars);
    if (this.includeBodies) LOG.warn("XERO_DIAGNOSTIC include-bodies is on: response bodies are logged. Synthetic Test data only.");
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    if (!enabled) return execution.execute(request, body);
    long started = System.nanoTime();
    ClientHttpResponse response = execution.execute(request, body);
    byte[] bytes = response.getBody().readAllBytes();
    try {
      LOG.info("{}", summary(request.getMethod().name(), request.getURI(), response.getStatusCode().value(), bytes,
          (System.nanoTime() - started) / 1_000_000, includeBodies, maxBodyChars));
    } catch (IOException | RuntimeException failure) {
      LOG.info("XERO_DIAGNOSTIC summary unavailable: {}", failure.getClass().getSimpleName());
    }
    // The caller still receives the full body; the diagnostic read must not change the outcome.
    return new Replayable(response, bytes);
  }

  /** One line per request. Pure function so the redaction rules can be tested directly. */
  static String summary(String method, URI uri, int status, byte[] body, long millis, boolean includeBodies, int maxBodyChars) {
    var out = new StringBuilder("method=").append(method)
        .append(" path=").append(uri.getRawPath())
        .append(" query=[").append(safeQuery(uri.getRawQuery())).append(']')
        .append(" status=").append(status)
        .append(" ms=").append(millis)
        .append(" bytes=").append(body.length)
        .append(" shape=[").append(shape(body)).append(']');
    if (includeBodies) {
      String text = new String(body, StandardCharsets.UTF_8);
      out.append(" body=").append(text.length() > maxBodyChars ? text.substring(0, maxBodyChars) + "...(truncated)" : text);
    }
    return out.toString();
  }

  static String safeQuery(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    var parts = new ArrayList<String>();
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String name = eq < 0 ? pair : pair.substring(0, eq);
      parts.add(SAFE_QUERY_VALUES.contains(name) ? pair : name + "=present");
    }
    return String.join(" ", parts);
  }

  /** Structure only: field counts, allowlisted envelope keys, array sizes and pagination numbers. */
  static String shape(byte[] body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (RuntimeException notJson) {
      return "non-json";
    }
    if (root == null || !root.isObject()) return "not-an-object";
    var parts = new ArrayList<String>();
    parts.add("fields=" + root.size());
    for (String key : ENVELOPE_KEYS) {
      JsonNode node = root.get(key);
      if (node == null) continue;
      if (node.isArray()) parts.add(key + "=array(" + node.size() + ")");
      else if (node.isObject() && PAGINATION_KEYS.stream().anyMatch(node::has)) parts.add(key + "=" + paginationNumbers(node));
      else if (node.isNumber()) parts.add(key + "=" + node.asText());
      else parts.add(key + "=present");
    }
    return String.join(" ", parts);
  }

  private static String paginationNumbers(JsonNode pagination) {
    return PAGINATION_KEYS.stream().map(k -> k + "=" + pagination.path(k).asText())
        .collect(Collectors.joining(",", "{", "}"));
  }

  /** Replays the bytes already read, so the RestClient caller can read the body as normal. */
  private static final class Replayable implements ClientHttpResponse {
    private final ClientHttpResponse delegate;
    private final byte[] body;

    Replayable(ClientHttpResponse delegate, byte[] body) {
      this.delegate = delegate;
      this.body = body;
    }

    @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
    @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
    @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
    @Override public void close() { delegate.close(); }
  }
}
