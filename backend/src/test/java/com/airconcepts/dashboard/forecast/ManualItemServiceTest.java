package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.ManualItem;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ManualItemServiceTest {
  private final ForecastAssumptionRepository repository=mock(ForecastAssumptionRepository.class);
  private final ManualItemService service=new ManualItemService(repository,new ForecastAccess(mock(EntityService.class)));
  private final UUID entity=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  @Test void frUc04_03_validationMatrix() {
    for(var item:List.of(item("", "200", 1),item("x".repeat(201),"200",1),item("Rent","0",1),item("Rent","-1",1),item("Rent","1",32))) {
      assertThatThrownBy(()->service.create(user,entity,item)).isInstanceOf(ApiFailure.class);
    }
    verifyNoInteractions(repository);
  }
  @Test void frUc04_03_crossEntityIdIs404() {
    UUID foreign=UUID.randomUUID();when(repository.manual(entity,foreign)).thenReturn(Optional.empty());
    assertThatThrownBy(()->service.update(user,entity,foreign,item("Rent","200",1)))
        .isInstanceOfSatisfying(ApiFailure.class,e->assertThat(e.status().value()).isEqualTo(404));
    verify(repository,never()).saveManual(any(),any(),any());
  }
  @Test void frUc04_03_softDeletePreservesRowAndAudits() {
    var existing=item("Rent","200",1);when(repository.manualForUpdate(entity,existing.id())).thenReturn(Optional.of(existing));
    service.deactivate(user,entity,existing.id());
    verify(repository).saveManual(eq(entity),eq(UUID.fromString(user.subject())),argThat(i->!i.active()&&i.id().equals(existing.id())));
  }
  @Test void frUc04_03_patchAcceptsJsonNumericAmount() {
    var existing=item("Rent","200.00",15);when(repository.manualForUpdate(entity,existing.id())).thenReturn(Optional.of(existing));when(repository.saveManual(any(),any(),any())).thenAnswer(i->i.getArgument(2));
    var patch=new HashMap<String,Object>();patch.put("amount",250.5);
    assertThat(service.patch(user,entity,existing.id(),patch).amount()).isEqualByComparingTo("250.50");
  }
  private ManualItem item(String name,String amount,Integer day) {
    return new ManualItem(UUID.randomUUID(),"OUT","RENT",name,new BigDecimal(amount),"MONTHLY",day,YearMonth.of(2026,11),null,true);
  }
  @Test void frUc04_03_partialEditPreservesUnspecifiedFieldsAndCanClearEndMonth(){
    var existing=item("Rent","200.00",15);when(repository.manualForUpdate(entity,existing.id())).thenReturn(Optional.of(existing));when(repository.saveManual(any(),any(),any())).thenAnswer(i->i.getArgument(2));
    var patch=new HashMap<String,Object>();patch.put("description","Office rent");patch.put("endMonth",null);
    var changed=service.patch(user,entity,existing.id(),patch);
    assertThat(changed.description()).isEqualTo("Office rent");assertThat(changed.amount()).isEqualByComparingTo(existing.amount());assertThat(changed.dayOfMonth()).isEqualTo(15);assertThat(changed.startMonth()).isEqualTo(existing.startMonth());assertThat(changed.endMonth()).isNull();
  }
}
