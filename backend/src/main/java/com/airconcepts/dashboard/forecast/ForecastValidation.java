package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
public final class ForecastValidation {
  private ForecastValidation() {}
  public static ApiFailure invalid(String field,String message) {return new ForecastInputFailure(field,message);}
  public static BigDecimal money(String field,BigDecimal amount,boolean positive) {
    if(amount==null||amount.precision()>18||(positive&&amount.signum()<=0)) throw invalid(field,"Enter a valid cash amount with at most two decimals.");
    try {var value=amount.setScale(2,RoundingMode.UNNECESSARY);if(value.precision()>18)throw invalid(field,"Use at most 16 digits before the decimal point.");return value;}catch(ArithmeticException e){throw invalid(field,"Use at most two decimal places.");}
  }
  public static String text(String field,String value,int max) {
    if(value==null||value.isBlank()||value.strip().length()>max||value.chars().anyMatch(Character::isISOControl)) throw invalid(field,"Enter 1 to "+max+" characters.");
    return value.strip();
  }
  public static void months(YearMonth start,YearMonth end) {if(start==null||(end!=null&&end.isBefore(start)))throw invalid("startMonth","Choose a start month and an end month on or after it.");}
  public static void day(String field,Integer day) {if(day!=null&&(day<1||day>31))throw invalid(field,"Choose a day from 1 to 31.");}
  public static ApiFailure missing() {return new ApiFailure(HttpStatus.NOT_FOUND,"FORECAST_RECORD_UNAVAILABLE","This forecast record is unavailable for this entity.");}
  public static final class ForecastInputFailure extends ApiFailure {
    private final Map<String,String> fieldErrors;
    public ForecastInputFailure(String field,String message) {super(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_INPUT_INVALID",message);fieldErrors=Map.of(field,message);}
    public Map<String,String> fieldErrors(){return fieldErrors;}
  }
}
