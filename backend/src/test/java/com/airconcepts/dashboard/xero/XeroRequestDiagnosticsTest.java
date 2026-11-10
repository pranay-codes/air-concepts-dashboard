package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.airconcepts.dashboard.xero.XeroRequestDiagnostics.summary;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroRequestDiagnosticsTest {
  private static final URI REQUEST = URI.create("https://api.xero.com/api.xro/2.0/Invoices?where=Contact.Name.Contains(%22Synthetic%22)"
      + "&order=Date%20DESC&page=1&pageSize=100&summaryOnly=true");
  private static final String BODY = "{\"pagination\":{\"page\":1,\"pageSize\":100,\"pageCount\":1,\"itemCount\":2},"
      + "\"Invoices\":[{\"Contact\":{\"Name\":\"Synthetic Customer\"}},{\"Contact\":{\"Name\":\"Other\"}}]}";

  @Test void summaryShowsStructureAndSafeQueryButNoValuesWhenBodiesAreOff() {
    String line = summary("GET", REQUEST, 200, BODY.getBytes(StandardCharsets.UTF_8), 12, false, 4000);
    assertThat(line)
        .contains("method=GET", "path=/api.xro/2.0/Invoices", "status=200", "ms=12", "bytes=",
            "where=present", "order=Date%20DESC", "page=1", "pageSize=100", "summaryOnly=true",
            "fields=2", "pagination={page=1,pageSize=100,pageCount=1,itemCount=2}",
            "Invoices=array(2)")
        .doesNotContain("Synthetic", "Other", "Contact.Name", "body=");
  }

  @Test void summaryIncludesBodyOnlyWhenEnabledAndTruncatesIt() {
    String line = summary("GET", REQUEST, 200, BODY.getBytes(StandardCharsets.UTF_8), 12, true, 20);
    assertThat(line).contains("body={\"pagination\":{\"page...(truncated)")
        .doesNotContain("Other");
  }

  @Test void nonJsonAndErrorBodiesAreDescribedWithoutValues() {
    String html = summary("GET", REQUEST, 500, "<html>secret</html>".getBytes(StandardCharsets.UTF_8), 1, false, 4000);
    assertThat(html).contains("status=500", "shape=[non-json]").doesNotContain("secret");

    String error = summary("POST", REQUEST, 400,
        "{\"ErrorNumber\":10,\"Type\":\"ValidationException\",\"Message\":\"Validation failed for Acme\"}".getBytes(StandardCharsets.UTF_8),
        1, false, 4000);
    assertThat(error).contains("ErrorNumber=10", "Type=present", "Message=present").doesNotContain("Acme");
  }

  @Test void callerStillReceivesTheFullBodyWithDiagnosticsOnOrOff() {
    for (boolean enabled : new boolean[] {true, false}) {
      var builder = RestClient.builder().baseUrl("https://api.xero.com")
          .requestInterceptor(new XeroRequestDiagnostics(enabled, false, 4000));
      var server = MockRestServiceServer.bindTo(builder).build();
      var client = builder.build();
      server.expect(anything()).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
      assertThat(client.get().uri("/api.xro/2.0/Invoices").retrieve().body(String.class)).isEqualTo(BODY);
      server.verify();
    }
  }
}
