package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Immutable copy of the quote values required to construct a sales draft. */
public record DraftInvoice(UUID contactId, String customer, String quoteNumber, String reference, LocalDate date,
    String currency, String lineAmountTypes, UUID brandingThemeId, BigDecimal quoteTotal, List<Line> lines) {
  public record Line(String description, BigDecimal quantity, BigDecimal unitAmount, String itemCode,
      String accountCode, String taxType, BigDecimal discountRate, List<XeroQuoteClient.Tracking> tracking) {
    public Line { tracking=tracking==null?List.of():List.copyOf(tracking); }
    @Override public String toString() { return "InvoiceLine[redacted]"; }
  }
  public DraftInvoice { lines=List.copyOf(lines); }
  @Override public String toString() { return "DraftInvoice[redacted]"; }
  /** A stable, length-delimited digest of exactly the values being reviewed and sent. */
  public String fingerprint() {
    try {
      var digest=MessageDigest.getInstance("SHA-256");
      part(digest,contactId); part(digest,customer); part(digest,quoteNumber); part(digest,reference); part(digest,date);
      part(digest,currency); part(digest,lineAmountTypes); part(digest,brandingThemeId); part(digest,quoteTotal);
      part(digest,lines.size());
      for (var line : lines) {
        part(digest,line.description()); part(digest,line.quantity()); part(digest,line.unitAmount());
        part(digest,line.itemCode()); part(digest,line.accountCode()); part(digest,line.taxType()); part(digest,line.discountRate());
        part(digest,line.tracking().size());
        for (var tracking : line.tracking()) {
          part(digest,tracking.categoryId()); part(digest,tracking.optionId()); part(digest,tracking.name()); part(digest,tracking.option());
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
  }
  private static void part(MessageDigest digest,Object value) {
    if (value==null) { digest.update(ByteBuffer.allocate(4).putInt(-1).array()); return; }
    var bytes=value.toString().getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
  }
  public static DraftInvoice fromQuote(XeroQuoteClient.Quote quote, String jobId, LocalDate date) {
    if (quote==null || quote.contactId()==null || blank(quote.contactName()) || blank(quote.number())
        || quote.total()==null || blank(quote.currency()) || blank(quote.lineAmountTypes())
        || quote.lines()==null || quote.lines().isEmpty() || date==null) throw incomplete();
    var lines=new ArrayList<Line>();
    for (var line : quote.lines()) {
      if (line==null || line.quantity()==null || line.unitAmount()==null) throw incomplete();
      lines.add(new Line(line.description(),line.quantity(),line.unitAmount(),line.itemCode(),line.accountCode(),line.taxType(),
          line.discountRate(),line.tracking()));
    }
    String reference=blank(jobId)?quote.number():jobId.strip()+" / "+quote.number();
    if (reference.codePointCount(0,reference.length())>255) reference=reference.substring(0,reference.offsetByCodePoints(0,255));
    return new DraftInvoice(quote.contactId(),quote.contactName(),quote.number(),reference,date,quote.currency(),
        quote.lineAmountTypes(),quote.brandingThemeId(),quote.total(),lines);
  }
  private static boolean blank(String text) { return text==null || text.isBlank(); }
  private static ApiFailure incomplete() { return new ApiFailure(HttpStatus.CONFLICT,"QUOTE_INVOICE_DATA_INCOMPLETE","The quote is missing information needed to prepare an invoice."); }
}
