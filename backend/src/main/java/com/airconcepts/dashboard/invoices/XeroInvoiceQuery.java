package com.airconcepts.dashboard.invoices;

import java.time.LocalDate;
import java.util.ArrayList;

/** Produces provider-side filters, never post-filters a single fetched page. */
public record XeroInvoiceQuery(String where, String order, int page, int pageSize) {
  public static XeroInvoiceQuery from(InvoiceQuery query, LocalDate today) {
    var clauses = new ArrayList<String>();
    clauses.add("Type==" + literal(query.type().name()));
    if (!query.search().isBlank()) {
      String term = literal(query.search());
      clauses.add("(InvoiceNumber.Contains(" + term + ")||Reference.Contains(" + term
          + ")||Contact.Name.Contains(" + term + "))");
    }
    if (query.status() != null) clauses.add("Status==" + literal(query.status().name()));
    if (query.overdue()) {
      clauses.add("Status==\"AUTHORISED\"");
      clauses.add("AmountDue>0");
      clauses.add("DueDate<" + date(today));
    }
    String dateField = query.dateField() == InvoiceQuery.DateField.DUE_DATE ? "DueDate" : "Date";
    if (query.from() != null) clauses.add(dateField + ">=" + date(query.from()));
    if (query.to() != null) clauses.add(dateField + "<" + date(query.to().plusDays(1)));
    String sort = switch (query.sort()) {
      case DATE -> "Date";
      case DUE_DATE -> "DueDate";
      case AMOUNT -> "Total";
      case STATUS -> "Status";
      case CONTACT -> "Contact.Name";
    };
    return new XeroInvoiceQuery(String.join("&&", clauses),
        sort + " " + query.direction().name() + ",InvoiceID ASC", query.page(), query.pageSize());
  }

  private static String literal(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String date(LocalDate date) {
    return "DateTime(" + date.getYear() + "," + date.getMonthValue() + "," + date.getDayOfMonth() + ")";
  }
}
