package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastPeriodServiceTest {
  private final ForecastAssumptionRepository repository=mock(ForecastAssumptionRepository.class);
  private final EntityService entities=mock(EntityService.class);
  private final ForecastAccess access=new ForecastAccess(entities);
  private final ForecastPeriodService service=new ForecastPeriodService(repository,access);
  private final UUID entity=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  @Test void frUc04_04_upsertOpeningBalanceWithAudit() {
    service.save(user,entity,LocalDate.of(2026,11,1),new BigDecimal("1000.00"),"Statement");
    verify(repository).saveOpening(entity,UUID.fromString(user.subject()),LocalDate.of(2026,11,1),new BigDecimal("1000.00"),"Statement");
  }
  @Test void frUc04_04_rejectsNonFirstDayMonth() {
    assertThatThrownBy(()->service.save(user,entity,LocalDate.of(2026,11,2),BigDecimal.ZERO,"Statement")).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(repository);
  }
  @Test void frUc04_07_standardUserAndInactiveCannotReadOrWrite() {
    for(Role role:Arrays.asList(Role.STANDARD_USER,null)) {
      var denied=new CurrentUser(user.subject(),role);
      assertThatThrownBy(()->service.save(denied,entity,LocalDate.of(2026,11,1),BigDecimal.ZERO,null))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    verifyNoInteractions(repository,entities);
  }
}
