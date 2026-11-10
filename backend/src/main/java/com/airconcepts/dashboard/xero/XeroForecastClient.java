package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.invoices.*;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/** Read-only forecast adapter. Caller checks connection health and persists safe operation state. */
public class XeroForecastClient {
  public static final int PAGE_SIZE = 100;
  private static final Pattern MS_DATE = Pattern.compile("/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/");
  public interface Pacer { void await(UUID tenant); }
  private final RestClient api;
  private final Clock clock;
  private final Pacer pacer;
  public XeroForecastClient(RestClient api, Clock clock, Pacer pacer) { this.api = api; this.clock = clock; this.pacer = pacer; }

  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Pagination(Integer page, Integer pageSize, Integer pageCount, Long itemCount) {}
  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Envelope(@JsonProperty("Invoices") List<Body> invoices, Pagination pagination) {}
  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Contact(@JsonProperty("Name") String name) {}
  @JsonIgnoreProperties(ignoreUnknown=true)
  private record Body(@JsonProperty("InvoiceID") UUID id, @JsonProperty("Type") InvoiceType type,
      @JsonProperty("InvoiceNumber") String number, @JsonProperty("Reference") String reference,
      @JsonProperty("Contact") Contact contact, @JsonProperty("Status") InvoiceStatus status,
      @JsonProperty("DateString") String dateString, @JsonProperty("Date") String date,
      @JsonProperty("DueDateString") String dueString, @JsonProperty("DueDate") String due,
      @JsonProperty("ExpectedPaymentDate") String expected, @JsonProperty("PlannedPaymentDate") String planned,
      @JsonProperty("CurrencyCode") String currency, @JsonProperty("Total") BigDecimal total,
      @JsonProperty("TotalTax") BigDecimal tax, @JsonProperty("AmountDue") BigDecimal amountDue,
      @JsonProperty("AmountPaid") BigDecimal paid, @JsonProperty("AmountCredited") BigDecimal credited,
      @JsonProperty("UpdatedDateUTC") String updated) {}

  public ForecastSource.Page page(TenantAccess access, InvoiceType type, int page, Instant modifiedSince) {
    if (page < 1) throw new IllegalArgumentException("Page must be positive");
    String where = "Type==\"" + type.name() + "\"" + (modifiedSince == null
        ? " AND Status==\"" + ForecastSource.OUTSTANDING_STATUS + "\" AND AmountDue>0" : "");
    var request = api.get().uri(b -> b.path("/api.xro/2.0/Invoices").queryParam("where", "{where}")
        .queryParam("order", "{order}").queryParam("page", page).queryParam("pageSize", PAGE_SIZE)
        .queryParam("summaryOnly", true).build(Map.of("where", where, "order", "UpdatedDateUTC ASC,InvoiceID ASC")));
    if (modifiedSince != null) request.header("If-Modified-Since",
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC).format(modifiedSince));
    var response = call(request, access);
    var body = response.getBody(); var p = body.pagination();
    if (p == null || p.page() == null || p.pageSize() == null || p.pageCount() == null || p.itemCount() == null
        || p.page() != page || p.pageSize() != PAGE_SIZE || p.itemCount() < 0 || p.pageCount() < 0) throw XeroReadErrors.malformed();
    long pages = p.itemCount() / PAGE_SIZE + (p.itemCount() % PAGE_SIZE == 0 ? 0 : 1);
    if (p.pageCount() != pages && !(pages == 0 && p.pageCount() == 1)) throw XeroReadErrors.malformed();
    long expected = Math.max(0, Math.min(PAGE_SIZE, p.itemCount() - (long) (page - 1) * PAGE_SIZE));
    if (body.invoices().size() != expected) throw XeroReadErrors.malformed();
    Set<UUID> ids = new HashSet<>();
    var documents = body.invoices().stream().map(row -> {
      var source = source(row, type);
      if (!ids.add(source.id()) || (modifiedSince == null && !source.outstanding())) throw XeroReadErrors.malformed();
      return source;
    }).toList();
    var h = response.getHeaders();
    return new ForecastSource.Page(documents, p.pageCount(), p.itemCount(), new ForecastSource.Limits(
        limit(h, "X-DayLimit-Remaining"), limit(h, "X-MinLimit-Remaining"), limit(h, "X-AppMinLimit-Remaining")));
  }

  public ForecastSource detail(TenantAccess access, InvoiceType type, UUID id) {
    return detailWithLimits(access,type,id).document();
  }
  public ForecastSource.Read detailWithLimits(TenantAccess access, InvoiceType type, UUID id) {
    var response=call(api.get().uri("/api.xro/2.0/Invoices/{id}", id), access);var body=response.getBody();
    if (body.invoices().size() != 1) throw XeroReadErrors.malformed();
    var result = source(body.invoices().getFirst(), type);
    if (!result.id().equals(id)) throw XeroReadErrors.malformed();
    var headers=response.getHeaders();
    return new ForecastSource.Read(result,new ForecastSource.Limits(limit(headers,"X-DayLimit-Remaining"),limit(headers,"X-MinLimit-Remaining"),limit(headers,"X-AppMinLimit-Remaining")));
  }

  private ResponseEntity<Envelope> call(RestClient.RequestHeadersSpec<?> request, TenantAccess access) {
    pacer.await(access.tenantId());
    var response = XeroReadErrors.call(() -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
        .header("xero-tenant-id", access.tenantId().toString()).accept(MediaType.APPLICATION_JSON)
        .retrieve().toEntity(Envelope.class), clock);
    if (response.getBody() == null || response.getBody().invoices() == null) throw XeroReadErrors.malformed();
    return response;
  }
  private static Integer limit(HttpHeaders headers, String key) {
    String value = headers.getFirst(key);
    if (value == null) return null;
    try { int n = Integer.parseInt(value); if (n < 0) throw XeroReadErrors.malformed(); return n; }
    catch (NumberFormatException invalid) { throw XeroReadErrors.malformed(); }
  }
  private static ForecastSource source(Body b, InvoiceType type) {
    if (b == null || b.id() == null || b.type() != type || b.status() == null || b.contact() == null
        || b.contact().name() == null || b.currency() == null || b.amountDue() == null || b.amountDue().signum() < 0)
      throw XeroReadErrors.malformed();
    return new ForecastSource(b.id(), b.type(), b.number(), b.reference(), b.contact().name(), b.status(),
        date(b.dateString() != null ? b.dateString() : b.date(), false),
        date(b.dueString() != null ? b.dueString() : b.due(), true),
        date(type == InvoiceType.ACCREC ? b.expected() : b.planned(), true), b.currency(),
        money(b.total()), money(b.tax()), money(b.amountDue()), money(b.paid()), money(b.credited()), instant(b.updated()));
  }
  private static BigDecimal money(BigDecimal value) {
    try { return value == null ? null : value.setScale(2, java.math.RoundingMode.UNNECESSARY); }
    catch (ArithmeticException invalid) { throw XeroReadErrors.malformed(); }
  }
  private static Instant instant(String value) {
    if (value == null) throw XeroReadErrors.malformed();
    try {
      var m = MS_DATE.matcher(value);
      return m.matches() ? Instant.ofEpochMilli(Long.parseLong(m.group(1))) : Instant.parse(value);
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(); }
  }
  private static LocalDate date(String value, boolean optional) {
    if (value == null && optional) return null;
    if (value == null) throw XeroReadErrors.malformed();
    try {
      var m = MS_DATE.matcher(value);
      if (m.matches()) return instant(value).atOffset(ZoneOffset.UTC).toLocalDate();
      return value.length() == 10 ? LocalDate.parse(value) : LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(); }
  }
}
