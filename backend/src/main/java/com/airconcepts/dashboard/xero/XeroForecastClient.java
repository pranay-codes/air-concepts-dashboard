package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.time.BusinessTime;
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
    if (p == null) throw XeroReadErrors.malformed("pagination missing");
    if (p.page() == null || p.pageSize() == null || p.pageCount() == null || p.itemCount() == null)
      throw XeroReadErrors.malformed("pagination fields missing");
    if (p.page() != page || p.pageSize() != PAGE_SIZE) throw XeroReadErrors.malformed("pagination page or pageSize differs from request");
    if (p.itemCount() < 0 || p.pageCount() < 0) throw XeroReadErrors.malformed("pagination counts negative");
    long pages = p.itemCount() / PAGE_SIZE + (p.itemCount() % PAGE_SIZE == 0 ? 0 : 1);
    if (p.pageCount() != pages && !(pages == 0 && p.pageCount() == 1)) throw XeroReadErrors.malformed("pageCount inconsistent with itemCount");
    long expected = Math.max(0, Math.min(PAGE_SIZE, p.itemCount() - (long) (page - 1) * PAGE_SIZE));
    if (body.invoices().size() != expected) throw XeroReadErrors.malformed("row count differs from pagination itemCount");
    Set<UUID> ids = new HashSet<>();
    var documents = body.invoices().stream().map(row -> {
      var source = source(row, type);
      if (!ids.add(source.id())) throw XeroReadErrors.malformed("duplicate InvoiceID in page");
      if (modifiedSince == null && !source.outstanding()) throw XeroReadErrors.malformed("document is not outstanding despite the filter");
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
    if (body.invoices().size() != 1) throw XeroReadErrors.malformed("detail did not return exactly one document");
    var result = source(body.invoices().getFirst(), type);
    if (!result.id().equals(id)) throw XeroReadErrors.malformed("detail returned a different InvoiceID");
    var headers=response.getHeaders();
    return new ForecastSource.Read(result,new ForecastSource.Limits(limit(headers,"X-DayLimit-Remaining"),limit(headers,"X-MinLimit-Remaining"),limit(headers,"X-AppMinLimit-Remaining")));
  }

  private ResponseEntity<Envelope> call(RestClient.RequestHeadersSpec<?> request, TenantAccess access) {
    pacer.await(access.tenantId());
    var response = XeroReadErrors.call(() -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
        .header("xero-tenant-id", access.tenantId().toString()).accept(MediaType.APPLICATION_JSON)
        .retrieve().toEntity(Envelope.class), clock);
    if (response.getBody() == null || response.getBody().invoices() == null) throw XeroReadErrors.malformed("Invoices array missing");
    return response;
  }
  private static Integer limit(HttpHeaders headers, String key) {
    String value = headers.getFirst(key);
    if (value == null) return null;
    try { int n = Integer.parseInt(value); if (n < 0) throw XeroReadErrors.malformed(key + " header negative"); return n; }
    catch (NumberFormatException invalid) { throw XeroReadErrors.malformed(key + " header not numeric"); }
  }
  /** Reasons name our own field labels only; provider values never enter a diagnostic. */
  private static ForecastSource source(Body b, InvoiceType type) {
    if (b == null) throw XeroReadErrors.malformed("null invoice row");
    if (b.id() == null) throw XeroReadErrors.malformed("InvoiceID missing");
    if (b.type() != type) throw XeroReadErrors.malformed("Type differs from the requested type");
    if (b.status() == null) throw XeroReadErrors.malformed("Status missing");
    if (b.contact() == null || b.contact().name() == null) throw XeroReadErrors.malformed("Contact.Name missing");
    if (b.currency() == null) throw XeroReadErrors.malformed("CurrencyCode missing");
    if (b.amountDue() == null) throw XeroReadErrors.malformed("AmountDue missing");
    if (b.amountDue().signum() < 0) throw XeroReadErrors.malformed("AmountDue negative");
    return new ForecastSource(b.id(), b.type(), b.number(), b.reference(), b.contact().name(), b.status(),
        date(b.dateString() != null ? b.dateString() : b.date(), false, "Date"),
        date(b.dueString() != null ? b.dueString() : b.due(), true, "DueDate"),
        date(type == InvoiceType.ACCREC ? b.expected() : b.planned(), true,
            type == InvoiceType.ACCREC ? "ExpectedPaymentDate" : "PlannedPaymentDate"), b.currency(),
        money(b.total(), "Total"), money(b.tax(), "TotalTax"), money(b.amountDue(), "AmountDue"),
        money(b.paid(), "AmountPaid"), money(b.credited(), "AmountCredited"), instant(b.updated(), "UpdatedDateUTC"));
  }
  private static BigDecimal money(BigDecimal value, String field) {
    try { return value == null ? null : value.setScale(2, java.math.RoundingMode.UNNECESSARY); }
    catch (ArithmeticException invalid) { throw XeroReadErrors.malformed(field + " has more than two decimals"); }
  }
  private static Instant instant(String value, String field) {
    if (value == null) throw XeroReadErrors.malformed(field + " missing");
    try {
      var m = MS_DATE.matcher(value);
      if (m.matches()) return Instant.ofEpochMilli(Long.parseLong(m.group(1)));
      // Xero's UTC timestamps are documented as /Date()/; tolerate an ISO text with or without an offset, read as UTC.
      return value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$") ? OffsetDateTime.parse(value).toInstant()
          : LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(field + " unparseable"); }
  }
  private static LocalDate date(String value, boolean optional, String field) {
    if (value == null && optional) return null;
    if (value == null) throw XeroReadErrors.malformed(field + " missing");
    try {
      var m = MS_DATE.matcher(value);
      if (m.matches()) return BusinessTime.dateOf(instant(value, field));
      return value.length() == 10 ? LocalDate.parse(value) : LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(field + " unparseable"); }
  }
}
