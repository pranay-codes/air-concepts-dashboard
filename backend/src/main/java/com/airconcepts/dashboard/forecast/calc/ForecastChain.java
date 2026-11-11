package com.airconcepts.dashboard.forecast.calc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/**
 * Opening balance for each month. An explicit override (a manually entered opening) always wins for its month (D12).
 * Otherwise a generated month's closing (its frozen opening plus receipts minus payments) becomes the next month's opening.
 * A month with no known closing (not generated and not the preview month) leaves the following month without a carried opening unless it has an override.
 */
public final class ForecastChain {
  private static final BigDecimal ZERO=new BigDecimal("0.00");
  private ForecastChain() {}

  /**
   * @param closing months whose closing is known: generated months, and the provisional preview month (D13)
   * @param netByMonth receipts minus payments for each month in closing
   * @param anchors explicit opening balances by month; each one overrides the carried value for its month
   */
  public static Map<YearMonth,BigDecimal> openings(YearMonth from,YearMonth to,Set<YearMonth> closing,Map<YearMonth,BigDecimal> netByMonth,
      Map<YearMonth,BigDecimal> anchors) {
    YearMonth first=from;
    while(!anchors.containsKey(first)&&closing.contains(first.minusMonths(1))) first=first.minusMonths(1);
    Map<YearMonth,BigDecimal> openings=new LinkedHashMap<>();
    BigDecimal current=null;
    for(YearMonth month=first;!month.isAfter(to);month=month.plusMonths(1)) {
      if(anchors.containsKey(month)) current=anchors.get(month);
      openings.put(month,current);
      if(closing.contains(month)) current=current==null?null:current.add(netByMonth.getOrDefault(month,ZERO));
      else current=null;
    }
    return openings;
  }
}
