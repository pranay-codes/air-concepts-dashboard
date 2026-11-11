package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.ForecastAssumptionRepository.OpeningBalance;
import com.airconcepts.dashboard.forecast.ForecastMonthRepository.GeneratedMonth;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastServiceTest {
  private final ForecastDatasetLoader loader=mock(ForecastDatasetLoader.class);
  private final ForecastAccess access=mock(ForecastAccess.class);
  private final ForecastMonthRepository months=mock(ForecastMonthRepository.class);
  private final ForecastAssumptionRepository assumptions=mock(ForecastAssumptionRepository.class);
  private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
  private final ForecastCalendar calendar=new ForecastCalendar(4,clock);
  private final ForecastResolver resolver=new ForecastResolver(loader,months,assumptions,calendar);
  private final ForecastService service=new ForecastService(access,resolver,calendar);
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  private static final YearMonth APRIL=YearMonth.of(2026,4),NOVEMBER=YearMonth.of(2026,11);

  @Test void frUc04_13_combinedSumsOnlyRequestedEntitiesAndNeverReturnsDocuments(){
    UUID a=UUID.randomUUID(),b=UUID.randomUUID();setup(a,BigDecimal.TEN);setup(b,BigDecimal.ONE);
    var result=service.combined(user,List.of(a,b),"2026");
    assertThat(result.months().getFirst().opening()).isEqualByComparingTo("11");
    assertThat(result.financialYearStart()).isEqualTo(APRIL);
    assertThat(result.entities()).hasSize(2);assertThat(result.complete()).isTrue();
    assertThat(result.getClass().getRecordComponents()).noneSatisfy(c->assertThat(c.getName()).isIn("documents","staffing","contributions"));
  }
  @Test void frUc04_13_archivedEntityFailsWholeRequest(){
    UUID a=UUID.randomUUID(),b=UUID.randomUUID();setup(a,BigDecimal.TEN);
    when(access.requireRead(user,b)).thenThrow(ForecastValidation.missing());
    assertThatThrownBy(()->service.combined(user,List.of(a,b),"2026")).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
  }
  @Test void frUc04_13_missingOpeningIsIncompleteAndNeverZero(){
    UUID a=UUID.randomUUID();setup(a,null);var result=service.combined(user,List.of(a),"2026");
    assertThat(result.complete()).isFalse();assertThat(result.months().getFirst().opening()).isNull();
  }
  @Test void frUc04_23_generatedMonthShowsFrozenLinesNotLiveDocuments(){
    UUID entity=UUID.randomUUID();setup(entity,new BigDecimal("1000.00"));
    // November is the first generated month, so its opening is the manual anchor entered for November.
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(NOVEMBER,new OpeningBalance(new BigDecimal("1000.00"),"Statement")));
    when(months.generated(entity)).thenReturn(Map.of(NOVEMBER,new GeneratedMonth(NOVEMBER,clock.instant(),1)));
    when(months.lines(entity,APRIL,APRIL.plusMonths(11))).thenReturn(Map.of(NOVEMBER,List.of(
        new Contribution(UUID.randomUUID(),"DOCUMENT","IN","ACCREC","INV-1","Frozen customer",LocalDate.of(2026,11,20),NOVEMBER,new BigDecimal("300.00"),
            DateRule.DUE_DATE,false,null,null,LocalDate.of(2026,11,1),LocalDate.of(2026,11,20)))));
    // The live mirror now shows another invoice; the frozen November line must still be the one that counts.
    var live=new ForecastDataset.Document(UUID.randomUUID(),"ACCREC","INV-2","Live customer",LocalDate.of(2026,11,1),LocalDate.of(2026,11,25),null,null,
        new BigDecimal("999.00"),false,null,List.of(),false);
    setDataset(entity,List.of(live));
    var november=service.view(user,entity,"2026").months().get(7);
    assertThat(november.state()).isEqualTo("GENERATED");
    assertThat(november.receipts()).isEqualByComparingTo("300.00");
    assertThat(november.opening()).isEqualByComparingTo("1000.00");
    assertThat(november.closing()).isEqualByComparingTo("1300.00");
    assertThat(november.pendingNewItems()).isEqualTo(1);
  }
  @Test void frUc04_22_notGeneratedPastMonthsHaveNoFiguresAndNeverZero(){
    UUID entity=UUID.randomUUID();setup(entity,new BigDecimal("1000.00"));
    var april=service.view(user,entity,"2026").months().getFirst();
    assertThat(april.state()).isEqualTo("NOT_GENERATED");
    assertThat(april.receipts()).isNull();assertThat(april.closing()).isNull();
    assertThat(april.opening()).isEqualByComparingTo("1000.00");
  }
  @Test void frUc04_05_previewClosingCarriesIntoTheNextOpening(){
    UUID entity=UUID.randomUUID();setup(entity,null);
    // November is the preview month: nothing is generated yet. Its closing is provisional, but December's opening follows it (D13).
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(NOVEMBER,new OpeningBalance(new BigDecimal("1000.00"),"Statement")));
    var receipt=new ForecastDataset.Document(UUID.randomUUID(),"ACCREC","INV-3","Customer",LocalDate.of(2026,11,1),LocalDate.of(2026,11,25),null,null,
        new BigDecimal("999.00"),false,null,List.of(),false);
    setDataset(entity,List.of(receipt));
    var rows=service.view(user,entity,"2026").months();
    assertThat(rows.get(7).state()).isEqualTo("PREVIEW");
    assertThat(rows.get(7).closing()).isEqualByComparingTo("1999.00");
    assertThat(rows.get(8).state()).isEqualTo("NOT_GENERATED");
    assertThat(rows.get(8).opening()).isEqualByComparingTo("1999.00");
    assertThat(rows.get(8).openingSource()).isEqualTo("CARRIED_FORWARD");
    assertThat(rows.get(8).receipts()).isNull();
    assertThat(rows.get(9).opening()).isNull();
  }
  private void setup(UUID id,BigDecimal opening){
    var entity=new Entity(id,"Synthetic");when(access.requireRead(user,id)).thenReturn(entity);
    if(opening!=null) when(assumptions.openingAnchors(id)).thenReturn(Map.of(APRIL,new OpeningBalance(opening,"Statement")));
    when(months.placement(id)).thenReturn(ForecastPlacement.Placed.none());
    setDataset(id,List.of());
  }
  private void setDataset(UUID id,List<ForecastDataset.Document> documents){
    var entity=new Entity(id,"Synthetic");
    when(loader.load(entity,APRIL)).thenReturn(new ForecastDataset(id,"Synthetic",APRIL,LocalDate.of(2026,11,10),null,null,documents,List.of(),List.of(),
        new SyncInfo(clock.instant(),"SUCCEEDED",false)));
  }
}
