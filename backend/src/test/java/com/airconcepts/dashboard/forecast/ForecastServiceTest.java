package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastServiceTest {
  private final ForecastDatasetLoader loader=mock(ForecastDatasetLoader.class);
  private final ForecastAccess access=mock(ForecastAccess.class);
  private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
  private final ForecastService service=new ForecastService(loader,access,clock);
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  @Test void frUc04_13_combinedSumsOnlyRequestedEntitiesAndNeverReturnsDocuments(){
    UUID a=UUID.randomUUID(),b=UUID.randomUUID();setup(a,BigDecimal.TEN);setup(b,BigDecimal.ONE);
    var result=service.combined(user,List.of(a,b),"2026-11");
    assertThat(result.months().getFirst().opening()).isEqualByComparingTo("11");
    assertThat(result.entities()).hasSize(2);assertThat(result.complete()).isTrue();
    assertThat(result.getClass().getRecordComponents()).noneSatisfy(c->assertThat(c.getName()).isIn("documents","staffing","contributions"));
  }
  @Test void frUc04_13_archivedEntityFailsWholeRequest(){
    UUID a=UUID.randomUUID(),b=UUID.randomUUID();setup(a,BigDecimal.TEN);
    when(access.requireRead(user,b)).thenThrow(ForecastValidation.missing());
    assertThatThrownBy(()->service.combined(user,List.of(a,b),"2026-11")).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
  }
  @Test void frUc04_13_missingOpeningIsIncompleteAndNeverZero(){
    UUID a=UUID.randomUUID();setup(a,null);var result=service.combined(user,List.of(a),"2026-11");
    assertThat(result.complete()).isFalse();assertThat(result.months().getFirst().opening()).isNull();
  }
  private void setup(UUID id,BigDecimal opening){
    var entity=new Entity(id,"Synthetic");when(access.requireRead(user,id)).thenReturn(entity);
    when(loader.load(entity,YearMonth.of(2026,11))).thenReturn(new ForecastDataset(id,"Synthetic",YearMonth.of(2026,11),LocalDate.of(2026,11,10),opening,"Statement",List.of(),List.of(),List.of(),
        new SyncInfo(clock.instant(),"SUCCEEDED",false)));
  }
}
