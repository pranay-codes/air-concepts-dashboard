package com.airconcepts.dashboard.invoices;

import java.time.LocalDate;
import java.util.Objects;

/** Validated, bounded query shared by HTTP and provider adapters. */
public record InvoiceQuery(InvoiceType type, String search, InvoiceStatus status,
    boolean overdue, DateField dateField, LocalDate from, LocalDate to,
    Sort sort, Direction direction, int page, int pageSize) {

  public enum DateField { INVOICE_DATE, DUE_DATE }
  public enum Sort { DATE, DUE_DATE, AMOUNT, STATUS, CONTACT }
  public enum Direction { ASC, DESC }

  public InvoiceQuery {
    Objects.requireNonNull(type, "Invoice type is required");
    Objects.requireNonNull(dateField, "Date field is required");
    Objects.requireNonNull(sort, "Sort is required");
    Objects.requireNonNull(direction, "Direction is required");
    search = search == null ? "" : search.strip();
    if (search.length() > 200 || search.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Search must contain at most 200 printable characters");
    }
    if (page < 1 || pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("Page must be positive and page size between 1 and 100");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Start date must not follow end date");
    }
    // Xero DateTime expressions support ordinary calendar years, not Java's extended range.
    for (LocalDate date : new LocalDate[] {from, to}) {
      if (date != null && (date.getYear() < 1 || date.getYear() > 9998)) {
        throw new IllegalArgumentException("Date must be between years 1 and 9998");
      }
    }
  }

  public static InvoiceQuery defaults(InvoiceType type) {
    return new InvoiceQuery(type, null, null, false, DateField.INVOICE_DATE,
        null, null, Sort.DATE, Direction.DESC, 1, 25);
  }
}
