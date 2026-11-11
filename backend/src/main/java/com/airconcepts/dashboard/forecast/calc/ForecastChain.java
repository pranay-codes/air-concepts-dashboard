package com.airconcepts.dashboard.forecast.calc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/**
 * Opening balance for each month. A generated month's closing (its frozen opening plus receipts minus payments)
 * becomes the next month's opening; an ungenerated month passes its manually entered anchor forward (D4).
 */
public final class ForecastChain {
  private static final BigDecimal ZERO=new BigDecimal("0.00");
  private ForecastChain() {}

  /**
   * @param generated months that have a frozen forecast
   * @param netByMonth receipts minus payments for each generated month
   * @param anchors manually entered opening balances, used where the previous month is not generated
   */
  public static Map<YearMonth,BigDecimal> openings(YearMonth from,YearMonth to,Set<YearMonth> generated,Map<YearMonth,BigDecimal> netByMonth,
      Map<YearMonth,BigDecimal> anchors) {
    YearMonth first=from;
    while(generated.contains(first.minusMonths(1))) first=first.minusMonths(1);
    Map<YearMonth,BigDecimal> openings=new LinkedHashMap<>();
    BigDecimal current=anchors.get(first);
    for(YearMonth month=first;!month.isAfter(to);month=month.plusMonths(1)) {
      openings.put(month,current);
      if(generated.contains(month)) current=current==null?null:current.add(netByMonth.getOrDefault(month,ZERO));
      else current=anchors.get(month.plusMonths(1));
    }
    return openings;
  }
}
