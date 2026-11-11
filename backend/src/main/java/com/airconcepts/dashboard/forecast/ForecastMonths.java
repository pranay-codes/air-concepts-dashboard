package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import org.springframework.http.HttpStatus;
public final class ForecastMonths {
  private ForecastMonths() {}
  public static final ZoneId ZONE=BusinessTime.ZONE;
  public static YearMonth parse(String value){
    try {if(value==null||!value.matches("\\d{4}-\\d{2}"))throw new IllegalArgumentException();return YearMonth.parse(value);}
    catch(RuntimeException e){throw new ApiFailure(HttpStatus.BAD_REQUEST,"FORECAST_PERIOD_INVALID","Choose a month in YYYY-MM format.");}
  }
}
