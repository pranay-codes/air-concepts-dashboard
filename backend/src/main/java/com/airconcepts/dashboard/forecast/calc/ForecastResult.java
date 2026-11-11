package com.airconcepts.dashboard.forecast.calc;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record ForecastResult(List<MonthRow> months,Totals totals,List<ForecastException> exceptions,BeyondWindow beyondWindow,
    List<Contribution> contributions,List<Exclusion> exclusions) {
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"month","state","generatable","pendingNewItems","receiptCount","paymentCount","containsOverdue"})
  public record MonthRow(YearMonth month,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal opening,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal payments,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal net,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal closing,
      int receiptCount,int paymentCount,boolean containsOverdue,
      @io.swagger.v3.oas.annotations.media.Schema(allowableValues={"GENERATED","PREVIEW","NOT_GENERATED"}) String state,
      boolean generatable,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant generatedAt,int pendingNewItems,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},allowableValues={"MANUAL","OVERRIDE","CARRIED_FORWARD"}) String openingSource) {
    /** Calculator rows have no generation state; the service assigns it when it resolves a financial year. */
    public MonthRow(YearMonth month,BigDecimal opening,BigDecimal receipts,BigDecimal payments,BigDecimal net,BigDecimal closing,
        int receiptCount,int paymentCount,boolean containsOverdue) {
      this(month,opening,receipts,payments,net,closing,receiptCount,paymentCount,containsOverdue,"PREVIEW",false,null,0,null);
    }
  }
  public record Totals(@JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal payments,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal closing) {}
  public record BeyondWindow(@JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal payments) {}
  public record ForecastException(String kind,UUID documentId,String message) {}
  public record Contribution(UUID sourceId,String source,String direction,String category,String number,String label,
      LocalDate date,YearMonth bucket,@JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal amount,
      DateRule rule,boolean overdue,Integer splitSequence,Integer splitCount,LocalDate issueDate,LocalDate dueDate) {
    /** Calculator and tests that predate the Xero dates on each line. */
    public Contribution(UUID sourceId,String source,String direction,String category,String number,String label,LocalDate date,YearMonth bucket,
        BigDecimal amount,DateRule rule,boolean overdue,Integer splitSequence,Integer splitCount) {
      this(sourceId,source,direction,category,number,label,date,bucket,amount,rule,overdue,splitSequence,splitCount,null,null);
    }
  }
  public record Exclusion(UUID documentId,String number,String reason) {}
}
