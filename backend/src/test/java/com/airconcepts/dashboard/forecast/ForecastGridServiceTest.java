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
  @Test void frUc04_manualEditsAndDeactivationNeverChangePreviousMonths() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var clock=Clock.fixed(Instant.parse("2026-12-10T00:00:00Z"),ZoneOffset.UTC);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,clock),assumptions,mock(ForecastNoteRepository.class));
    var entity=UUID.randomUUID();var item=UUID.randomUUID();var nov=YearMonth.of(2026,11);var dec=nov.plusMonths(1);
    when(store.months(entity,nov,dec)).thenReturn(Map.of(nov,Instant.EPOCH,dec,Instant.EPOCH));
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(nov,new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("100.00"),"Bank")));
    when(store.manualLines(entity,nov,dec)).thenReturn(List.of(new ForecastGridRepository.ManualLine(nov,item,"IN","Original",new BigDecimal("25.00"))));
    when(assumptions.manualItems(entity,false)).thenReturn(List.of(new ForecastDataset.ManualItem(item,"IN","OTHER_RECEIPT","Revised",new BigDecimal("50.00"),"MONTHLY",1,nov,null,true)),List.of());
    var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
    var edited=service.grid(user,entity,nov.toString(),2);var deactivated=service.grid(user,entity,nov.toString(),2);
    assertThat(edited.months().getFirst().closing()).isEqualTo("125.00");
    assertThat(deactivated.months().getFirst().closing()).isEqualTo("125.00");
    assertThat(edited.months().get(1).closing()).isEqualTo("175.00");
    assertThat(edited.rows()).filteredOn(row->row.contact().equals("Original")).singleElement().satisfies(row->assertThat(row.amounts()).containsOnlyKeys(nov.toString()));
    assertThat(edited.rows()).filteredOn(row->row.contact().equals("Revised")).singleElement().satisfies(row->assertThat(row.amounts()).containsOnlyKeys(dec.toString()));
    assertThat(deactivated.months().get(1).closing()).isEqualTo("125.00");
    assertThat(deactivated.rows()).singleElement().satisfies(row->assertThat(row.amounts()).containsEntry(nov.toString(),"25.00"));
  }
  @Test void frUc04_03_manualItemsAreIncludedOnceInBalancesAndKeepPosition() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),assumptions,mock(ForecastNoteRepository.class));
    var entity=UUID.randomUUID();var item=UUID.randomUUID();var month=YearMonth.of(2026,11);var next=month.plusMonths(1);
    when(store.months(entity,month,next)).thenReturn(Map.of(month,Instant.EPOCH,next,Instant.EPOCH));
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(month,new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("100.00"),"Bank")));
    when(assumptions.manualItems(entity,false)).thenReturn(List.of(new ForecastDataset.ManualItem(item,"IN","OTHER_RECEIPT","Manual",new BigDecimal("25.00"),"ONE_OFF",1,month,null,true)));
    when(assumptions.manualPositions(entity)).thenReturn(Map.of(item,"City Limousines"));
    var grid=service.grid(new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR),entity,month.toString(),2);
    assertThat(grid.months().getFirst().moneyIn()).isEqualTo("25.00");
    assertThat(grid.months().getFirst().closing()).isEqualTo("125.00");
    assertThat(grid.months().get(1).opening()).isEqualTo("125.00");
    assertThat(grid.rows()).singleElement().satisfies(row->{assertThat(row.source()).isEqualTo("MANUAL");assertThat(row.positionBefore()).isEqualTo("City Limousines");assertThat(row.amounts()).containsOnlyKeys(month.toString());});
  }
  @Test void frUc04_20_gridNotesBelongToIndividualDocuments() {
    var access=mock(ForecastAccess.class); var store=mock(ForecastGridRepository.class);
    var notes=mock(ForecastNoteRepository.class); var entity=UUID.randomUUID(); var first=UUID.randomUUID(); var second=UUID.randomUUID();
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.systemUTC()),mock(ForecastAssumptionRepository.class),notes);
    var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR); var month=YearMonth.of(2026,11);
    when(store.lines(entity,month,month)).thenReturn(List.of(
      new ForecastGridRepository.Line(month,first,null,"IN","Customer",new BigDecimal("12.00"),"INV-1"),
      new ForecastGridRepository.Line(month,second,null,"IN","Customer",new BigDecimal("9.00"),"INV-2")));
    when(notes.notes(entity)).thenReturn(List.of(new ForecastNoteRepository.TransactionNote("DOCUMENT",first,"Call Monday",Instant.EPOCH)));
    var rows=service.grid(user,entity,"2026-11",1).rows();
    assertThat(rows).filteredOn(r->r.documentId().equals(first)).singleElement().satisfies(r->assertThat(r.note()).isEqualTo("Call Monday"));
    assertThat(rows).filteredOn(r->r.documentId().equals(second)).singleElement().satisfies(r->assertThat(r.note()).isNull());
    verify(notes).notes(entity); verify(access).requireRead(user,entity);
  }
  @Test void frUc04_03_staffingIsIncludedOnceAndCarriesIntoNextOpening() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var service=new ForecastGridService(access,loader,store,legacy,new ForecastCalendar(4,Clock.systemUTC()),assumptions,mock(ForecastNoteRepository.class));
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
    var calendar=new ForecastCalendar(4,Clock.systemUTC());var service=new ForecastGridService(access,loader,store,legacy,calendar,assumptions,mock(ForecastNoteRepository.class));
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
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
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
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
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
    var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));var id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);var nov=YearMonth.of(2026,11);
    when(store.months(id,nov,nov.plusMonths(7))).thenReturn(Map.of(nov,Instant.EPOCH));
    when(store.lines(id,nov,nov.plusMonths(7))).thenReturn(List.of(
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("12.30")),
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("7.70")),
      new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"OUT","Customer",new BigDecimal("5.00"))));
    var result=service.grid(user,id,"2026-11",8);
    assertThat(result.months().getFirst().available()).isTrue();assertThat(result.months().get(1).available()).isFalse();
    assertThat(result.rows()).hasSize(3);
    assertThat(result.rows().stream().filter(row->row.direction().equals("IN"))).allSatisfy(row->assertThat(row.companyAmounts()).containsEntry(nov.toString(),"20.00"));
    assertThat(result.rows().stream().filter(row->row.direction().equals("OUT"))).allSatisfy(row->assertThat(row.companyAmounts()).containsEntry(nov.toString(),"5.00"));
    assertThat(result.months().getFirst().moneyIn()).isEqualTo("20.00");
    verify(access).requireRead(user,id);
  }
  @Test void frUc04_20_gridShowsEachCompanyAndInvoiceNumber() {
    var access=mock(ForecastAccess.class); var store=mock(ForecastGridRepository.class); var legacy=mock(ForecastMonthRepository.class);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,legacy,new ForecastCalendar(4,Clock.systemUTC()),mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
    var id=UUID.randomUUID(); var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR); var month=YearMonth.of(2026,11);
    when(store.months(id,month,month)).thenReturn(Map.of(month,Instant.EPOCH));
    when(store.lines(id,month,month)).thenReturn(List.of(
      new ForecastGridRepository.Line(month,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("12.30"),"INV-1"),
      new ForecastGridRepository.Line(month,UUID.randomUUID(),null,"IN","Customer",new BigDecimal("7.70"),"INV-2"),
      new ForecastGridRepository.Line(month,UUID.randomUUID(),null,"OUT","Supplier",new BigDecimal("5.00"),null)));
    var grid=service.grid(user,id,"2026-11",1);
    assertThat(grid.rows()).extracting(ForecastGridService.Row::contact).containsExactlyInAnyOrder("Customer - INV-1","Customer - INV-2","Supplier - Number unavailable");
    assertThat(grid.months().getFirst().moneyIn()).isEqualTo("20.00");
    assertThat(grid.months().getFirst().moneyOut()).isEqualTo("5.00");
  }
  @Test void frUc04_gridRefreshPreservesCurrentAndAppendsSixFutureRevisions() {
    var access=mock(ForecastAccess.class); var loader=mock(ForecastDatasetLoader.class);
    var store=mock(ForecastGridRepository.class); var legacy=mock(ForecastMonthRepository.class);
    var calendar=new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC));
    var service=new ForecastGridService(access,loader,store,legacy,calendar,mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
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
