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
    var p=new ForecastPatch(fields,Set.of("label","monthlyCost","payDayOfMonth","startMonth","endMonth","active","hourlyRate","weeklyHours","paymentFrequency","firstPayday","employmentStart","employmentEnd"));
    return repository.saveStaff(entity,UUID.fromString(user.subject()),validated(id,new StaffingLine(id,p.text("label",old.label()),p.money("monthlyCost",old.monthlyCost()),p.day("payDayOfMonth",old.payDayOfMonth()),p.month("startMonth",old.startMonth()),p.month("endMonth",old.endMonth()),p.active(old.active()),p.money("hourlyRate",old.hourlyRate()),p.money("weeklyHours",old.weeklyHours()),p.text("paymentFrequency",old.paymentFrequency()),p.date("firstPayday",old.firstPayday()),p.date("employmentStart",old.employmentStart()),p.date("employmentEnd",old.employmentEnd()))));
  }
  @org.springframework.transaction.annotation.Transactional
  public StaffingLine deactivate(CurrentUser user,UUID entity,UUID id){access.requireWrite(user,entity);var i=repository.staffForUpdate(entity,id).orElseThrow(ForecastValidation::missing);
    return repository.saveStaff(entity,UUID.fromString(user.subject()),new StaffingLine(id,i.label(),i.monthlyCost(),i.payDayOfMonth(),i.startMonth(),i.endMonth(),false,i.hourlyRate(),i.weeklyHours(),i.paymentFrequency(),i.firstPayday(),i.employmentStart(),i.employmentEnd()));}
  private static StaffingLine validated(UUID id,StaffingLine i){
    if(i==null)throw ForecastValidation.invalid("staffing","Enter the staffing line.");
    var label=ForecastValidation.text("label",i.label(),160);var amount=ForecastValidation.money("monthlyCost",i.monthlyCost()==null&&i.paymentFrequency()!=null?java.math.BigDecimal.ZERO:i.monthlyCost(),false);
    if(amount.signum()<0)throw ForecastValidation.invalid("monthlyCost","Use zero or a positive amount.");
    ForecastValidation.months(i.startMonth(),i.endMonth());ForecastValidation.day("payDayOfMonth",i.payDayOfMonth());
    if(i.paymentFrequency()!=null) {
      if(!Set.of("WEEKLY","FORTNIGHTLY","MONTHLY").contains(i.paymentFrequency())||i.firstPayday()==null)throw ForecastValidation.invalid("paymentFrequency","Choose a frequency and first payday.");
      var rate=ForecastValidation.money("hourlyRate",i.hourlyRate(),false);var hours=ForecastValidation.money("weeklyHours",i.weeklyHours(),false);
      if(rate.signum()<0||hours.signum()<0||hours.compareTo(new java.math.BigDecimal("168"))>0)throw ForecastValidation.invalid("weeklyHours","Use a non-negative rate and up to 168 weekly hours.");
      if(i.employmentStart()!=null&&i.employmentEnd()!=null&&i.employmentEnd().isBefore(i.employmentStart()))throw ForecastValidation.invalid("employmentEnd","End date must be on or after start date.");
      return new StaffingLine(id,label,amount,i.payDayOfMonth(),i.startMonth(),i.endMonth(),i.active(),rate,hours,i.paymentFrequency(),i.firstPayday(),i.employmentStart(),i.employmentEnd());
    }
    if(i.hourlyRate()!=null||i.weeklyHours()!=null||i.firstPayday()!=null||i.employmentStart()!=null||i.employmentEnd()!=null)throw ForecastValidation.invalid("paymentFrequency","Choose a frequency and first payday.");
    return new StaffingLine(id,label,amount,i.payDayOfMonth(),i.startMonth(),i.endMonth(),i.active());
  }
}
