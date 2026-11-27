package com.airconcepts.dashboard.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.ForecastGridService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForecastGridControllerTest {
  private final ForecastGridService service=mock(ForecastGridService.class);
  private final ForecastGridController controller=new ForecastGridController(service,mock(CurrentUserResolver.class));
  @Test void frUc04_missingAmountDoesNotClearAnOverride() {
    var body=new ForecastGridController.AmountRequest("2026-11","IN","COMPANY","Acme",null,null);
    assertThatThrownBy(()->controller.saveAmount(null,UUID.randomUUID(),body)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(service);
  }
  @Test void frUc04_rejectsAmbiguousResetAndAmount() {
    var body=new ForecastGridController.AmountRequest("2026-11","IN","COMPANY","Acme",BigDecimal.ONE,true);
    assertThatThrownBy(()->controller.saveAmount(null,UUID.randomUUID(),body)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(service);
  }
}
