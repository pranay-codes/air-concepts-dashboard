package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ProviderFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/** Documented invoice PUT boundary. Callers must reserve and reconcile an operation before invoking it. */
public final class XeroInvoiceWriteClient {
  public record Receipt(UUID id) {}
  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Envelope(@JsonProperty("Invoices") List<Invoice> invoices) {}
  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Invoice(@JsonProperty("InvoiceID") UUID id, @JsonProperty("Type") String type,
      @JsonProperty("Status") String status, @JsonProperty("HasErrors") Boolean hasErrors) {}
  private record Payload(@JsonValue Map<String,Object> value) {
    @Override public String toString() { return "InvoicePayload[redacted]"; }
  }
  private final RestClient api;
  private final Clock clock;
  private final XeroForecastClient.Pacer pacer;
  private final boolean idempotencyVerified;
  public XeroInvoiceWriteClient(RestClient api, Clock clock, XeroForecastClient.Pacer pacer, boolean idempotencyVerified) {
    this.api=Objects.requireNonNull(api); this.clock=Objects.requireNonNull(clock);
    this.pacer=Objects.requireNonNull(pacer); this.idempotencyVerified=idempotencyVerified;
  }
  public Receipt create(TenantAccess access, DraftInvoice draft, UUID operation) {
    Objects.requireNonNull(access); Objects.requireNonNull(draft); Objects.requireNonNull(operation);
    XeroScopes.require(access.grantedScopes(),XeroScopes.INVOICES_WRITE);
    pacer.await(access.tenantId());
    try {
      var request=api.put().uri("/api.xro/2.0/Invoices?unitdp=4")
          .header(HttpHeaders.AUTHORIZATION,"Bearer "+access.accessToken())
          .header("xero-tenant-id",access.tenantId().toString())
          .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
      if (idempotencyVerified) request.header("Idempotency-Key",operation.toString());
      var response=XeroReadErrors.call(() -> request.body(new Payload(payload(draft))).retrieve().body(Envelope.class),clock);
      if (response==null || response.invoices()==null || response.invoices().size()!=1) throw XeroReadErrors.malformed();
      var invoice=response.invoices().getFirst();
      if (invoice==null || invoice.id()==null || !"ACCREC".equals(invoice.type()) || !"DRAFT".equals(invoice.status())
          || Boolean.TRUE.equals(invoice.hasErrors())) throw XeroReadErrors.malformed();
      return new Receipt(invoice.id());
    } catch (ProviderFailure failure) {
      throw new ProviderFailure(failure.code(),"The invoice outcome could not be confirmed. Review before retrying.",
          failure.upstreamStatus(),failure.retryAfterSeconds());
    }
  }
  private static Map<String,Object> payload(DraftInvoice draft) {
    Map<String,Object> invoice=new LinkedHashMap<>();
    invoice.put("Type","ACCREC"); invoice.put("Status","DRAFT");
    invoice.put("Contact",Map.of("ContactID",draft.contactId()));
    invoice.put("Date",draft.date().toString()); invoice.put("Reference",draft.reference());
    invoice.put("CurrencyCode",draft.currency()); invoice.put("LineAmountTypes",draft.lineAmountTypes());
    put(invoice,"BrandingThemeID",draft.brandingThemeId());
    List<Map<String,Object>> lines=new ArrayList<>();
    for (var source : draft.lines()) {
      Map<String,Object> line=new LinkedHashMap<>();
      put(line,"Description",source.description()); line.put("Quantity",source.quantity()); line.put("UnitAmount",source.unitAmount());
      put(line,"ItemCode",source.itemCode()); put(line,"AccountCode",source.accountCode()); put(line,"TaxType",source.taxType());
      put(line,"DiscountRate",source.discountRate());
      if (!source.tracking().isEmpty()) {
        List<Map<String,Object>> tracking=new ArrayList<>();
        for (var sourceTracking : source.tracking()) {
          Map<String,Object> value=new LinkedHashMap<>();
          put(value,"TrackingCategoryID",sourceTracking.categoryId()); put(value,"TrackingOptionID",sourceTracking.optionId());
          put(value,"Name",sourceTracking.name()); put(value,"Option",sourceTracking.option()); tracking.add(value);
        }
        line.put("Tracking",tracking);
      }
      lines.add(line);
    }
    invoice.put("LineItems",lines);
    return Map.of("Invoices",List.of(invoice));
  }
  private static void put(Map<String,Object> target,String name,Object value) { if (value!=null) target.put(name,value); }
}
