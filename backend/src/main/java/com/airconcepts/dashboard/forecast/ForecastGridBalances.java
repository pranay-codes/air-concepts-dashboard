package com.airconcepts.dashboard.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** Computes balances from the same saved invoice and bill lines displayed by the grid. */
public final class ForecastGridBalances {
  public record Balance(BigDecimal moneyIn,BigDecimal moneyOut,BigDecimal opening,BigDecimal closing) {}
  private ForecastGridBalances() {}
  public static Map<YearMonth,Balance> calculate(YearMonth start,YearMonth end,Set<YearMonth> available,
      List<ForecastGridRepository.Line> lines,Map<YearMonth,BigDecimal> anchors) {
    Map<YearMonth,BigDecimal> in=new HashMap<>(),out=new HashMap<>();
    for(var line:lines) ("IN".equals(line.direction())?in:out).merge(line.month(),line.amount(),BigDecimal::add);
    Map<YearMonth,Balance> result=new LinkedHashMap<>();BigDecimal carry=null;
    for(var month=start;!month.isAfter(end);month=month.plusMonths(1)) {
      var opening=anchors.getOrDefault(month,carry);
      if(!available.contains(month)) {result.put(month,new Balance(null,null,opening,null));carry=null;continue;}
      var receipts=in.getOrDefault(month,new BigDecimal("0.00"));var payments=out.getOrDefault(month,new BigDecimal("0.00"));
      var closing=opening==null?null:opening.add(receipts).subtract(payments);
      result.put(month,new Balance(receipts,payments,opening,closing));carry=closing;
    }
    return result;
  }
}
