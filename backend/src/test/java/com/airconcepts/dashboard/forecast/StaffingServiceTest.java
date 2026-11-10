package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.StaffingLine;
import com.airconcepts.dashboard.security.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
class StaffingServiceTest {
  @Test void frUc04_03_staffingPartialEditPreservesExistingCostAndDates(){
    var repository=mock(ForecastAssumptionRepository.class);var service=new StaffingService(repository,new ForecastAccess(mock(EntityService.class)));
    UUID entity=UUID.randomUUID(),id=UUID.randomUUID();var user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
    var original=new StaffingLine(id,"Role",new BigDecimal("1000.00"),15,YearMonth.of(2026,11),null,true);when(repository.staffForUpdate(entity,id)).thenReturn(Optional.of(original));when(repository.saveStaff(any(),any(),any())).thenAnswer(i->i.getArgument(2));
    var changed=service.patch(user,entity,id,Map.of("label","Updated role"));assertThat(changed.label()).isEqualTo("Updated role");assertThat(changed.monthlyCost()).isEqualByComparingTo("1000.00");assertThat(changed.startMonth()).isEqualTo(original.startMonth());
  }
}
