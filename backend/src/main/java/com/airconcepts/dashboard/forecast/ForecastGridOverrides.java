package com.airconcepts.dashboard.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** Applies monthly forecast-only overrides without changing source documents. */
public final class ForecastGridOverrides {
  public record Override(YearMonth month,String direction,String scope,String key,BigDecimal amount,String company,String number) {
    public Override(YearMonth month,String direction,String scope,String key,BigDecimal amount){this(month,direction,scope,key,amount,null,null);}
  }
  public record Result(List<ForecastGridRepository.Line> rows,List<ForecastGridRepository.Line> balances) {}
  private ForecastGridOverrides() {}

  public static Result apply(List<ForecastGridRepository.Line> lines,List<Override> overrides) {
    var rows=new ArrayList<>(lines);
    for(var change:overrides) {
      if(!"DOCUMENT".equals(change.scope())) continue;
      var matching=rows.stream().filter(l->l.month().equals(change.month())&&l.direction().equals(change.direction())&&l.documentId()!=null&&l.documentId().toString().equals(change.key())).toList();
      var template=matching.isEmpty()?rows.stream().filter(l->l.direction().equals(change.direction())&&l.documentId()!=null&&l.documentId().toString().equals(change.key())).findFirst().orElse(null):matching.get(0);
      if(template==null&&change.company()!=null) template=new ForecastGridRepository.Line(change.month(),UUID.fromString(change.key()),null,change.direction(),change.company(),change.amount(),change.number());
      if(template==null) continue;
      int index=matching.isEmpty()?rows.size():rows.indexOf(matching.get(0));
      rows.removeAll(matching);
      rows.add(index,new ForecastGridRepository.Line(change.month(),template.documentId(),null,template.direction(),change.company()==null?template.contact():change.company(),change.amount(),change.company()==null?template.number():change.number()));
    }
    var balances=new ArrayList<>(rows);
    for(var change:overrides) if("COMPANY".equals(change.scope())) {
      balances.removeIf(l->l.month().equals(change.month())&&l.direction().equals(change.direction())&&l.contact().equals(change.key()));
      balances.add(new ForecastGridRepository.Line(change.month(),null,null,change.direction(),change.key(),change.amount()));
    }
    return new Result(List.copyOf(rows),List.copyOf(balances));
  }
}
