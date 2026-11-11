package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Financial-year arithmetic and the generation rules for monthly forecasts (FR-UC04-22 to 24).
 * The financial year starts on {@code dashboard.forecast.financial-year-start-month} (default April, NZ; see the spec addendum D1).
 */
@Component
public class ForecastCalendar {
  /** A month can be generated from the current month up to this many months ahead. */
  public static final int GENERATION_WINDOW_MONTHS=11;
  private static final int FY_YEARS_BACK=10,FY_YEARS_AHEAD=2;
  private final int startMonth;private final Clock clock;

  public ForecastCalendar(@Value("${dashboard.forecast.financial-year-start-month:4}") int startMonth,Clock clock) {
    if(startMonth<1||startMonth>12) throw new IllegalArgumentException("dashboard.forecast.financial-year-start-month must be 1 to 12");
    this.startMonth=startMonth;this.clock=clock;
  }

  public int startMonth(){return startMonth;}

  public YearMonth currentMonth() {return YearMonth.from(LocalDate.now(clock.withZone(ForecastMonths.ZONE)));}

  /** The financial year that contains today. */
  public YearMonth currentFinancialYearStart() {return financialYearContaining(currentMonth());}

  public YearMonth financialYearContaining(YearMonth month) {
    return month.getMonthValue()>=startMonth?YearMonth.of(month.getYear(),startMonth):YearMonth.of(month.getYear()-1,startMonth);
  }

  /** Parses a {@code financialYearStart} query value (a four-digit year) or returns the current financial year when absent. */
  public YearMonth financialYearStart(String value) {
    if(value==null||value.isBlank()) return currentFinancialYearStart();
    if(!value.matches("\\d{4}")) throw invalidYear();
    var start=YearMonth.of(Integer.parseInt(value),startMonth);
    var current=currentFinancialYearStart();
    if(start.getYear()<current.getYear()-FY_YEARS_BACK||start.getYear()>current.getYear()+FY_YEARS_AHEAD) throw invalidYear();
    return start;
  }

  public String label(YearMonth financialYearStart) {return financialYearStart.getYear()+"-"+financialYearStart.plusMonths(11).getYear();}

  public boolean inGenerationWindow(YearMonth month,YearMonth current) {
    return !month.isBefore(current)&&!month.isAfter(current.plusMonths(GENERATION_WINDOW_MONTHS));
  }

  /**
   * A month may be generated, or regenerated, when it is in the window, the following month is not generated (D3),
   * and every month from the current month up to it is generated (D2).
   */
  public boolean canGenerate(YearMonth month,Set<YearMonth> generated,YearMonth current) {
    if(!inGenerationWindow(month,current)||generated.contains(month.plusMonths(1))) return false;
    for(YearMonth earlier=current;earlier.isBefore(month);earlier=earlier.plusMonths(1)) if(!generated.contains(earlier)) return false;
    return true;
  }

  public static ApiFailure invalidYear() {
    return new ApiFailure(HttpStatus.BAD_REQUEST,"FORECAST_PERIOD_INVALID","Choose a financial year within the supported range.");
  }
}
