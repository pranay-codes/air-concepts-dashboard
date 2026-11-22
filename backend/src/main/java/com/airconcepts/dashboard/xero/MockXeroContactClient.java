package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** Tenant-scoped synthetic contacts; never calls a provider. */
public final class MockXeroContactClient extends XeroContactClient {
  private final Map<UUID,Map<UUID,Contact>> rows=new HashMap<>();
  public MockXeroContactClient(RestClient unused, Clock clock, XeroForecastClient.Pacer pacer) { super(unused,clock,pacer); }
  public synchronized void seed(UUID tenant, Contact contact) {
    Objects.requireNonNull(tenant); Objects.requireNonNull(contact); Objects.requireNonNull(contact.id());
    rows.computeIfAbsent(tenant,ignored -> new HashMap<>()).put(contact.id(),contact);
  }
  @Override public synchronized Contact detail(TenantAccess access, UUID contactId) {
    var contact=rows.getOrDefault(access.tenantId(),Map.of()).get(contactId);
    if (contact==null) throw new ApiFailure(HttpStatus.NOT_FOUND,"CONTACT_NOT_FOUND","The test contact is unavailable.");
    return contact;
  }
}
