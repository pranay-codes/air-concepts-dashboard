package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.airconcepts.dashboard.web.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroAttachmentClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final XeroAttachmentClient client = new XeroAttachmentClient(builder.build(), Clock.systemUTC());
  private final UUID invoice = UUID.randomUUID(), attachment = UUID.randomUUID();
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");

  @Test void frUc15_24_25_ignoresProviderUrlsAndFetchesVerifiedMembershipAtFixedOrigin() {
    metadata("report (1).pdf", "application/pdf", 8);
    server.expect(request -> assertThat(request.getURI().toString())
        .startsWith("https://api.xero.com/api.xro/2.0/Invoices/" + invoice + "/Attachments/")
        .contains("report%20%281%29.pdf"))
        .andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-access"))
        .andRespond(withSuccess("%PDF-1.7", MediaType.APPLICATION_PDF));
    var file = client.file(access, invoice, attachment);
    assertThat(file.previewable()).isTrue();
    assertThat(file.content()).isEqualTo("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    assertThat(file.toString()).doesNotContain("%PDF");
    server.verify();
  }

  @Test void frUc15_25_missingMembershipNeverFetchesArbitraryFile() {
    metadata("report.pdf", "application/pdf", 8);
    assertThatThrownBy(() -> client.file(access, invoice, UUID.randomUUID())).isInstanceOfSatisfying(ApiFailure.class,
        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    server.verify();
  }

  @Test void frUc15_24_htmlSvgAndMislabelledBytesCannotPreview() {
    for (String mime : new String[]{"text/html", "image/svg+xml", "application/pdf"}) {
      server.reset(); metadata("report.pdf", mime, 8);
      server.expect(anything()).andRespond(withSuccess("<script>", MediaType.TEXT_HTML));
      var file = client.file(access, invoice, attachment);
      assertThat(file.previewable()).isFalse();
      assertThat(file.contentType()).isEqualTo("application/octet-stream");
    }
  }

  @Test void frUc15_25_rejectsOversizedTruncatedAndTraversalAttachments() {
    metadata("report.pdf", "application/pdf", 25L * 1024 * 1024 + 1);
    assertThatThrownBy(() -> client.file(access, invoice, attachment)).isInstanceOfSatisfying(ApiFailure.class,
        e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    server.reset(); metadata("report.pdf", "application/pdf", 8);
    server.expect(anything()).andRespond(withSuccess("short", MediaType.APPLICATION_PDF));
    assertThatThrownBy(() -> client.file(access, invoice, attachment)).isInstanceOf(ProviderFailure.class);
    server.reset(); metadata("../report.pdf", "application/pdf", 8);
    assertThatThrownBy(() -> client.file(access, invoice, attachment)).isInstanceOf(ProviderFailure.class);
    server.verify();
  }

  @Test void frUc15_25_binaryErrorsAndRateLimitsNeverBecomeDownloadBodies() {
    metadata("report.pdf", "application/pdf", 8);
    server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30").body("private error"));
    assertThatThrownBy(() -> client.file(access, invoice, attachment)).isInstanceOfSatisfying(ProviderFailure.class, e -> {
      assertThat(e.upstreamStatus()).isEqualTo(429); assertThat(e.retryAfterSeconds()).isEqualTo(30);
      assertThat(e.getMessage()).doesNotContain("private error");
    });
  }

  private void metadata(String filename, String mime, long size) {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Invoices/" + invoice + "/Attachments"))
        .andRespond(withSuccess("{\"Attachments\":[{\"AttachmentID\":\"" + attachment + "\",\"FileName\":\"" + filename
            + "\",\"MimeType\":\"" + mime + "\",\"ContentLength\":\"" + size
            + "\",\"Url\":\"https://attacker.example.invalid/private\"}]}", MediaType.APPLICATION_JSON));
  }

  @Test void frUc15_25_readFailureClosesProviderStreamWithoutExposingItsCause() {
    metadata("report.pdf", "application/pdf", 8);
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var stream = new java.io.InputStream() {
      @Override public int read() throws java.io.IOException { throw new java.io.IOException("private upstream diagnostic"); }
      @Override public void close() { closed.set(true); }
    };
    server.expect(anything()).andRespond(request -> new org.springframework.mock.http.client.MockClientHttpResponse(stream, HttpStatus.OK));
    assertThatThrownBy(() -> client.file(access, invoice, attachment)).isInstanceOfSatisfying(ProviderFailure.class,
        e -> assertThat(e.getCause()).isNull());
    assertThat(closed).isTrue();
  }

  @Test void frUc15_24_rasterPreviewsRequireTheirSignatures() {
    byte[][] contents = {new byte[]{(byte)137,80,78,71,13,10,26,10}, new byte[]{(byte)255,(byte)216,(byte)255},
        "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII), "RIFF0000WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII)};
    String[] types = {"image/png", "image/jpeg", "image/gif", "image/webp"};
    for (int i=0; i<types.length; i++) {
      server.reset(); metadata("image.bin", types[i], contents[i].length);
      server.expect(anything()).andRespond(withSuccess(contents[i], MediaType.APPLICATION_OCTET_STREAM));
      var result = client.file(access, invoice, attachment);
      assertThat(result.previewable()).isTrue();
      assertThat(result.contentType()).isEqualTo(types[i]);
    }
  }
}
