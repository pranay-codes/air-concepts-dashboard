package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ProviderFailure;
import tools.jackson.databind.DatabindException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.client.*;

/** Safe common accounting diagnostics: never retain raw provider bodies, URLs or exception causes. */
final class XeroReadErrors {
  private XeroReadErrors() {}
  static <T> T call(Supplier<T> request, Clock clock) {
    try { return request.get(); }
    catch (RestClientResponseException e) { throw response(e.getStatusCode().value(), e.getResponseHeaders(), clock); }
    catch (ResourceAccessException e) { throw unavailable(); }
    catch (UnknownContentTypeException e) { throw malformed("unexpected content type"); }
    catch (RestClientException e) { throw malformed(unreadable(e)); }
  }
  /** Names the JSON location that could not be bound (keys and indexes only), never a value. */
  private static String unreadable(Throwable failure) {
    for (Throwable t = failure, guard = null; t != null && t != guard; guard = t, t = t.getCause()) {
      if (t instanceof DatabindException mapping) {
        var path = new StringBuilder("body unreadable at $");
        for (var ref : mapping.getPath())
          path.append(ref.getPropertyName() != null ? "." + ref.getPropertyName() : "[" + ref.getIndex() + "]");
        return path.toString();
      }
    }
    return "body unreadable";
  }
  static ProviderFailure response(int status, HttpHeaders headers, Clock clock) {
    String code = status == 401 ? "XERO_RECONNECT_REQUIRED" : status == 403 ? "XERO_ORGANISATION_ACCESS_DENIED"
        : status == 429 ? "XERO_RATE_LIMITED" : status == 404 ? "XERO_DOCUMENT_UNAVAILABLE" : "XERO_PROVIDER_FAILED";
    return new ProviderFailure(code, status == 429 ? "Xero has limited requests. Wait before retrying."
        : status == 401 || status == 403 ? "Xero refused access. Ask a Director to check this organisation's connection."
        : "Xero could not return this document or page. Retry or check the connection.", status,
        retryAfter(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER), clock));
  }
  static ProviderFailure unavailable() {
    return new ProviderFailure("XERO_UNAVAILABLE", "Xero did not return a valid response. Retry this request.", 0);
  }
  static ProviderFailure malformed() {
    return new ProviderFailure("XERO_INVALID_RESPONSE", "Xero returned an incomplete or inconsistent response. Retry this request.", 200);
  }
  /** The reason is a fixed phrase chosen by our code (which check failed), never provider content. */
  static ProviderFailure malformed(String reason) {
    return new ProviderFailure("XERO_INVALID_RESPONSE",
        "Xero returned an incomplete or inconsistent response (" + reason + "). Retry this request.", 200);
  }
  private static Integer retryAfter(String value, Clock clock) {
    if (value == null) return null;
    try { return Math.max(0, Integer.parseInt(value.strip())); }
    catch (NumberFormatException nonNumeric) {
      try {
        var duration = Duration.between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        return Math.toIntExact(Math.max(0, duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0)));
      } catch (RuntimeException invalid) { return null; }
    }
  }
}
