package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DraftInvoiceTest {
  private final UUID quoteId=UUID.randomUUID(),contact=UUID.randomUUID(),brand=UUID.randomUUID();
  private XeroQuoteClient.Quote quote(List<XeroQuoteClient.Line> lines) {
    return new XeroQuoteClient.Quote(quoteId,"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,contact,"Synthetic customer",
        LocalDate.of(2026,11,1),null,Instant.parse("2026-11-20T00:00:00Z"),new BigDecimal("230.00"),"NZD","Exclusive",lines,brand);
  }
  @Test void gl17_copiesFinancialFieldsAndTrackingWithoutRounding() {
    var tracking=new XeroQuoteClient.Tracking(UUID.randomUUID(),UUID.randomUUID(),"Region","North");
    var line=new XeroQuoteClient.Line("Installation",new BigDecimal("2.5000"),new BigDecimal("40.1234"),null,null,
        "ITEM","200","OUTPUT2",new BigDecimal("7.5"),List.of(tracking));
    var draft=DraftInvoice.fromQuote(quote(List.of(line)),"J-1",LocalDate.of(2026,11,21));
    assertThat(draft.reference()).isEqualTo("J-1 / Q-1");
    assertThat(draft.contactId()).isEqualTo(contact);
    assertThat(draft.brandingThemeId()).isEqualTo(brand);
    assertThat(draft.lines()).singleElement().satisfies(copied -> {
      assertThat(copied.quantity()).isEqualByComparingTo("2.5000");
      assertThat(copied.unitAmount()).isEqualByComparingTo("40.1234");
      assertThat(copied.discountRate()).isEqualByComparingTo("7.5");
      assertThat(copied.tracking()).containsExactly(tracking);
      assertThat(copied.accountCode()).isEqualTo("200");
    });
  }
  @Test void gl17_missingPriceOrContactCannotCreateAnIncompleteDraft() {
    var line=new XeroQuoteClient.Line("Work",BigDecimal.ONE,null,null,null,null,null,null,null,List.of());
    assertThatThrownBy(() -> DraftInvoice.fromQuote(quote(List.of(line)),"J-1",LocalDate.now())).isInstanceOf(ApiFailure.class);
  }
  @Test void gl17_reviewFingerprintChangesWhenFinancialDataChanges() {
    var line=new XeroQuoteClient.Line("Work",BigDecimal.ONE,new BigDecimal("40.1234"),null,null,null,"200","OUTPUT2",null,List.of());
    var changed=new XeroQuoteClient.Line("Work",BigDecimal.ONE,new BigDecimal("40.1235"),null,null,null,"200","OUTPUT2",null,List.of());
    var date=LocalDate.of(2026,11,21);
    var first=DraftInvoice.fromQuote(quote(List.of(line)),"J-1",date);
    assertThat(first.fingerprint()).matches("[0-9a-f]{64}");
    assertThat(first.fingerprint()).isEqualTo(DraftInvoice.fromQuote(quote(List.of(line)),"J-1",date).fingerprint());
    assertThat(first.fingerprint()).isNotEqualTo(DraftInvoice.fromQuote(quote(List.of(changed)),"J-1",date).fingerprint());
  }
}
