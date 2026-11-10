package com.airconcepts.dashboard.forecast;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
public final class ForecastConflictPolicy {
  private ForecastConflictPolicy(){}
  public static boolean changed(boolean hasSplits,LocalDate oldDate,BigDecimal oldAmount,LocalDate newDate,BigDecimal newAmount){
    return hasSplits&&(!Objects.equals(oldDate,newDate)||oldAmount.compareTo(newAmount)!=0);
  }
}
