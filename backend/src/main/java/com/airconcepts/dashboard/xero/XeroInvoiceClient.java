package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.web.client.*;

/** Accounting reads only. The caller supplies an authorised server-side tenant context. */
public class XeroInvoiceClient {
  private final RestClient api;
  private final Clock clock;
  private static final Pattern XERO_DATE = Pattern.compile("/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/");

  public XeroInvoiceClient(RestClient api, Clock clock) { this.api = api; this.clock = clock; }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Envelope(@JsonProperty("Invoices") List<InvoiceBody> invoices, Pagination pagination) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Pagination(Integer page, Integer pageSize, Integer pageCount, Long itemCount) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Contact(@JsonProperty("Name") String name) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record InvoiceBody(@JsonProperty("InvoiceID") UUID id, @JsonProperty("Type") InvoiceType type,
      @JsonProperty("InvoiceNumber") String number, @JsonProperty("Reference") String reference,
      @JsonProperty("Contact") Contact contact, @JsonProperty("DateString") String dateString,
      @JsonProperty("Date") String date, @JsonProperty("DueDateString") String dueString,
      @JsonProperty("DueDate") String due, @JsonProperty("Total") BigDecimal total,
      @JsonProperty("AmountDue") BigDecimal amountDue, @JsonProperty("CurrencyCode") String currency,
      @JsonProperty("Status") InvoiceStatus status, @JsonProperty("SubTotal") BigDecimal subtotal,
      @JsonProperty("TotalTax") BigDecimal tax, @JsonProperty("AmountPaid") BigDecimal paid,
      @JsonProperty("AmountCredited") BigDecimal credited, @JsonProperty("LineItems") List<LineBody> lines,
      @JsonProperty("Payments") List<PaymentBody> payments, @JsonProperty("LineAmountTypes") String taxBasis) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LineBody(@JsonProperty("Description") String description, @JsonProperty("Quantity") BigDecimal quantity,
      @JsonProperty("UnitAmount") BigDecimal unit, @JsonProperty("LineAmount") BigDecimal amount,
      @JsonProperty("TaxAmount") BigDecimal tax) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PaymentBody(@JsonProperty("PaymentID") UUID id, @JsonProperty("Date") String date,
      @JsonProperty("Amount") BigDecimal amount, @JsonProperty("Reference") String reference) {}

  public InvoiceViews.Page list(TenantAccess access, InvoiceQuery query) {
    var filter = XeroInvoiceQuery.from(query, InvoiceDates.today(clock));
    var body = call(api.get().uri(b -> b.path("/api.xro/2.0/Invoices")
        .queryParam("where", "{where}").queryParam("order", "{order}")
        .queryParam("page", filter.page()).queryParam("pageSize", filter.pageSize())
        .build(Map.of("where", filter.where(), "order", filter.order()))), access);
    var p = body.pagination();
    if (p == null || p.page() == null || p.pageSize() == null || p.pageCount() == null || p.itemCount() == null
        || p.page() != query.page() || p.pageSize() != query.pageSize() || p.itemCount() < 0 || p.pageCount() < 0) throw malformed();
    long pages = p.itemCount() / p.pageSize() + (p.itemCount() % p.pageSize() == 0 ? 0 : 1);
    if (p.pageCount() != pages && !(pages == 0 && p.pageCount() == 1)) throw malformed();
    long expected = Math.max(0L, Math.min(p.pageSize(), p.itemCount() - ((long) p.page() - 1) * p.pageSize()));
    if (body.invoices().size() != expected) throw malformed();
    var ids = new HashSet<UUID>();
    var rows = body.invoices().stream().map(i -> {
      var row = summary(i, query.type());
      if (!ids.add(row.id())) throw malformed();
      return row;
    }).toList();
    return new InvoiceViews.Page(rows, p.page(), p.pageSize(), p.pageCount(), p.itemCount());
  }

  public InvoiceViews.Detail detail(TenantAccess access, InvoiceType type, UUID id) {
    var body = call(api.get().uri("/api.xro/2.0/Invoices/{id}?unitdp=4", id), access);
    if (body.invoices().size() != 1) throw malformed();
    var i = body.invoices().getFirst();
    var summary = summary(i, type);
    if (!id.equals(summary.id()) || i.lines() == null) throw malformed();
    var lines = i.lines().stream().map(line -> {
      if (line == null) throw malformed();
      return new InvoiceViews.Line(line.description(), line.quantity(), line.unit(), line.amount(), line.tax());
    }).toList();
    var payments = i.payments() == null ? List.<InvoiceViews.Payment>of() : i.payments().stream().map(p -> {
      if (p == null || p.id() == null || p.amount() == null) throw malformed();
      return new InvoiceViews.Payment(p.id(), date(p.date(), false), p.amount(), p.reference());
    }).toList();
    InvoiceViews.TaxBasis taxBasis = i.taxBasis() == null ? null : switch (i.taxBasis()) {
      case "Exclusive" -> InvoiceViews.TaxBasis.EXCLUSIVE;
      case "Inclusive" -> InvoiceViews.TaxBasis.INCLUSIVE;
      case "NoTax" -> InvoiceViews.TaxBasis.NO_TAX;
      default -> throw malformed();
    };
    return new InvoiceViews.Detail(summary, i.subtotal(), i.tax(), i.paid(), i.credited(), lines, payments, taxBasis);
  }

  private InvoiceViews.Summary summary(InvoiceBody i, InvoiceType type) {
    if (i == null || i.id() == null || i.type() != type || i.contact() == null
        || i.contact().name() == null || i.status() == null) throw malformed();
    LocalDate date = date(i.dateString() != null ? i.dateString() : i.date(), false);
    LocalDate due = date(i.dueString() != null ? i.dueString() : i.due(), true);
    return new InvoiceViews.Summary(i.id(), i.type(), i.number(), i.reference(), i.contact().name(), date, due,
        i.total(), i.amountDue(), i.currency(), i.status(),
        InvoiceDates.isOverdue(i.status(), due, i.amountDue(), InvoiceDates.today(clock)), null);
  }

  private Envelope call(RestClient.RequestHeadersSpec<?> request, TenantAccess access) {
      var body = XeroReadErrors.call(() -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
          .header("xero-tenant-id", access.tenantId().toString()).accept(MediaType.APPLICATION_JSON)
          .retrieve().body(Envelope.class), clock);
      if (body == null || body.invoices() == null) throw malformed();
      return body;
  }

  private static LocalDate date(String value, boolean optional) {
    if (value == null && optional) return null;
    if (value == null) throw malformed();
    try {
      var matcher = XERO_DATE.matcher(value);
      if (matcher.matches()) return BusinessTime.dateOf(Instant.ofEpochMilli(Long.parseLong(matcher.group(1))));
      if (value.length() == 10) return LocalDate.parse(value);
      return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
    } catch (RuntimeException invalid) { throw malformed(); }
  }

  private static ProviderFailure malformed() {
    return XeroReadErrors.malformed();
  }
}
