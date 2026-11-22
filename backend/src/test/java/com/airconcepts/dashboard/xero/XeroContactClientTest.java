package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.airconcepts.dashboard.web.ProviderFailure;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroContactClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "synthetic-token");
  private final XeroContactClient client = new XeroContactClient(builder.build(), Clock.systemUTC(), tenant -> {});
  private final UUID id = UUID.randomUUID();

  @Test void gl12_preservesContactAddressesAndPhonesWithoutChoosingASite() {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Contacts/" + id))
        .andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    var contact = client.detail(access, id);
    assertThat(contact.name()).isEqualTo("Synthetic customer");
    assertThat(contact.addresses()).hasSize(2);
    assertThat(contact.addresses().getFirst().type()).isEqualTo("STREET");
    assertThat(contact.addresses().get(1).type()).isEqualTo("POBOX");
    assertThat(contact.phones()).singleElement().satisfies(phone -> assertThat(phone.number()).isEqualTo("5551234"));
    assertThat(contact.persons()).singleElement().satisfies(person -> assertThat(person.firstName()).isEqualTo("Alex"));
    assertThat(contact.toString()).doesNotContain("Synthetic", "5551234");
  }

  @Test void gl06_contactIdMustMatchAndErrorsNeverExposeProviderContent() {
    server.expect(anything()).andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.detail(access, UUID.randomUUID())).isInstanceOf(ProviderFailure.class);
    server.reset(); server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", "60").body("private synthetic body"));
    assertThatThrownBy(() -> client.detail(access, id)).isInstanceOfSatisfying(ProviderFailure.class, failure -> {
      assertThat(failure.code()).isEqualTo("XERO_RATE_LIMITED");
      assertThat(failure.retryAfterSeconds()).isEqualTo(60);
      assertThat(failure.getMessage()).doesNotContain("private", "synthetic");
      assertThat(failure.getCause()).isNull();
    });
  }

  private String body() {
    return """
        {"Contacts":[{"ContactID":"%s","Name":"Synthetic customer","FirstName":"Alex",
          "Addresses":[{"AddressType":"STREET","AddressLine1":"Example Street","City":"Auckland"},
                       {"AddressType":"POBOX","AddressLine1":"PO Box 1"}],
          "Phones":[{"PhoneType":"MOBILE","PhoneNumber":"5551234","PhoneAreaCode":"09"}],
          "ContactPersons":[{"FirstName":"Alex","LastName":"Example"}]}]}
        """.formatted(id);
  }
}
