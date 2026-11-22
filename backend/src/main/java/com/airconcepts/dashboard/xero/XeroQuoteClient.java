package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.time.BusinessTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/** Read-only Quotes adapter. A caller must establish access and verify page completeness before syncing. */
public class XeroQuoteClient {
  private static final int MAX_PAGE_SIZE = 100;
  private static final Pattern MS_DATE = Pattern.compile("/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/");
  private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
  public enum Status { DRAFT, SENT, DECLINED, ACCEPTED, INVOICED, DELETED }
  public record Tracking(UUID categoryId, UUID optionId, String name, String option) {}
  public record Line(String description, BigDecimal quantity, BigDecimal unitAmount, BigDecimal lineAmount,
      BigDecimal taxAmount, String itemCode, String accountCode, String taxType, BigDecimal discountRate,
      List<Tracking> tracking) {
    public Line { tracking = tracking == null ? null : List.copyOf(tracking); }
    @Override public String toString() { return "QuoteLine[redacted]"; }
  }
  public record Quote(UUID id, String number, String reference, Status status, UUID contactId, String contactName,
      LocalDate date, LocalDate expiryDate, Instant updatedAt, BigDecimal total, String currency,
      String lineAmountTypes, List<Line> lines, UUID brandingThemeId) {
    public Quote(UUID id, String number, String reference, Status status, UUID contactId, String contactName,
        LocalDate date, LocalDate expiryDate, Instant updatedAt, BigDecimal total, String currency,
        String lineAmountTypes, List<Line> lines) {
      this(id,number,reference,status,contactId,contactName,date,expiryDate,updatedAt,total,currency,lineAmountTypes,lines,null);
    }
    public Quote { lines = lines == null ? null : List.copyOf(lines); }
    @Override public String toString() { return "XeroQuote[redacted]"; }
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Envelope(@JsonProperty("Quotes") List<Body> quotes) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Contact(@JsonProperty("ContactID") UUID id, @JsonProperty("Name") String name) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TrackBody(@JsonProperty("TrackingCategoryID") UUID categoryId,
      @JsonProperty("TrackingOptionID") UUID optionId, @JsonProperty("Name") String name,
      @JsonProperty("Option") String option) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LineBody(@JsonProperty("Description") String description, @JsonProperty("Quantity") BigDecimal quantity,
      @JsonProperty("UnitAmount") BigDecimal unitAmount, @JsonProperty("LineAmount") BigDecimal lineAmount,
      @JsonProperty("TaxAmount") BigDecimal taxAmount, @JsonProperty("ItemCode") String itemCode,
      @JsonProperty("AccountCode") String accountCode, @JsonProperty("TaxType") String taxType,
      @JsonProperty("DiscountRate") BigDecimal discountRate, @JsonProperty("Tracking") List<TrackBody> tracking) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Body(@JsonProperty("QuoteID") UUID id, @JsonProperty("QuoteNumber") String number,
      @JsonProperty("Reference") String reference, @JsonProperty("Status") Status status,
      @JsonProperty("Contact") Contact contact, @JsonProperty("DateString") String dateString,
      @JsonProperty("Date") String date, @JsonProperty("ExpiryDateString") String expiryString,
      @JsonProperty("ExpiryDate") String expiry, @JsonProperty("UpdatedDateUTC") String updated,
      @JsonProperty("Total") BigDecimal total, @JsonProperty("CurrencyCode") String currency,
      @JsonProperty("LineAmountTypes") String lineAmountTypes, @JsonProperty("LineItems") List<LineBody> lines,
      @JsonProperty("BrandingThemeID") UUID brandingThemeId) {}

  private final RestClient api;
  private final Clock clock;
  private final XeroForecastClient.Pacer pacer;
  public XeroQuoteClient(RestClient api, Clock clock, XeroForecastClient.Pacer pacer) {
    this.api = api; this.clock = clock; this.pacer = pacer;
  }

  /** A page is not evidence of a complete sync; the provider does not document Quotes pagination metadata. */
  public List<Quote> page(TenantAccess access, int page, Status status, Instant modifiedSince) {
    if (page < 1) throw new IllegalArgumentException("Page must be positive");
    var request = api.get().uri(builder -> {
      builder.path("/api.xro/2.0/Quotes").queryParam("page", page);
      if (status != null) builder.queryParam("Status", status.name());
      return builder.build();
    });
    if (modifiedSince != null) request.header("If-Modified-Since", MODIFIED.format(modifiedSince));
    var rows = call(request, access);
    if (rows.size() > MAX_PAGE_SIZE) throw XeroReadErrors.malformed("quote page exceeds documented size");
    Set<UUID> ids = new HashSet<>();
    return rows.stream().map(row -> {
      Quote quote = quote(row);
      if (!ids.add(quote.id())) throw XeroReadErrors.malformed("duplicate QuoteID in page");
      if (status != null && quote.status() != status) throw XeroReadErrors.malformed("quote status differs from requested status");
      return quote;
    }).toList();
  }

  public Quote detail(TenantAccess access, UUID quoteId) {
    Objects.requireNonNull(quoteId, "quoteId");
    var rows = call(api.get().uri("/api.xro/2.0/Quotes/{id}", quoteId), access);
    if (rows.size() != 1) throw XeroReadErrors.malformed("quote detail did not return exactly one record");
    var result = quote(rows.getFirst());
    if (!quoteId.equals(result.id())) throw XeroReadErrors.malformed("quote detail returned a different QuoteID");
    return result;
  }

  private List<Body> call(RestClient.RequestHeadersSpec<?> request, TenantAccess access) {
    pacer.await(access.tenantId());
    var response = XeroReadErrors.call(() -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
        .header("xero-tenant-id", access.tenantId().toString()).accept(MediaType.APPLICATION_JSON)
        .retrieve().toEntity(Envelope.class), clock);
    if (response.getBody() == null || response.getBody().quotes() == null) throw XeroReadErrors.malformed("Quotes array missing");
    return response.getBody().quotes();
  }

  private static Quote quote(Body row) {
    if (row == null || row.id() == null) throw XeroReadErrors.malformed("QuoteID missing");
    if (row.number() == null || row.number().isBlank() || row.number().length() > 255)
      throw XeroReadErrors.malformed("QuoteNumber invalid");
    if (row.status() == null) throw XeroReadErrors.malformed("quote status missing");
    String ref = row.reference() == null ? null : row.reference().strip();
    if (ref != null && ref.length() > 4000) throw XeroReadErrors.malformed("quote reference too long");
    if (ref != null && ref.isEmpty()) ref = null;
    List<Line> lines = row.lines() == null ? null : row.lines().stream().map(XeroQuoteClient::line).toList();
    return new Quote(row.id(), row.number(), ref, row.status(), row.contact() == null ? null : row.contact().id(),
        row.contact() == null ? null : row.contact().name(), date(row.dateString() != null ? row.dateString() : row.date(), "Date"),
        date(row.expiryString() != null ? row.expiryString() : row.expiry(), "ExpiryDate"),
        instant(row.updated(), "UpdatedDateUTC"), row.total(), row.currency(), row.lineAmountTypes(), lines,row.brandingThemeId());
  }

  private static Line line(LineBody row) {
    if (row == null) throw XeroReadErrors.malformed("null quote line");
    List<Tracking> tracking = row.tracking() == null ? null : row.tracking().stream().map(t -> {
      if (t == null) throw XeroReadErrors.malformed("null quote tracking entry");
      return new Tracking(t.categoryId(), t.optionId(), t.name(), t.option());
    }).toList();
    return new Line(row.description(), row.quantity(), row.unitAmount(), row.lineAmount(), row.taxAmount(),
        row.itemCode(), row.accountCode(), row.taxType(), row.discountRate(), tracking);
  }

  private static LocalDate date(String value, String field) {
    if (value == null) return null;
    try {
      if (MS_DATE.matcher(value).matches()) return BusinessTime.dateOf(instant(value, field));
      return value.length() == 10 ? LocalDate.parse(value) : LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(field + " unparseable"); }
  }

  private static Instant instant(String value, String field) {
    if (value == null) throw XeroReadErrors.malformed(field + " missing");
    try {
      var match = MS_DATE.matcher(value);
      if (match.matches()) return Instant.ofEpochMilli(Long.parseLong(match.group(1)));
      return value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$") ? OffsetDateTime.parse(value).toInstant()
          : LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException invalid) { throw XeroReadErrors.malformed(field + " unparseable"); }
  }
}
