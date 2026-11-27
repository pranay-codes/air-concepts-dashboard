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
  @Test void frUc04_blankManualOverrideSurvivesMonthRollover() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var entity=UUID.randomUUID();var item=UUID.randomUUID();var month=YearMonth.of(2026,11);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-12-10T00:00:00Z"),ZoneOffset.UTC)),assumptions,mock(ForecastNoteRepository.class));
    when(store.months(entity,month,month)).thenReturn(Map.of(month,Instant.EPOCH));
    when(store.overrides(entity,month,month)).thenReturn(List.of(new ForecastGridOverrides.Override(month,"OUT","MANUAL",item.toString(),new BigDecimal("40.00"))));
    when(assumptions.manualItems(entity,true)).thenReturn(List.of(new ForecastDataset.ManualItem(item,"OUT","OTHER_PAYMENT","Reserve",BigDecimal.ONE,"ONE_OFF",1,month,null,false)));
    var result=service.grid(new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR),entity,month.toString(),1);
    assertThat(result.months().getFirst().moneyOut()).isEqualTo("40.00");
    assertThat(result.rows()).singleElement().satisfies(row->assertThat(row.amounts()).containsEntry("2026-11","40.00"));
  }
  @Test void frUc04_inactiveManualOverrideDoesNotReappearInFuture() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var entity=UUID.randomUUID();var item=UUID.randomUUID();var month=YearMonth.of(2026,11);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),assumptions,mock(ForecastNoteRepository.class));
    when(store.months(entity,month,month)).thenReturn(Map.of(month,Instant.EPOCH));
    when(store.overrides(entity,month,month)).thenReturn(List.of(new ForecastGridOverrides.Override(month,"IN","MANUAL",item.toString(),new BigDecimal("50.00"))));
    var result=service.grid(new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR),entity,month.toString(),1);
    assertThat(result.months().getFirst().moneyIn()).isEqualTo("0.00");
    assertThat(result.rows()).isEmpty();
  }
  @Test void frUc04_companyOverrideRemainsVisibleWhenDocumentsDisappear() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var entity=UUID.randomUUID();var month=YearMonth.of(2026,11);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),assumptions,mock(ForecastNoteRepository.class));
    when(store.months(entity,month,month)).thenReturn(Map.of(month,Instant.EPOCH));
    when(store.overrides(entity,month,month)).thenReturn(List.of(new ForecastGridOverrides.Override(month,"IN","COMPANY","Acme",new BigDecimal("50.00"))));
    var result=service.grid(new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR),entity,month.toString(),1);
    assertThat(result.months().getFirst().moneyIn()).isEqualTo("50.00");
    assertThat(result.rows()).singleElement().satisfies(row->{assertThat(row.source()).isEqualTo("COMPANY");assertThat(row.companyAmounts()).containsEntry("2026-11","50.00");});
  }
  @Test void frUc04_companyOverrideDeterminesTotalWithoutChangingRows() {
    var access=mock(ForecastAccess.class);var store=mock(ForecastGridRepository.class);var assumptions=mock(ForecastAssumptionRepository.class);
    var entity=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();var month=YearMonth.of(2026,11);
    var service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,mock(ForecastMonthRepository.class),new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),assumptions,mock(ForecastNoteRepository.class));
    when(store.months(entity,month,month)).thenReturn(Map.of(month,Instant.EPOCH));
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(month,new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("100.00"),"Bank")));
    when(store.lines(entity,month,month)).thenReturn(List.of(new ForecastGridRepository.Line(month,first,null,"IN","Acme",new BigDecimal("10.00"),"1"),new ForecastGridRepository.Line(month,second,null,"IN","Acme",new BigDecimal("20.00"),"2")));
    when(store.overrides(entity,month,month)).thenReturn(List.of(new ForecastGridOverrides.Override(month,"IN","COMPANY","Acme",new BigDecimal("50.00"))));
    var result=service.grid(new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR),entity,month.toString(),1);
    assertThat(result.months().getFirst().moneyIn()).isEqualTo("50.00");
    assertThat(result.months().getFirst().closing()).isEqualTo("150.00");
    assertThat(result.rows()).filteredOn(row->row.documentId().equals(first)).singleElement().satisfies(row->{assertThat(row.amounts()).containsEntry("2026-11","10.00");assertThat(row.companyAmounts()).containsEntry("2026-11","50.00");});
  }
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

  private static final YearMonth NOV=YearMonth.of(2026,11), DEC=NOV.plusMonths(1);

  @Test void frUc04_15_monthEndAddsOverdueToNextMonthAndLeavesCurrentMonthUntouched() {
    var run=new Generator();
    var unpaid=document("ACCREC","2026-10-10","100.00");
    var partPaid=document("ACCREC","2026-11-05","150.00");
    var bill=document("ACCPAY","2026-11-20","40.00");
    var unplaced=document("ACCREC","2026-09-30","7.00");
    run.save(NOV,unpaid.id(),null,"100.00");run.save(NOV,partPaid.id(),null,"200.00");run.save(NOV,bill.id(),null,"40.00");
    var result=run.click("2026-11-26",unpaid,partPaid,bill,unplaced);
    assertThat(run.amounts(DEC)).containsOnly(
        org.assertj.core.api.Assertions.entry(unpaid.id(),"100.00"),org.assertj.core.api.Assertions.entry(partPaid.id(),"150.00"),
        org.assertj.core.api.Assertions.entry(bill.id(),"40.00"),org.assertj.core.api.Assertions.entry(unplaced.id(),"7.00"));
    assertThat(run.appended).doesNotContainKey(NOV);
    verify(run.assumptions,never()).captureManualMonth(run.entity.id(),NOV);
    assertThat(result.preserved()).containsExactly("2026-11");
    assertThat(result.omittedCurrentItems()).isZero();
    assertThat(result.changedProtectedSchedules()).isZero();
  }

  @Test void frUc04_15_unpaidItemRollsForwardAtEachMonthEnd() {
    var run=new Generator();var invoice=document("ACCREC","2026-10-10","100.00");
    run.click("2026-10-25",invoice);
    assertThat(run.amounts(NOV)).containsOnlyKeys(invoice.id());
    run.click("2026-11-26",invoice);
    assertThat(run.amounts(NOV)).containsOnlyKeys(invoice.id());
    assertThat(run.amounts(DEC)).containsOnlyKeys(invoice.id());
    assertThat(run.appended.get(NOV)).isEqualTo(1);
  }

  @Test void frUc04_15_itemsNotYetOverdueAreNotCarried() {
    var run=new Generator();run.save(NOV,UUID.randomUUID(),null,"1.00");
    var dueToday=document("ACCREC","2026-11-26","10.00");
    var dueLater=document("ACCREC","2026-11-28","20.00");
    var december=document("ACCREC","2026-12-15","30.00");
    var result=run.click("2026-11-26",dueToday,dueLater,december);
    assertThat(run.amounts(DEC)).containsOnlyKeys(december.id());
    assertThat(result.omittedCurrentItems()).isEqualTo(2);
  }

  @Test void frUc04_15_itemPastDueOnTheClickDayIsCarried() {
    var run=new Generator();var invoice=document("ACCREC","2026-11-27","20.00");
    run.save(NOV,invoice.id(),null,"20.00");
    run.click("2026-11-30",invoice);
    assertThat(run.amounts(DEC)).containsOnlyKeys(invoice.id());
  }

  @Test void frUc04_15_unsavedCurrentMonthIsNotCreatedAtMonthEnd() {
    var run=new Generator();
    var overdue=document("ACCREC","2026-11-10","10.00");var dueLater=document("ACCREC","2026-11-28","20.00");
    var result=run.click("2026-11-26",overdue,dueLater);
    assertThat(run.appended).doesNotContainKey(NOV);
    assertThat(result.created()).hasSize(6).doesNotContain("2026-11");
    assertThat(result.preserved()).isEmpty();
    assertThat(result.omittedCurrentItems()).isEqualTo(1);
    assertThat(run.amounts(DEC)).containsOnlyKeys(overdue.id());
  }

  @Test void frUc04_15_legacyCurrentMonthIsNotCopiedAtMonthEnd() {
    var run=new Generator();
    when(run.legacy.generated(run.entity.id())).thenReturn(Map.of(NOV,new ForecastMonthRepository.GeneratedMonth(NOV,Instant.EPOCH,1)));
    var result=run.click("2026-11-26");
    assertThat(run.appended).doesNotContainKey(NOV);
    assertThat(result.preserved()).containsExactly("2026-11");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
      "2026-11-02,true,false","2026-11-10,true,false","2026-11-11,false,false","2026-11-20,false,false","2026-11-21,false,true",
      "2026-12-21,false,false","2026-12-22,false,true","2027-02-18,false,false","2027-02-19,false,true"})
  void frUc04_15_currentMonthIsWrittenOnDaysOneToTenAndOverdueCarriesInTheLastTenDays(String today,boolean writesCurrent,boolean carries) {
    var run=new Generator();var now=YearMonth.from(LocalDate.parse(today));
    var overdue=document("ACCREC",now.atDay(1).minusDays(5).toString(),"10.00");
    var result=run.click(today,overdue);
    if(writesCurrent) assertThat(run.amounts(now)).containsOnlyKeys(overdue.id());
    else assertThat(run.appended).doesNotContainKey(now);
    if(carries) assertThat(run.amounts(now.plusMonths(1))).containsOnlyKeys(overdue.id());
    else assertThat(run.amounts(now.plusMonths(1))).isEmpty();
    assertThat(result.omittedCurrentItems()).isEqualTo(writesCurrent||carries?0:1);
  }

  @Test void frUc04_15_earlyMonthUpdateKeepsSavedLinesAndAddsUnlistedUnpaidItems() {
    var run=new Generator();
    var paid=UUID.randomUUID();
    var partPaid=document("ACCREC","2026-11-20","30.00");
    var missedCarry=document("ACCPAY","2026-11-15","15.00");
    var newThisMonth=document("ACCREC","2026-12-20","50.00");
    run.save(NOV,partPaid.id(),null,"80.00");run.save(NOV,missedCarry.id(),null,"15.00");
    run.save(DEC,paid,null,"100.00");run.save(DEC,partPaid.id(),null,"80.00");
    var result=run.click("2026-12-05",partPaid,missedCarry,newThisMonth);
    assertThat(run.amounts(DEC)).containsOnly(
        org.assertj.core.api.Assertions.entry(paid,"100.00"),org.assertj.core.api.Assertions.entry(partPaid.id(),"80.00"),
        org.assertj.core.api.Assertions.entry(missedCarry.id(),"15.00"),org.assertj.core.api.Assertions.entry(newThisMonth.id(),"50.00"));
    assertThat(run.appended).doesNotContainKey(NOV).containsEntry(DEC,1);
    verify(run.assumptions).captureManualMonth(run.entity.id(),DEC);
    assertThat(result.refreshed()).containsExactly("2026-12");
    assertThat(result.preserved()).isEmpty();
    assertThat(result.omittedCurrentItems()).isZero();
  }

  @Test void frUc04_15_earlyMonthWithNothingToAddKeepsTheSavedRevision() {
    var run=new Generator();var saved=document("ACCREC","2026-12-20","50.00");run.save(DEC,saved.id(),null,"50.00");
    var result=run.click("2026-12-05",saved);
    assertThat(run.appended).doesNotContainKey(DEC);
    assertThat(result.preserved()).containsExactly("2026-12");
  }

  @Test void frUc04_15_savedUnsplitDocumentIsNotAddedAgainAsAnOverdueSplit() {
    var run=new Generator();var id=UUID.randomUUID();run.save(DEC,id,null,"100.00");
    var split=new ForecastDataset.Document(id,"ACCREC","1","A",LocalDate.of(2026,11,1),LocalDate.of(2026,11,10),null,null,new BigDecimal("100.00"),false,null,
        List.of(new ForecastDataset.Split(1,LocalDate.of(2026,11,20),new BigDecimal("100.00"))),false);
    var result=run.click("2026-12-05",split);
    assertThat(run.appended).doesNotContainKey(DEC);
    assertThat(result.changedProtectedSchedules()).isEqualTo(1);
  }

  @Test void frUc04_15_earlyMonthLegacyCopyAlsoReceivesUnlistedItems() {
    var run=new Generator();var legacyDocument=UUID.randomUUID();var added=document("ACCREC","2026-12-20","50.00");
    when(run.legacy.generated(run.entity.id())).thenReturn(Map.of(DEC,new ForecastMonthRepository.GeneratedMonth(DEC,Instant.EPOCH,1)));
    var frozen=new com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution(legacyDocument,"DOCUMENT","IN","ACCREC","1","Synthetic contact",
        LocalDate.of(2026,12,1),DEC,new BigDecimal("9.00"),com.airconcepts.dashboard.forecast.calc.DateRule.DUE_DATE,false,null,null);
    when(run.legacy.lines(eq(run.entity.id()),any(),any())).thenReturn(Map.of(DEC,List.of(frozen)));
    var result=run.click("2026-12-05",added);
    assertThat(run.amounts(DEC)).containsOnly(org.assertj.core.api.Assertions.entry(legacyDocument,"9.00"),org.assertj.core.api.Assertions.entry(added.id(),"50.00"));
    assertThat(result.refreshed()).containsExactly("2026-12");
  }

  @Test void frUc04_15_excludedAndRescheduledItemsAreNotCarriedAsOverdue() {
    var run=new Generator();var base=document("ACCREC","2026-10-10","10.00");
    var excluded=new ForecastDataset.Document(base.id(),"ACCREC","1","A",base.issueDate(),base.dueDate(),null,null,base.amountDue(),true,"Disputed",List.of(),false);
    var rescheduled=new ForecastDataset.Document(UUID.randomUUID(),"ACCREC","2","B",base.issueDate(),base.dueDate(),null,LocalDate.of(2027,1,15),new BigDecimal("20.00"),false,null,List.of(),false);
    run.click("2026-11-26",excluded,rescheduled);
    assertThat(run.amounts(DEC)).isEmpty();
    assertThat(run.amounts(DEC.plusMonths(1))).containsOnlyKeys(rescheduled.id());
  }

  @Test void frUc04_15_overdueSplitIsCarriedOnceAndRemainingChangedScheduleIsReported() {
    var run=new Generator();var id=UUID.randomUUID();run.save(NOV,id,null,"100.00");
    var split=new ForecastDataset.Document(id,"ACCREC","1","A",LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),null,null,new BigDecimal("100.00"),false,null,
        List.of(new ForecastDataset.Split(1,LocalDate.of(2026,11,1),new BigDecimal("60.00")),new ForecastDataset.Split(2,LocalDate.of(2027,1,10),new BigDecimal("40.00"))),false);
    var result=run.click("2026-11-26",split);
    assertThat(run.amounts(DEC)).containsExactly(org.assertj.core.api.Assertions.entry(id,"60.00"));
    assertThat(run.amounts(DEC.plusMonths(1))).isEmpty();
    assertThat(result.changedProtectedSchedules()).isEqualTo(1);
  }

  private static ForecastDataset.Document document(String type,String due,String amount) {
    var dueDate=LocalDate.parse(due);
    return new ForecastDataset.Document(UUID.randomUUID(),type,"1","Synthetic contact",dueDate.minusDays(20),dueDate,null,null,new BigDecimal(amount),false,null,List.of(),false);
  }

  /** In-memory stand-in for the grid store so consecutive Create forecast clicks can be followed. */
  private static final class Generator {
    final Entity entity=new Entity(UUID.randomUUID(),"Synthetic");
    final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
    final ForecastMonthRepository legacy=mock(ForecastMonthRepository.class);
    final ForecastAssumptionRepository assumptions=mock(ForecastAssumptionRepository.class);
    final Map<YearMonth,List<ForecastGridRepository.Line>> saved=new TreeMap<>();
    /** Number of revisions written by clicks, per month. */
    final Map<YearMonth,Integer> appended=new HashMap<>();

    void save(YearMonth month,UUID document,Integer sequence,String amount) {
      saved.computeIfAbsent(month,m->new ArrayList<>()).add(new ForecastGridRepository.Line(month,document,sequence,"IN","Synthetic contact",new BigDecimal(amount)));
    }
    Map<UUID,String> amounts(YearMonth month) {
      var result=new HashMap<UUID,String>();
      for(var line:saved.getOrDefault(month,List.of()))
        assertThat(result.put(line.documentId(),line.amount().toPlainString())).as("one line per document in "+month).isNull();
      return result;
    }
    @SuppressWarnings("unchecked")
    ForecastGridService.Generation click(String today,ForecastDataset.Document... documents) {
      var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);
      var clock=Clock.fixed(Instant.parse(today+"T00:00:00Z"),ZoneOffset.UTC);var now=YearMonth.from(LocalDate.parse(today));
      when(access.requireWrite(user,entity.id())).thenReturn(entity);
      when(store.months(eq(entity.id()),any(),any())).thenAnswer(call->{
        YearMonth from=call.getArgument(1),to=call.getArgument(2);var result=new HashMap<YearMonth,Instant>();
        saved.keySet().stream().filter(m->!m.isBefore(from)&&!m.isAfter(to)).forEach(m->result.put(m,Instant.EPOCH));return result;
      });
      when(store.lines(eq(entity.id()),any(),any())).thenAnswer(call->{
        YearMonth from=call.getArgument(1),to=call.getArgument(2);
        return saved.entrySet().stream().filter(e->!e.getKey().isBefore(from)&&!e.getKey().isAfter(to)).flatMap(e->e.getValue().stream()).toList();
      });
      doAnswer(call->{
        YearMonth month=call.getArgument(1);List<com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution> lines=call.getArgument(3);
        saved.put(month,new ArrayList<>(lines.stream().map(c->new ForecastGridRepository.Line(month,c.sourceId(),c.splitSequence(),c.direction(),c.label(),c.amount())).toList()));
        appended.merge(month,1,Integer::sum);return null;
      }).when(store).append(eq(entity.id()),any(),any(),anyList());
      when(loader.load(entity,now)).thenReturn(new ForecastDataset(entity.id(),"Synthetic",now,LocalDate.parse(today),null,null,List.of(documents),List.of(),List.of(),
          new ForecastDataset.SyncInfo(Instant.EPOCH,"SUCCEEDED",false)));
      return new ForecastGridService(access,loader,store,legacy,new ForecastCalendar(4,clock),assumptions,mock(ForecastNoteRepository.class)).generate(user,entity.id());
    }
  }
  @Test void frUc04_refreshRetiresFutureOverridesForSettledDocuments() {
    var access=mock(ForecastAccess.class);var loader=mock(ForecastDatasetLoader.class);var store=mock(ForecastGridRepository.class);var legacy=mock(ForecastMonthRepository.class);
    var now=YearMonth.of(2026,11);var id=UUID.randomUUID();var entity=new Entity(id,"Synthetic");var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
    var service=new ForecastGridService(access,loader,store,legacy,new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
    var change=new ForecastGridOverrides.Override(now.plusMonths(1),"IN","DOCUMENT",UUID.randomUUID().toString(),new BigDecimal("50.00"));
    when(access.requireWrite(user,id)).thenReturn(entity);
    when(store.overrides(id,now.plusMonths(1),now.plusMonths(6))).thenReturn(List.of(change));
    when(loader.load(entity,now)).thenReturn(new ForecastDataset(id,"Synthetic",now,LocalDate.of(2026,11,10),null,null,List.of(),List.of(),List.of(),new ForecastDataset.SyncInfo(Instant.EPOCH,"SUCCEEDED",false)));
    service.generate(user,id);
    verify(store).deactivateOverride(id,UUID.fromString(user.subject()),change);
  }
}
