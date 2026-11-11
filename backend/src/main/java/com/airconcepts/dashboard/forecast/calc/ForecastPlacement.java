package com.airconcepts.dashboard.forecast.calc;

import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import java.time.YearMonth;
import java.util.*;

/**
 * Which planned lines already live in a generated month. A document is placed once per entity (FR-UC04-24),
 * while manual items and staffing lines are placed once per month. Pure: no database or clock access.
 */
public final class ForecastPlacement {
  public record Placed(Set<UUID> documents,Set<String> keys) {
    public Placed {documents=Set.copyOf(documents);keys=Set.copyOf(keys);}
    public static Placed none(){return new Placed(Set.of(),Set.of());}
  }
  private ForecastPlacement() {}

  /** Key for a manual or staffing line within one month. */
  public static String key(String source,UUID sourceId,YearMonth month) {return source+"|"+sourceId+"|"+month;}

  /** Builds the placed set from stored lines of one entity, whatever month they were placed in. */
  public static Placed of(Collection<Contribution> stored) {
    var documents=new HashSet<UUID>();var keys=new HashSet<String>();
    for(var line:stored) {
      if("DOCUMENT".equals(line.source())) documents.add(line.sourceId());
      else keys.add(key(line.source(),line.sourceId(),line.bucket()));
    }
    return new Placed(documents,keys);
  }

  /** Calculated lines dated in {@code month} that are not yet placed anywhere (documents) or in that month (other sources). */
  public static List<Contribution> unplaced(List<Contribution> calculated,YearMonth month,Placed placed) {
    return calculated.stream().filter(c->c.bucket().equals(month)).filter(c->isUnplaced(c,placed)).toList();
  }

  public static boolean isUnplaced(Contribution line,Placed placed) {
    return "DOCUMENT".equals(line.source())?!placed.documents().contains(line.sourceId())
        :!placed.keys().contains(key(line.source(),line.sourceId(),line.bucket()));
  }
}
