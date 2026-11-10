package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import org.springframework.http.HttpStatus;
public final class ForecastMonths {
  private ForecastMonths() {}
  public static final ZoneId ZONE=ZoneId.of("Pacific/Auckland");
  public static YearMonth parse(String value){
    try {if(value==null||!value.matches("\\d{4}-\\d{2}"))throw new IllegalArgumentException();return YearMonth.parse(value);}
    catch(RuntimeException e){throw new ApiFailure(HttpStatus.BAD_REQUEST,"FORECAST_PERIOD_INVALID","Choose a month in YYYY-MM format.");}
  }
  public static YearMonth window(String value,Clock clock){
    var current=YearMonth.from(LocalDate.now(clock.withZone(ZONE)));var month=value==null?current:parse(value);
    if(month.isAfter(current)||month.isBefore(current.minusMonths(12)))throw new ApiFailure(HttpStatus.BAD_REQUEST,"FORECAST_PERIOD_INVALID","Choose the current month or one of the previous 12 months.");
    return month;
  }
}
