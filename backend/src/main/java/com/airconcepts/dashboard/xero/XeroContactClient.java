package com.airconcepts.dashboard.xero;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Read-only contact details. Address types are retained; no address is assumed to be a job site. */
public class XeroContactClient {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Address(@JsonProperty("AddressType") String type, @JsonProperty("AddressLine1") String line1,
      @JsonProperty("AddressLine2") String line2, @JsonProperty("AddressLine3") String line3,
      @JsonProperty("AddressLine4") String line4, @JsonProperty("City") String city,
      @JsonProperty("Region") String region, @JsonProperty("PostalCode") String postalCode,
      @JsonProperty("Country") String country, @JsonProperty("AttentionTo") String attentionTo) {
    @Override public String toString() { return "ContactAddress[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Phone(@JsonProperty("PhoneType") String type, @JsonProperty("PhoneNumber") String number,
      @JsonProperty("PhoneAreaCode") String areaCode, @JsonProperty("PhoneCountryCode") String countryCode) {
    @Override public String toString() { return "ContactPhone[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Person(@JsonProperty("FirstName") String firstName, @JsonProperty("LastName") String lastName,
      @JsonProperty("EmailAddress") String email, @JsonProperty("IncludeInEmails") Boolean includeInEmails) {
    @Override public String toString() { return "ContactPerson[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Contact(@JsonProperty("ContactID") UUID id, @JsonProperty("Name") String name,
      @JsonProperty("FirstName") String firstName, @JsonProperty("LastName") String lastName,
      @JsonProperty("EmailAddress") String email, @JsonProperty("Addresses") List<Address> addresses,
      @JsonProperty("Phones") List<Phone> phones, @JsonProperty("ContactPersons") List<Person> persons) {
    public Contact {
      addresses = addresses == null ? null : List.copyOf(addresses);
      phones = phones == null ? null : List.copyOf(phones);
      persons = persons == null ? null : List.copyOf(persons);
    }
    @Override public String toString() { return "XeroContact[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Envelope(@JsonProperty("Contacts") List<Contact> contacts) {}

  private final RestClient api;
  private final Clock clock;
  private final XeroForecastClient.Pacer pacer;
  public XeroContactClient(RestClient api, Clock clock, XeroForecastClient.Pacer pacer) {
    this.api = api; this.clock = clock; this.pacer = pacer;
  }

  public Contact detail(TenantAccess access, UUID contactId) {
    Objects.requireNonNull(contactId, "contactId");
    pacer.await(access.tenantId());
    var response = XeroReadErrors.call(() -> api.get().uri("/api.xro/2.0/Contacts/{id}", contactId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
        .header("xero-tenant-id", access.tenantId().toString()).accept(MediaType.APPLICATION_JSON)
        .retrieve().toEntity(Envelope.class), clock);
    if (response.getBody() == null || response.getBody().contacts() == null || response.getBody().contacts().size() != 1)
      throw XeroReadErrors.malformed("contact detail did not return exactly one record");
    var contact = response.getBody().contacts().getFirst();
    if (contact == null || !contactId.equals(contact.id())) throw XeroReadErrors.malformed("contact detail returned a different ContactID");
    if (contact.name() == null || contact.name().isBlank()) throw XeroReadErrors.malformed("contact name missing");
    return contact;
  }
}
