package com.airconcepts.dashboard.forecast.calc;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record ForecastResult(List<MonthRow> months,Totals totals,List<ForecastException> exceptions,BeyondWindow beyondWindow,
    List<Contribution> contributions,List<Exclusion> exclusions) {
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"month","opening","receipts","payments","net","closing","receiptCount","paymentCount","containsOverdue"})
  public record MonthRow(YearMonth month,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal opening,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal payments,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal net,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal closing,
      int receiptCount,int paymentCount,boolean containsOverdue) {}
  public record Totals(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal payments,
      @JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal closing) {}
  public record BeyondWindow(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal receipts,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal payments) {}
  public record ForecastException(String kind,UUID documentId,String message) {}
  public record Contribution(UUID sourceId,String source,String direction,String category,String number,String label,
      LocalDate date,YearMonth bucket,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount,
      DateRule rule,boolean overdue,Integer splitSequence,Integer splitCount) {}
  public record Exclusion(UUID documentId,String number,String reason) {}
}
