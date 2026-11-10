package com.airconcepts.dashboard.forecast;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
/** Explicit presence preserves omitted values; only the approved fields can be patched. */
final class ForecastPatch {
  private final Map<String,Object> values;
  ForecastPatch(Map<String,Object> values,Set<String> allowed){
    if(values==null||!allowed.containsAll(values.keySet()))throw ForecastValidation.invalid("item","Only editable forecast fields may be changed.");this.values=values;
  }
  String text(String key,String old){if(!values.containsKey(key))return old;var value=values.get(key);if(value==null)return null;if(value instanceof String text)return text;throw ForecastValidation.invalid(key,"Enter text.");}
  BigDecimal money(String key,BigDecimal old){
    if(!values.containsKey(key))return old;
    Object value=values.get(key);
    if(value==null)return null;
    // JSON numbers and decimal strings are both accepted, matching the create request.
    try{return value instanceof Number number?new BigDecimal(number.toString()):new BigDecimal(text(key,null));}
    catch(NumberFormatException e){throw ForecastValidation.invalid(key,"Enter a decimal cash amount.");}
  }
  YearMonth month(String key,YearMonth old){if(!values.containsKey(key))return old;String value=text(key,null);return value==null?null:ForecastMonths.parse(value);}
  Integer day(String key,Integer old){if(!values.containsKey(key))return old;Object value=values.get(key);if(value==null)return null;if(value instanceof Integer day)return day;throw ForecastValidation.invalid(key,"Enter a whole day from 1 to 31.");}
  boolean active(boolean old){if(!values.containsKey("active"))return old;Object value=values.get("active");if(value instanceof Boolean active)return active;throw ForecastValidation.invalid("active","Choose active or inactive.");}
}
