package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.StaffingLine;
import com.airconcepts.dashboard.security.CurrentUser;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class StaffingService {
  private final ForecastAssumptionRepository repository;private final ForecastAccess access;
  public StaffingService(ForecastAssumptionRepository repository,ForecastAccess access){this.repository=repository;this.access=access;}
  public List<StaffingLine> list(CurrentUser user,UUID entity,boolean inactive){access.requireRead(user,entity);return repository.staffing(entity,inactive);}
  public StaffingLine create(CurrentUser user,UUID entity,StaffingLine item){access.requireWrite(user,entity);return repository.saveStaff(entity,UUID.fromString(user.subject()),validated(UUID.randomUUID(),item));}
  public StaffingLine update(CurrentUser user,UUID entity,UUID id,StaffingLine item){access.requireWrite(user,entity);repository.staff(entity,id).orElseThrow(ForecastValidation::missing);return repository.saveStaff(entity,UUID.fromString(user.subject()),validated(id,item));}
  @org.springframework.transaction.annotation.Transactional
  public StaffingLine patch(CurrentUser user,UUID entity,UUID id,Map<String,Object> fields){
    access.requireWrite(user,entity);var old=repository.staffForUpdate(entity,id).orElseThrow(ForecastValidation::missing);
    var p=new ForecastPatch(fields,Set.of("label","monthlyCost","payDayOfMonth","startMonth","endMonth","active"));
    return repository.saveStaff(entity,UUID.fromString(user.subject()),validated(id,new StaffingLine(id,p.text("label",old.label()),p.money("monthlyCost",old.monthlyCost()),p.day("payDayOfMonth",old.payDayOfMonth()),p.month("startMonth",old.startMonth()),p.month("endMonth",old.endMonth()),p.active(old.active()))));
  }
  @org.springframework.transaction.annotation.Transactional
  public StaffingLine deactivate(CurrentUser user,UUID entity,UUID id){access.requireWrite(user,entity);var i=repository.staffForUpdate(entity,id).orElseThrow(ForecastValidation::missing);
    return repository.saveStaff(entity,UUID.fromString(user.subject()),new StaffingLine(id,i.label(),i.monthlyCost(),i.payDayOfMonth(),i.startMonth(),i.endMonth(),false));}
  private static StaffingLine validated(UUID id,StaffingLine i){
    if(i==null)throw ForecastValidation.invalid("staffing","Enter the staffing line.");
    var label=ForecastValidation.text("label",i.label(),160);var amount=ForecastValidation.money("monthlyCost",i.monthlyCost(),false);
    if(amount.signum()<0)throw ForecastValidation.invalid("monthlyCost","Use zero or a positive amount.");
    ForecastValidation.months(i.startMonth(),i.endMonth());ForecastValidation.day("payDayOfMonth",i.payDayOfMonth());
    return new StaffingLine(id,label,amount,i.payDayOfMonth(),i.startMonth(),i.endMonth(),i.active());
  }
}
