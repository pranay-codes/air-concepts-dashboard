package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.invoices.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Provider-owned fields only; dashboard assumptions never enter this read model. */
public record ForecastSource(UUID id, InvoiceType type, String number, String reference, String contact,
    InvoiceStatus status, LocalDate issueDate, LocalDate dueDate, LocalDate expectedDate, String currency,
    BigDecimal total, BigDecimal totalTax, BigDecimal amountDue, BigDecimal amountPaid, BigDecimal amountCredited,
    Instant updatedAt) {
  public static final String OUTSTANDING_STATUS = "AUTHORISED";
  public boolean outstanding() { return status.name().equals(OUTSTANDING_STATUS) && amountDue.signum() > 0; }
  public record Limits(Integer day, Integer minute, Integer appMinute) {}
  public record Read(ForecastSource document,Limits limits) {}
  public record Page(List<ForecastSource> documents, int pageCount, long itemCount, Limits limits) {}
}
