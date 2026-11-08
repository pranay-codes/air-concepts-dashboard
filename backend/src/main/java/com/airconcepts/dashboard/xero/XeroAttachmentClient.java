package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.invoices.AttachmentViews;
import com.airconcepts.dashboard.web.ApiFailure;
import com.fasterxml.jackson.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/** Resolves UUID membership server-side. Provider URLs and browser filenames are never followed. */
public class XeroAttachmentClient {
  private static final int MAX_BYTES = 25 * 1024 * 1024;
  private static final Set<String> PREVIEW_TYPES = Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");
  private final RestClient api;
  private final Clock clock;
  public XeroAttachmentClient(RestClient api, Clock clock) { this.api = api; this.clock = clock; }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Envelope(@JsonProperty("Attachments") List<AttachmentBody> attachments) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AttachmentBody(@JsonProperty("AttachmentID") UUID id, @JsonProperty("FileName") String name,
      @JsonProperty("MimeType") String type, @JsonProperty("ContentLength") Long size) {}

  public List<AttachmentViews.Attachment> list(TenantAccess access, UUID invoice) {
    var body = XeroReadErrors.call(() -> headers(api.get().uri("/api.xro/2.0/Invoices/{id}/Attachments", invoice), access)
        .accept(MediaType.APPLICATION_JSON).retrieve().body(Envelope.class), clock);
    if (body == null || body.attachments() == null || body.attachments().size() > 100) throw XeroReadErrors.malformed();
    var ids = new HashSet<UUID>(); var names = new HashSet<String>();
    return body.attachments().stream().map(a -> {
      if (a == null || a.id() == null || !safeName(a.name()) || a.size() == null || a.size() < 0
          || !ids.add(a.id()) || !names.add(a.name())) throw XeroReadErrors.malformed();
      String type = safeType(a.type());
      return new AttachmentViews.Attachment(a.id(), a.name(), type, a.size(), a.size() <= MAX_BYTES && PREVIEW_TYPES.contains(type));
    }).toList();
  }

  public AttachmentViews.File file(TenantAccess access, UUID invoice, UUID id) {
    var metadata = list(access, invoice).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "ATTACHMENT_UNAVAILABLE", "This attachment is no longer available on this document. Refresh its details."));
    if (metadata.size() > MAX_BYTES) throw tooLarge();
    byte[] bytes = XeroReadErrors.call(() -> headers(api.get().uri(b -> b
        .path("/api.xro/2.0/Invoices/{invoice}/Attachments/{filename}")
        .build(Map.of("invoice", invoice, "filename", metadata.fileName()))), access)
        .accept(MediaType.APPLICATION_OCTET_STREAM).exchange((request, response) -> {
          if (!response.getStatusCode().is2xxSuccessful()) throw XeroReadErrors.response(response.getStatusCode().value(), response.getHeaders(), clock);
          long size = response.getHeaders().getContentLength();
          if (size > MAX_BYTES) throw tooLarge();
          if (size >= 0 && size != metadata.size()) throw XeroReadErrors.malformed();
          try (var input = response.getBody()) {
            var content = input.readNBytes(MAX_BYTES + 1);
            if (content.length > MAX_BYTES) throw tooLarge();
            if (content.length != metadata.size()) throw XeroReadErrors.malformed();
            return content;
          } catch (IOException unavailable) { throw XeroReadErrors.unavailable(); }
        }), clock);
    boolean preview = metadata.previewable() && signature(metadata.contentType(), bytes);
    return new AttachmentViews.File(metadata.fileName(), preview ? metadata.contentType() : "application/octet-stream", bytes, preview);
  }

  private static RestClient.RequestHeadersSpec<?> headers(RestClient.RequestHeadersSpec<?> request, TenantAccess access) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken()).header("xero-tenant-id", access.tenantId().toString());
  }
  private static boolean safeName(String name) {
    return name != null && !name.isBlank() && name.length() <= 512 && !name.equals(".") && !name.equals("..")
        && name.chars().noneMatch(c -> Character.isISOControl(c) || "<>:\"/\\|?*+".indexOf(c) >= 0);
  }
  private static String safeType(String type) {
    if (type == null) return "application/octet-stream";
    String result = type.strip().toLowerCase(Locale.ROOT);
    return result.length() <= 128 && result.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+") ? result : "application/octet-stream";
  }
  private static boolean signature(String type, byte[] bytes) {
    return switch (type) {
      case "application/pdf" -> starts(bytes, "%PDF-");
      case "image/png" -> bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
      case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
      case "image/gif" -> starts(bytes, "GIF87a") || starts(bytes, "GIF89a");
      case "image/webp" -> bytes.length >= 12 && starts(bytes, "RIFF") && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP");
      default -> false;
    };
  }
  private static boolean starts(byte[] bytes, String prefix) {
    return bytes.length >= prefix.length() && new String(bytes,0,prefix.length(),StandardCharsets.US_ASCII).equals(prefix);
  }
  private static ApiFailure tooLarge() {
    return new ApiFailure(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE", "This attachment exceeds the 25 MiB download limit.");
  }
}
