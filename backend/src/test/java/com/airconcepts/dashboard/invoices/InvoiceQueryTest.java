package com.airconcepts.dashboard.invoices;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceQueryTest {
  @Test
  void frUc15_08_28_defaultsAreBoundedAllDatesAllStatusesAndStableNewestFirst() {
    var query = InvoiceQuery.defaults(InvoiceType.ACCREC);
    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(25);
    assertThat(query.from()).isNull();
    assertThat(query.status()).isNull();
    assertThat(XeroInvoiceQuery.from(query, LocalDate.of(2026, 11, 6)).order())
        .isEqualTo("Date DESC,InvoiceID ASC");
    assertThat(XeroInvoiceQuery.from(query, LocalDate.of(2026, 11, 6)).where())
        .isEqualTo("Type==\"ACCREC\"");
  }

  @Test
  void frUc15_15_26_27_combinesSearchStatusOverdueAndInclusiveDueDateRange() {
    var query = new InvoiceQuery(InvoiceType.ACCPAY, "Acme", InvoiceStatus.AUTHORISED,
        true, InvoiceQuery.DateField.DUE_DATE, LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 11, 6), InvoiceQuery.Sort.AMOUNT, InvoiceQuery.Direction.ASC, 3, 100);
    var provider = XeroInvoiceQuery.from(query, LocalDate.of(2026, 11, 6));
    assertThat(provider.where()).contains("Type==\"ACCPAY\"", "InvoiceNumber.Contains(\"Acme\")",
        "Reference.Contains(\"Acme\")", "Contact.Name.Contains(\"Acme\")",
        "Status==\"AUTHORISED\"", "AmountDue>0", "DueDate<DateTime(2026,11,6)",
        "DueDate>=DateTime(2026,10,1)", "DueDate<DateTime(2026,11,7)");
    assertThat(provider.order()).isEqualTo("Total ASC,InvoiceID ASC");
    assertThat(provider.page()).isEqualTo(3);
    assertThat(provider.pageSize()).isEqualTo(100);
  }

  @Test
  void frUc15_15_searchCannotEscapeItsQuotedLiteral() {
    var query = new InvoiceQuery(InvoiceType.ACCREC, "a\"\\b", null, false,
        InvoiceQuery.DateField.INVOICE_DATE, null, null, InvoiceQuery.Sort.CONTACT,
        InvoiceQuery.Direction.DESC, 1, 25);
    assertThat(XeroInvoiceQuery.from(query, LocalDate.of(2026, 11, 6)).where())
        .contains("Contains(\"a\\\"\\\\b\")");
    assertThatThrownBy(() -> new InvoiceQuery(InvoiceType.ACCREC, "bad\nquery", null, false,
        InvoiceQuery.DateField.INVOICE_DATE, null, null, InvoiceQuery.Sort.DATE,
        InvoiceQuery.Direction.DESC, 1, 25)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void frUc15_08_27_rejectsInvalidPagingRangesAndUnboundedSearch() {
    for (int size : new int[] {0, -1, 101}) {
      assertThatThrownBy(() -> query(1, size, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> query(0, 25, null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> query(1, 25, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> query(1, 25, null, LocalDate.MAX)).isInstanceOf(IllegalArgumentException.class);
  }

  private InvoiceQuery query(int page, int size, LocalDate from, LocalDate to) {
    return new InvoiceQuery(InvoiceType.ACCREC, null, null, false,
        InvoiceQuery.DateField.INVOICE_DATE, from, to, InvoiceQuery.Sort.DATE,
        InvoiceQuery.Direction.DESC, page, size);
  }
}
