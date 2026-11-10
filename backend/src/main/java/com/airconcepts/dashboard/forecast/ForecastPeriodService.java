package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.forecast.ForecastAssumptionRepository.OpeningBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class ForecastPeriodService {
  private final ForecastAssumptionRepository repository;private final ForecastAccess access;
  public ForecastPeriodService(ForecastAssumptionRepository repository,ForecastAccess access){this.repository=repository;this.access=access;}
  public OpeningBalance get(CurrentUser user,UUID entity,LocalDate month){access.requireRead(user,entity);firstDay(month);return repository.opening(entity,month);}
  public OpeningBalance save(CurrentUser user,UUID entity,LocalDate month,BigDecimal balance,String source){
    access.requireWrite(user,entity);firstDay(month);var value=ForecastValidation.money("openingBalance",balance,false);
    if(source!=null&&source.length()>500)throw ForecastValidation.invalid("source","Use at most 500 characters.");
    return repository.saveOpening(entity,UUID.fromString(user.subject()),month,value,source);
  }
  private void firstDay(LocalDate month){if(month==null||month.getDayOfMonth()!=1)throw ForecastValidation.invalid("startMonth","Choose the first day of a month.");}
}
