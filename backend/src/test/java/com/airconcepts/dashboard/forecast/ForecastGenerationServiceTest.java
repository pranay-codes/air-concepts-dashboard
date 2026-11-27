package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.forecast.ForecastAssumptionRepository.OpeningBalance;
import com.airconcepts.dashboard.forecast.ForecastMonthRepository.GeneratedMonth;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastGenerationServiceTest {
  private final ForecastAccess access=mock(ForecastAccess.class);
  private final ForecastDatasetLoader loader=mock(ForecastDatasetLoader.class);
  private final ForecastAssumptionRepository assumptions=mock(ForecastAssumptionRepository.class);
  private final ForecastMonthRepository months=mock(ForecastMonthRepository.class);
  private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
  private final ForecastCalendar calendar=new ForecastCalendar(4,clock);
  private final ForecastGenerationService service=new ForecastGenerationService(access,loader,assumptions,months,calendar);
  private final UUID entityId=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  private static final YearMonth NOV=YearMonth.of(2026,11),DEC=NOV.plusMonths(1);

  private static Document invoice(String number,LocalDate due){
    return new Document(UUID.randomUUID(),"ACCREC",number,"Customer "+number,due.minusDays(14),due,null,null,new BigDecimal("100.00"),false,null,List.of(),false);
  }
  /** Stubs the entity, the November opening anchor, the empty placement, and an insert that reports every row it was given. */
  private void dataset(List<Document> documents){
    var entity=new Entity(entityId,"Synthetic");when(access.requireWrite(user,entityId)).thenReturn(entity);
    for(var month:List.of(NOV,DEC)) when(loader.load(entity,month)).thenReturn(new ForecastDataset(entityId,"Synthetic",month,LocalDate.of(2026,11,10),null,null,
        documents,List.of(),List.of(),new ForecastDataset.SyncInfo(clock.instant(),"SUCCEEDED",false)));
    when(months.placement(entityId)).thenReturn(ForecastPlacement.Placed.none());
    when(assumptions.openingAnchors(entityId)).thenReturn(Map.of(NOV,new OpeningBalance(new BigDecimal("1000.00"),"Statement")));
    when(months.insertLines(eq(entityId),any(),anyInt(),any(),anyList())).thenAnswer(call->((List<?>)call.getArgument(4)).size());
  }

  @Test void frUc04_23_generateOnceThenNoOpLeavesFrozenLinesAlone(){
    var due=invoice("INV-1",LocalDate.of(2026,11,20));dataset(List.of(due));
    var first=service.generate(user,entityId,"2026-11");
    assertThat(first.addedLines()).isEqualTo(1);assertThat(first.generationCount()).isEqualTo(1);
    verify(months).createGeneration(eq(entityId),eq(NOV),any());
    verify(assumptions).captureManualMonth(entityId,NOV);
    verify(months).insertLines(eq(entityId),eq(NOV),eq(1),any(),anyList());
    // The second request sees the stored month and the placed document: it adds nothing and touches nothing.
    when(months.generated(entityId)).thenReturn(Map.of(NOV,new GeneratedMonth(NOV,clock.instant(),1)));
    when(months.placement(entityId)).thenReturn(new ForecastPlacement.Placed(Set.of(due.id()),Set.of()));
    var second=service.generate(user,entityId,"2026-11");
    assertThat(second.addedLines()).isZero();assertThat(second.generationCount()).isEqualTo(1);
    verify(months,times(1)).insertLines(any(),any(),anyInt(),any(),anyList());
    verify(months,never()).recordRegeneration(any(),any(),any());
    verify(assumptions,times(2)).captureManualMonth(entityId,NOV);
  }

  @Test void frUc04_24_placedDocumentIsNeverReplacedAndNewDocumentsAreAdded(){
    var placed=invoice("INV-PAID",LocalDate.of(2026,11,20));var fresh=invoice("INV-NEW",LocalDate.of(2026,11,25));dataset(List.of(placed,fresh));
    when(months.generated(entityId)).thenReturn(Map.of(NOV,new GeneratedMonth(NOV,clock.instant(),1)));
    when(months.placement(entityId)).thenReturn(new ForecastPlacement.Placed(Set.of(placed.id()),Set.of()));
    var result=service.generate(user,entityId,"2026-11");
    assertThat(result.addedLines()).isEqualTo(1);assertThat(result.generationCount()).isEqualTo(2);
    verify(months).recordRegeneration(entityId,NOV,UUID.fromString(user.subject()));
    verify(months).insertLines(eq(entityId),eq(NOV),eq(2),any(),argThat(rows->rows.size()==1&&rows.getFirst().sourceId().equals(fresh.id())));
  }

  @Test void frUc04_24_overdueItemsOnlyJoinTheMonthThatIsCurrentWhenGenerated(){
    var overdue=invoice("INV-LATE",LocalDate.of(2026,11,1));dataset(List.of(overdue));
    when(months.generated(entityId)).thenReturn(Map.of(NOV,new GeneratedMonth(NOV,clock.instant(),1)));
    // November is current, so the overdue invoice is added to November.
    assertThat(service.generate(user,entityId,"2026-11").addedLines()).isEqualTo(1);
    // December is generated early: the overdue invoice is bucketed to November, so December must not take it.
    when(months.generated(entityId)).thenReturn(Map.of(NOV,new GeneratedMonth(NOV,clock.instant(),2)));
    var next=service.generate(user,entityId,"2026-12");
    assertThat(next.addedLines()).isZero();
    verify(months,never()).insertLines(eq(entityId),eq(DEC),anyInt(),any(),anyList());
  }

  @Test void frUc04_24_lockedWhenTheFollowingMonthIsGenerated(){
    dataset(List.of());
    when(months.generated(entityId)).thenReturn(Map.of(NOV,new GeneratedMonth(NOV,clock.instant(),1),DEC,new GeneratedMonth(DEC,clock.instant(),1)));
    assertThatThrownBy(()->service.generate(user,entityId,"2026-11")).isInstanceOfSatisfying(ApiFailure.class,f->assertThat(f.code()).isEqualTo("FORECAST_MONTH_LOCKED"));
    verify(months,never()).insertLines(any(),any(),anyInt(),any(),anyList());
    verify(months,never()).createGeneration(any(),any(),any());
  }

  @Test void frUc04_23_monthsMustFollowTheCurrentMonthInOrder(){
    dataset(List.of());
    assertThatThrownBy(()->service.generate(user,entityId,"2026-12")).isInstanceOfSatisfying(ApiFailure.class,f->assertThat(f.code()).isEqualTo("FORECAST_MONTH_NOT_GENERATABLE"));
    assertThatThrownBy(()->service.generate(user,entityId,"2026-10")).isInstanceOfSatisfying(ApiFailure.class,f->assertThat(f.code()).isEqualTo("FORECAST_PERIOD_INVALID"));
    verify(months,never()).createGeneration(any(),any(),any());
  }

  @Test void frUc04_23_generationNeedsAnOpeningBalance(){
    dataset(List.of());when(assumptions.openingAnchors(entityId)).thenReturn(Map.of());
    assertThatThrownBy(()->service.generate(user,entityId,"2026-11")).isInstanceOfSatisfying(ApiFailure.class,f->assertThat(f.code()).isEqualTo("FORECAST_OPENING_REQUIRED"));
    verify(months,never()).createGeneration(any(),any(),any());
  }
}
