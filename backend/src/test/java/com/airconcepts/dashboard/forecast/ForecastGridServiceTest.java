package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForecastGridServiceTest {
  @Test void frUc04_03_staffingIsIncludedOnceAndCarriesIntoNextOpening() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var service=new ForecastGridService(access,loader,store,legacy,new ForecastCalendar(4,Clock.systemUTC()),assumptions);
    var entity=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);var jan=YearMonth.of(2026,1);var feb=jan.plusMonths(1);
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(jan,new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("10000.00"),"Bank")));
    when(store.months(entity,jan,feb)).thenReturn(Map.of(jan,Instant.EPOCH,feb,Instant.EPOCH));
    when(assumptions.staffing(entity,false)).thenReturn(List.of(new ForecastDataset.StaffingLine(UUID.randomUUID(),"Private",BigDecimal.ZERO,null,jan,null,true,new BigDecimal("25.00"),new BigDecimal("40.00"),"WEEKLY",LocalDate.of(2026,1,2),null,null)));
    var result=service.grid(user,entity,"2026-01",2);
    assertThat(result.months().getFirst().staffingExpenses()).isEqualTo("5000.00");
    assertThat(result.months().getFirst().moneyOut()).isEqualTo("5000.00");
    assertThat(result.months().getFirst().closing()).isEqualTo("5000.00");
    assertThat(result.months().get(1).opening()).isEqualTo("5000.00");
    assertThat(result.rows()).isEmpty();
    verify(access).requireRead(user,entity);verify(assumptions).staffing(entity,false);
  }
  @Test void frUc04_05_gridCarriesBalanceFromBeforeTheVisibleWindow() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var calendar=new ForecastCalendar(4,Clock.systemUTC());var service=new ForecastGridService(access,loader,store,legacy,calendar,assumptions);
    var id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);var nov=YearMonth.of(2026,11);var dec=nov.plusMonths(1);var end=dec.plusMonths(7);
    when(assumptions.openingAnchors(id)).thenReturn(Map.of(nov,new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("100.00"),"Statement")));
    when(store.months(id,nov,end)).thenReturn(Map.of(nov,Instant.EPOCH,dec,Instant.EPOCH));
    when(store.lines(id,nov,end)).thenReturn(List.of(new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","A",new BigDecimal("50.00"))));
    var result=service.grid(user,id,dec.toString(),8);
    assertThat(result.months().getFirst().opening()).isEqualTo("150.00");
    assertThat(result.months().getFirst().closing()).isEqualTo("150.00");
    assertThat(result.months().getFirst().openingSource()).isEqualTo("CARRIED_FORWARD");
  }
  @Test void frUc04_changedProtectedUnsplitDocumentIsNotCountedAgainAsSplits() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class));
    var id=UUID.randomUUID();var doc=UUID.randomUUID();var entity=new Entity(id,"Synthetic");var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);var now=YearMonth.of(2026,11);
    when(access.requireWrite(user,id)).thenReturn(entity);when(store.months(id,now,now.plusMonths(6))).thenReturn(Map.of(now,Instant.EPOCH));
    when(store.lines(id,YearMonth.of(1,1),now)).thenReturn(List.of(new ForecastGridRepository.Line(now,doc,null,"IN","A",new BigDecimal("100.00"))));
    var document=new ForecastDataset.Document(doc,"ACCREC","1","A",LocalDate.of(2026,11,1),LocalDate.of(2026,12,1),null,null,new BigDecimal("100.00"),false,null,List.of(new ForecastDataset.Split(1,LocalDate.of(2026,12,1),new BigDecimal("100.00"))),false);
    when(loader.load(entity,now)).thenReturn(new ForecastDataset(id,"Synthetic",now,LocalDate.of(2026,11,10),null,null,List.of(document),List.of(),List.of(),new ForecastDataset.SyncInfo(Instant.EPOCH,"SUCCEEDED",false)));
    var result=service.generate(user,id);assertThat(result.changedProtectedSchedules()).isEqualTo(1);
    verify(store).append(eq(id),eq(now.plusMonths(1)),any(),argThat(List::isEmpty));
  }
  @Test void frUc04_firstGenerationCreatesCurrentMonthAndSixFutureMonths() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class));
    var id=UUID.randomUUID();var entity=new Entity(id,"Synthetic");var user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);var now=YearMonth.of(2026,11);
    when(access.requireWrite(user,id)).thenReturn(entity);
    when(loader.load(entity,now)).thenReturn(new ForecastDataset(id,"Synthetic",now,LocalDate.of(2026,11,10),null,null,List.of(),List.of(),List.of(),new ForecastDataset.SyncInfo(Instant.EPOCH,"SUCCEEDED",false)));
    var result=service.generate(user,id);
    assertThat(result.created()).hasSize(7).contains("2026-11","2027-05");
    verify(store).append(eq(id),eq(now),any(),anyList());
  }
  @Test void frUc04_gridGroupsInvoicesAndBillsAndMarksMissingMonthsUnavailable() {
    var access=mock(ForecastAccess.class); var loader=mock(ForecastDatasetLoader.class); var store=mock(ForecastGridRepository.class); var legacy=mock(ForecastMonthRepository.class);
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));
    var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class));var id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);var nov=YearMonth.of(2026,11);
    when(store.months(id,nov,nov.plusMonths(7))).thenReturn(Map.of(nov,Instant.EPOCH));
    when(store.lines(id,nov,nov.plusMonths(7))).thenReturn(List.of(
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("12.30")),
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("7.70")),
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"OUT","Customer",new BigDecimal("5.00"))));
    var result=service.grid(user,id,"2026-11",8);
    assertThat(result.months().getFirst().available()).isTrue();assertThat(result.months().get(1).available()).isFalse();
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().stream().filter(r->r.direction().equals("IN")).findFirst().orElseThrow().amounts().get("2026-11")).isEqualTo("20.00");
    verify(access).requireRead(user,id);
  }
  @Test void frUc04_gridRefreshPreservesCurrentAndAppendsSixFutureRevisions() {
    var access=mock(ForecastAccess.class); var loader=mock(ForecastDatasetLoader.class);
    var store=mock(ForecastGridRepository.class); var legacy=mock(ForecastMonthRepository.class);
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));
    var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class));
    var id=UUID.randomUUID(); var entity=new Entity(id,"Synthetic");
    var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR); var current=YearMonth.of(2026,11);
    when(access.requireWrite(user,id)).thenReturn(entity);
    var existing=new HashMap<YearMonth,Instant>(); for(int i=0;i<=6;i++) existing.put(current.plusMonths(i),Instant.EPOCH);
    when(store.months(id,current,current.plusMonths(6))).thenReturn(existing);
    when(legacy.generated(id)).thenReturn(Map.of());
    when(loader.load(entity,current)).thenReturn(new ForecastDataset(id,"Synthetic",current,LocalDate.of(2026,11,10),null,null,List.of(),List.of(),List.of(),new ForecastDataset.SyncInfo(Instant.EPOCH,"SUCCEEDED",false)));
    var result=service.generate(user,id);
    assertThat(result.preserved()).containsExactly("2026-11");
    assertThat(result.refreshed()).hasSize(6);
    verify(store,never()).append(eq(id),eq(current),any(),anyList());
    verify(store,times(6)).append(eq(id),any(),any(),anyList());
  }
}
