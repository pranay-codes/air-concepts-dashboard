package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.Split;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastAdjustmentServiceTest {
  private final ForecastAdjustmentRepository repository=mock(ForecastAdjustmentRepository.class);
  private final ForecastAdjustmentService service=new ForecastAdjustmentService(repository,new ForecastAccess(mock(EntityService.class)));
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  private final UUID entity=UUID.randomUUID(),id=UUID.randomUUID();
  @Test void frUc04_15_exclusionReasonRequired(){
    for(String reason:Arrays.asList(null," ","x".repeat(501))) {
      var change=new ForecastAdjustmentService.AdjustmentChange();change.setExcluded(true);change.setExclusionReason(reason);
      assertThatThrownBy(()->service.adjust(user,entity,id,change)).isInstanceOfSatisfying(ApiFailure.class,e->assertThat(e.code()).isEqualTo("FORECAST_EXCLUSION_REASON_REQUIRED"));
    }
    verifyNoInteractions(repository);
  }
  @Test void frUc04_15_splitSumMismatchIs422(){
    when(repository.document(entity,id)).thenReturn(new ForecastAdjustmentRepository.DocumentState(id,new BigDecimal("100"),true));
    assertThatThrownBy(()->service.splits(user,entity,id,List.of(new Split(1,LocalDate.of(2026,11,20),new BigDecimal("99")))))
        .isInstanceOfSatisfying(ApiFailure.class,e->assertThat(e.code()).isEqualTo("FORECAST_SPLIT_SUM_MISMATCH"));
  }
  @Test void sec_adjustmentOnSettledDocumentIs409(){
    when(repository.document(entity,id)).thenReturn(new ForecastAdjustmentRepository.DocumentState(id,BigDecimal.ZERO,false));
    assertThatThrownBy(()->service.splits(user,entity,id,List.of())).isInstanceOfSatisfying(ApiFailure.class,e->assertThat(e.code()).isEqualTo("FORECAST_DOCUMENT_NOT_OUTSTANDING"));
  }
  @Test void frUc04_16_dateChangeWithSplitsFlagsConflictAndKeepsLines(){
    assertThat(ForecastConflictPolicy.changed(true,LocalDate.of(2026,11,1),new BigDecimal("100"),LocalDate.of(2026,11,2),new BigDecimal("100"))).isTrue();
    assertThat(ForecastConflictPolicy.changed(true,null,new BigDecimal("100"),null,new BigDecimal("99"))).isTrue();
    assertThat(ForecastConflictPolicy.changed(false,null,new BigDecimal("100"),null,new BigDecimal("99"))).isFalse();
    assertThat(ForecastConflictPolicy.changed(true,null,new BigDecimal("100.00"),null,new BigDecimal("100"))).isFalse();
  }
}
