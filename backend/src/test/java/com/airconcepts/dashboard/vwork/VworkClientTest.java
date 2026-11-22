package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class VworkClientTest {
  private final VworkCredential credential = new VworkCredential("synthetic-key&private");

  @Test void gl15_documentedJobReadParsesGzipWithoutInventingTemplateIdentity() throws Exception {
    var calls = new ArrayList<URI>();
    VworkLiveClient.Transport transport = (uri, headers) -> {
      calls.add(uri);
      assertThat(headers).containsEntry("Accept-Encoding", "gzip");
      return new VworkLiveClient.Response(200, Map.of("Content-Encoding", "gzip"), gzip(job("17", "J-17")));
    };
    var client = new VworkLiveClient(transport, Clock.systemUTC());
    var result = client.job(credential, "17");
    assertThat(result.id()).isEqualTo("17");
    assertThat(result.reference()).isEqualTo("J-17");
    assertThat(result.workerName()).isEqualTo("Synthetic worker");
    assertThat(result.plannedStart()).isEqualTo(Instant.parse("2026-11-19T02:00:00Z"));
    assertThat(result.templateId()).isNull();
    assertThat(result.templateName()).isEqualTo("Free text");
    assertThat(calls.getFirst().getRawQuery()).contains("api_key=synthetic-key%26private");
    assertThat(credential.toString()).doesNotContain("synthetic", "private");
    assertThat(result.toString()).doesNotContain("Synthetic worker", "J-17");
  }

  @Test void gl14_lookupUsesDocumentedThirdPartyIdButDoesNotProveEntity() {
    var client = new VworkLiveClient((uri, headers) -> {
      assertThat(uri.getRawPath()).endsWith("/J%2F17.xml");
      assertThat(uri.getRawQuery()).contains("use_third_party_id=true");
      return new VworkLiveClient.Response(200, Map.of(), job("17", "J/17").getBytes(StandardCharsets.UTF_8));
    }, Clock.systemUTC());
    assertThat(client.findByReference(credential, "J/17")).singleElement()
        .satisfies(job -> assertThat(job.templateId()).isNull());
  }

  @Test void gl26_liveErrorsNeverExposeKeysUrlsOrProviderBodies() {
    var client = new VworkLiveClient((uri, headers) -> { throw new IOException("private " + uri); }, Clock.systemUTC());
    assertThatThrownBy(() -> client.job(credential, "17")).isInstanceOfSatisfying(VworkFailure.class, failure -> {
      assertThat(failure.code()).isEqualTo("VWORK_UNAVAILABLE");
      assertThat(failure.getMessage()).doesNotContain("synthetic", "private", "api_key");
      assertThat(failure.getCause()).isNull();
    });
  }

  @Test void gl26_rejectsExternalEntitiesAndInvalidOrMismatchedIds() {
    var xml = "<!DOCTYPE job [<!ENTITY x SYSTEM 'file:///nonexistent'>]><job><id>17</id><status>assigned</status><worker_name>&x;</worker_name></job>";
    var client = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(200, Map.of(), xml.getBytes(StandardCharsets.UTF_8)), Clock.systemUTC());
    assertThatThrownBy(() -> client.job(credential, "17")).isInstanceOf(VworkFailure.class);
    var mismatch = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(200, Map.of(), job("18", "J-18").getBytes(StandardCharsets.UTF_8)), Clock.systemUTC());
    assertThatThrownBy(() -> mismatch.job(credential, "17")).isInstanceOf(VworkFailure.class);
  }

  @Test void gl13_mockIsDeterministicAndDoesNotExposePriceFields() {
    var first = new MockVworkClient();
    var second = new MockVworkClient();
    var draft = new VworkClient.NewJob("J-1", "template-A", "Synthetic task", List.of(new VworkClient.ScopeLine("Install", "2")));
    var a = first.create(credential, draft);
    var b = second.create(credential, draft);
    assertThat(a.id()).isEqualTo(b.id());
    assertThat(a.templateId()).isEqualTo("template-A");
    assertThat(first.findByReference(credential, "J-1")).containsExactly(a);
    assertThatThrownBy(() -> first.create(credential, draft)).isInstanceOf(VworkFailure.class);
    assertThat(Arrays.stream(VworkClient.NewJob.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("price", "unitAmount", "total", "tax");
  }

  @Test void gl16_mockCanScheduleAndCompleteWithoutEmail() {
    var mock = new MockVworkClient();
    var job = mock.create(credential, new VworkClient.NewJob("J-2", "template-A", "Task", List.of(new VworkClient.ScopeLine("Work", "1"))));
    var start = Instant.parse("2026-11-20T00:00:00Z");
    mock.schedule(job.id(), "Technician", start);
    assertThat(mock.job(credential, job.id()).plannedStart()).isEqualTo(start);
    mock.complete(job.id(), start.plusSeconds(3600));
    assertThat(mock.job(credential, job.id()).status()).isEqualTo(VworkClient.Status.COMPLETED);
    assertThat(mock.job(credential, job.id()).completedAt()).isEqualTo(start.plusSeconds(3600));
  }

  @Test void gl15_listRequiresPaginationMetadataAndRejectsDuplicateJobs() {
    var payload = "<jobs current_page=\"1\" per_page=\"50\" total_pages=\"1\" total_entries=\"1\">" + job("17", "J-17") + "</jobs>";
    var client = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(200, Map.of(), payload.getBytes(StandardCharsets.UTF_8)), Clock.systemUTC());
    var page = client.jobs(credential, 1);
    assertThat(page.totalEntries()).isEqualTo(1);
    assertThat(page.jobs()).singleElement().satisfies(value -> assertThat(value.id()).isEqualTo("17"));
    var duplicate = payload.replace("</jobs>", job("17", "J-17") + "</jobs>");
    var bad = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(200, Map.of(), duplicate.getBytes(StandardCharsets.UTF_8)), Clock.systemUTC());
    assertThatThrownBy(() -> bad.jobs(credential, 1)).isInstanceOf(VworkFailure.class);
  }

  @Test void gl22_404LookupIsEmptyAnd429RetainsRetryWithoutBody() {
    var absent = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(404, Map.of(), new byte[0]), Clock.systemUTC());
    assertThat(absent.findByReference(credential, "J-17")).isEmpty();
    var limited = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(429, Map.of("retry-after", "60"),
        "private response".getBytes(StandardCharsets.UTF_8)), Clock.systemUTC());
    assertThatThrownBy(() -> limited.job(credential, "17")).isInstanceOfSatisfying(VworkFailure.class, failure -> {
      assertThat(failure.code()).isEqualTo("VWORK_RATE_LIMITED");
      assertThat(failure.retryAfterSeconds()).isEqualTo(60);
      assertThat(failure.getMessage()).doesNotContain("private", "synthetic");
    });
  }

  @Test void gl26_liveCreationIsBlockedAndOversizedGzipIsRejected() throws Exception {
    var compressed = gzip("x".repeat(10 * 1024 * 1024 + 1));
    var client = new VworkLiveClient((uri, headers) -> new VworkLiveClient.Response(200,
        Map.of("Content-Encoding", "gzip"), compressed), Clock.systemUTC());
    assertThatThrownBy(() -> client.job(credential, "17")).isInstanceOfSatisfying(VworkFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("VWORK_PROVIDER_FAILED"));
    var draft = new VworkClient.NewJob("J-1", "template-A", "Task", List.of(new VworkClient.ScopeLine("Work", "1")));
    assertThatThrownBy(() -> client.create(credential, draft)).isInstanceOfSatisfying(VworkFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("VWORK_CAPABILITY_UNVERIFIED"));
  }

  private static String job(String id, String reference) {
    return "<job><id>" + id + "</id><status>assigned</status><third_party_id>" + reference
        + "</third_party_id><template_name>Free text</template_name><worker_name>Synthetic worker</worker_name>"
        + "<planned_start_at>2026-11-19T02:00:00Z</planned_start_at></job>";
  }
  private static byte[] gzip(String content) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new GZIPOutputStream(bytes)) { zip.write(content.getBytes(StandardCharsets.UTF_8)); }
    return bytes.toByteArray();
  }
}
