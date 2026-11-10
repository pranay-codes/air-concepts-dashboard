package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
class ForecastStoredDocumentsTest {
  @Test void sec_foreignEntityIdIs404(){
    var jdbc=mock(JdbcTemplate.class);var access=mock(ForecastAccess.class);var adjustments=mock(ForecastAdjustmentRepository.class);
    UUID entity=UUID.randomUUID(),foreign=UUID.randomUUID();
    var service=new ForecastStoredDocuments(jdbc,access,adjustments);var user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
    assertThatThrownBy(()->service.get(user,entity,foreign)).isInstanceOfSatisfying(ApiFailure.class,e->assertThat(e.status().value()).isEqualTo(404));
    verifyNoInteractions(adjustments);
  }
}
