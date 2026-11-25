package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.StaffingLine;
import com.airconcepts.dashboard.security.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
class StaffingServiceTest {
  @Test void frUc04_03_rejectsStandardUserAndForeignEntityRecord(){
    var repository=mock(ForecastAssumptionRepository.class);var service=new StaffingService(repository,new ForecastAccess(mock(EntityService.class)));
    var entity=UUID.randomUUID();var id=UUID.randomUUID();var standard=new CurrentUser(UUID.randomUUID().toString(),Role.STANDARD_USER);
    assertThatThrownBy(()->service.list(standard,entity,false)).isInstanceOf(RuntimeException.class);
    verify(repository,never()).staffing(any(),anyBoolean());
    var admin=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
    when(repository.staffForUpdate(entity,id)).thenReturn(Optional.empty());
    assertThatThrownBy(()->service.patch(admin,entity,id,Map.of("label","Changed"))).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verify(repository,never()).saveStaff(any(),any(),any());
  }
  @Test void frUc04_03_scheduleIsPreservedOnEditAndDeactivate(){
    var repository=mock(ForecastAssumptionRepository.class);var service=new StaffingService(repository,new ForecastAccess(mock(EntityService.class)));
    var entity=UUID.randomUUID();var id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
    var original=new StaffingLine(id,"Role",BigDecimal.ZERO,null,YearMonth.of(2026,11),null,true,new BigDecimal("35.00"),new BigDecimal("40.00"),"FORTNIGHTLY",LocalDate.of(2026,11,6),null,null);
    when(repository.staffForUpdate(entity,id)).thenReturn(Optional.of(original));when(repository.saveStaff(any(),any(),any())).thenAnswer(i->i.getArgument(2));
    var changed=service.patch(user,entity,id,Map.of("label","Updated"));assertThat(changed.paymentFrequency()).isEqualTo("FORTNIGHTLY");assertThat(changed.firstPayday()).isEqualTo(original.firstPayday());
    var inactive=service.deactivate(user,entity,id);assertThat(inactive.active()).isFalse();assertThat(inactive.hourlyRate()).isEqualByComparingTo("35.00");
  }
  @Test void frUc04_03_staffingPartialEditPreservesExistingCostAndDates(){
    var repository=mock(ForecastAssumptionRepository.class);var service=new StaffingService(repository,new ForecastAccess(mock(EntityService.class)));
    UUID entity=UUID.randomUUID(),id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
    var original=new StaffingLine(id,"Role",new BigDecimal("1000.00"),15,YearMonth.of(2026,11),null,true);when(repository.staffForUpdate(entity,id)).thenReturn(Optional.of(original));when(repository.saveStaff(any(),any(),any())).thenAnswer(i->i.getArgument(2));
    var changed=service.patch(user,entity,id,Map.of("label","Updated role"));assertThat(changed.label()).isEqualTo("Updated role");assertThat(changed.monthlyCost()).isEqualByComparingTo("1000.00");assertThat(changed.startMonth()).isEqualTo(original.startMonth());
  }
}
