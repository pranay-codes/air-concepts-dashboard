package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForecastGridAmountServiceTest {
  private final ForecastAccess access=new ForecastAccess(mock(EntityService.class));
  private final ForecastGridRepository store=mock(ForecastGridRepository.class);
  private final ForecastMonthRepository legacy=mock(ForecastMonthRepository.class);
  private final ForecastGridService service=new ForecastGridService(access,mock(ForecastDatasetLoader.class),store,legacy,
      new ForecastCalendar(4,Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC)),mock(ForecastAssumptionRepository.class),mock(ForecastNoteRepository.class));
  private final UUID entity=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  @Test void frUc04_rejectsHistoricalAmounts() {
    assertThatThrownBy(()->service.saveAmount(user,entity,"2026-10","IN","COMPANY","Acme",BigDecimal.ONE)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(store,legacy);
  }
  @Test void frUc04_rejectsForeignCompany() {
    assertThatThrownBy(()->service.saveAmount(user,entity,"2026-11","IN","COMPANY","Foreign",BigDecimal.ONE)).isInstanceOf(ApiFailure.class);
    verify(store,never()).saveOverride(any(),any(),any());
  }
  @Test void frUc04_rejectsForeignDocumentAndManualItems() {
    var foreign=UUID.randomUUID().toString();
    for(var scope:List.of("DOCUMENT","MANUAL"))
      assertThatThrownBy(()->service.saveAmount(user,entity,"2026-11","IN",scope,foreign,BigDecimal.ONE)).isInstanceOf(ApiFailure.class);
    verify(store,never()).saveOverride(any(),any(),any());
  }
  @Test void frUc04_rejectsStandardUser() {
    var denied=new CurrentUser(user.subject(),Role.STANDARD_USER);
    assertThatThrownBy(()->service.saveAmount(denied,entity,"2026-11","IN","COMPANY","Acme",BigDecimal.ONE)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(store,legacy);
  }
  @Test void frUc04_restoresCalculatedCompanyAmount() {
    var month=YearMonth.of(2026,11);
    var existing=new ForecastGridOverrides.Override(month,"IN","COMPANY","Acme",new BigDecimal("50.00"));
    when(store.companyExists(entity,"Acme","IN")).thenReturn(true);
    when(store.overrides(entity,month,month)).thenReturn(List.of(existing));
    service.saveAmount(user,entity,"2026-11","IN","COMPANY","Acme",null);
    verify(store).deactivateOverride(entity,UUID.fromString(user.subject()),existing);
    verify(store,never()).saveOverride(any(),any(),any());
  }
  @Test void frUc04_canRestoreFutureSettledDocumentButCannotSetNewAmount() {
    var month=YearMonth.of(2026,12);var document=UUID.randomUUID();
    var existing=new ForecastGridOverrides.Override(month,"IN","DOCUMENT",document.toString(),new BigDecimal("50.00"));
    when(store.documentTemplate(entity,document,"IN",month)).thenReturn(new ForecastGridRepository.Line(month,document,null,"IN","Acme",BigDecimal.ZERO));
    when(store.overrides(entity,month,month)).thenReturn(List.of(existing));
    service.saveAmount(user,entity,month.toString(),"IN","DOCUMENT",document.toString(),null);
    verify(store).deactivateOverride(entity,UUID.fromString(user.subject()),existing);
    assertThatThrownBy(()->service.saveAmount(user,entity,month.toString(),"IN","DOCUMENT",document.toString(),BigDecimal.ONE)).isInstanceOf(ApiFailure.class);
    verify(store,never()).saveOverride(any(),any(),any());
  }
}
