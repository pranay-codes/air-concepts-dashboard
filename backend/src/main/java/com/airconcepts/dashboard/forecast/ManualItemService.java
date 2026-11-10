package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.ManualItem;
import com.airconcepts.dashboard.security.CurrentUser;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ManualItemService {
  private static final Set<String> CATEGORIES=Set.of("DIRECT_DEBIT","LOAN","RENT","TAX","TRANSFER","OTHER_PAYMENT","OTHER_RECEIPT");
  private final ForecastAssumptionRepository repository;private final ForecastAccess access;
  public ManualItemService(ForecastAssumptionRepository repository,ForecastAccess access){this.repository=repository;this.access=access;}
  public List<ManualItem> list(CurrentUser user,UUID entity,boolean inactive){access.requireRead(user,entity);return repository.manualItems(entity,inactive);}
  public ManualItem create(CurrentUser user,UUID entity,ManualItem item){access.requireWrite(user,entity);return repository.saveManual(entity,actor(user),validated(UUID.randomUUID(),item));}
  public ManualItem update(CurrentUser user,UUID entity,UUID id,ManualItem item){access.requireWrite(user,entity);repository.manual(entity,id).orElseThrow(ForecastValidation::missing);return repository.saveManual(entity,actor(user),validated(id,item));}
  @org.springframework.transaction.annotation.Transactional
  public ManualItem patch(CurrentUser user,UUID entity,UUID id,Map<String,Object> fields){
    access.requireWrite(user,entity);var old=repository.manualForUpdate(entity,id).orElseThrow(ForecastValidation::missing);
    var p=new ForecastPatch(fields,Set.of("direction","category","description","amount","recurrence","dayOfMonth","startMonth","endMonth","active"));
    return repository.saveManual(entity,actor(user),validated(id,new ManualItem(id,p.text("direction",old.direction()),p.text("category",old.category()),p.text("description",old.description()),p.money("amount",old.amount()),p.text("recurrence",old.recurrence()),p.day("dayOfMonth",old.dayOfMonth()),p.month("startMonth",old.startMonth()),p.month("endMonth",old.endMonth()),p.active(old.active()))));
  }
  @org.springframework.transaction.annotation.Transactional
  public ManualItem deactivate(CurrentUser user,UUID entity,UUID id){access.requireWrite(user,entity);var i=repository.manualForUpdate(entity,id).orElseThrow(ForecastValidation::missing);
    return repository.saveManual(entity,actor(user),new ManualItem(i.id(),i.direction(),i.category(),i.description(),i.amount(),i.recurrence(),i.dayOfMonth(),i.startMonth(),i.endMonth(),false));}
  private static UUID actor(CurrentUser user){return UUID.fromString(user.subject());}
  private static ManualItem validated(UUID id,ManualItem i){
    if(i==null)throw ForecastValidation.invalid("item","Enter the manual item.");
    if(!Set.of("IN","OUT").contains(i.direction()==null?"":i.direction()))throw ForecastValidation.invalid("direction","Choose receipts or payments.");
    if(!CATEGORIES.contains(i.category()==null?"":i.category()))throw ForecastValidation.invalid("category","Choose a supported category.");
    if(!Set.of("ONE_OFF","MONTHLY").contains(i.recurrence()==null?"":i.recurrence()))throw ForecastValidation.invalid("recurrence","Choose one-off or monthly.");
    var description=ForecastValidation.text("description",i.description(),200);var amount=ForecastValidation.money("amount",i.amount(),true);
    ForecastValidation.months(i.startMonth(),i.endMonth());ForecastValidation.day("dayOfMonth",i.dayOfMonth());
    return new ManualItem(id,i.direction(),i.category(),description,amount,i.recurrence(),i.dayOfMonth(),i.startMonth(),i.endMonth(),i.active());
  }
}
