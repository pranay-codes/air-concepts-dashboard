package com.airconcepts.dashboard.invoices;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Read-only financial values retain provider precision; absent values are never invented. */
public final class InvoiceViews {
  private InvoiceViews() {}
  public enum TaxBasis { EXCLUSIVE, INCLUSIVE, NO_TAX }
  @Schema(name = "InvoiceSummary", requiredProperties = {"id", "type", "contact", "date", "status", "overdue"})
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Summary(UUID id, InvoiceType type, String number, String reference, String contact,
      LocalDate date, LocalDate dueDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal total,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal amountDue, String currency,
      InvoiceStatus status, boolean overdue, @JsonInclude(JsonInclude.Include.NON_NULL) Owner owner) {
    public Summary withOwner(Owner value) {
      return new Summary(id, type, number, reference, contact, date, dueDate, total, amountDue, currency, status, overdue, value);
    }
  }
  @Schema(name = "InvoiceOwner", requiredProperties = {"id", "displayName", "active"})
  public record Owner(UUID id, String displayName, boolean active) {}
  @Schema(name = "SalesAssignment", requiredProperties = {"invoiceId"})
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Assignment(UUID invoiceId, Owner owner) {}
  @Schema(name = "InvoicePage", requiredProperties = {"items", "page", "pageSize", "pageCount", "itemCount"})
  public record Page(List<Summary> items, int page, int pageSize, int pageCount, long itemCount) {
    public Page { items = List.copyOf(items); }
  }
  @Schema(name = "InvoiceLine")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Line(String description,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal unitAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal lineAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal taxAmount) {}
  @Schema(name = "InvoicePayment")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Payment(UUID id, LocalDate date,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal amount, String reference) {}
  @Schema(name = "InvoiceDetail", requiredProperties = {"invoice", "lines", "payments"})
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Detail(Summary invoice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal tax,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal amountPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", format = "decimal") BigDecimal amountCredited,
      List<Line> lines, List<Payment> payments, TaxBasis taxBasis) {
    public Detail { lines = List.copyOf(lines); payments = List.copyOf(payments); }
  }
}
