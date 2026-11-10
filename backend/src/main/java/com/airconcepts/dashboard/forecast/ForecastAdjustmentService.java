package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.Split;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.web.ApiFailure;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
@Service
public class ForecastAdjustmentService {
  public static class AdjustmentChange {
    private LocalDate expectedDateOverride;private boolean dateSupplied;private Boolean excluded;private String exclusionReason;
    public LocalDate getExpectedDateOverride(){return expectedDateOverride;}
    @JsonSetter public void setExpectedDateOverride(LocalDate value){dateSupplied=true;expectedDateOverride=value;}
    @JsonIgnore public boolean dateSupplied(){return dateSupplied;}
    public Boolean getExcluded(){return excluded;}public void setExcluded(Boolean value){excluded=value;}
    public String getExclusionReason(){return exclusionReason;}public void setExclusionReason(String value){exclusionReason=value;}
  }
  private final ForecastAdjustmentRepository repository;private final ForecastAccess access;
  public ForecastAdjustmentService(ForecastAdjustmentRepository repository,ForecastAccess access){this.repository=repository;this.access=access;}
  public ForecastAdjustmentRepository.AdjustmentState adjust(CurrentUser user,UUID entity,UUID id,AdjustmentChange change){
    access.requireWrite(user,entity);
    if(Boolean.TRUE.equals(change.getExcluded())&&(change.getExclusionReason()==null||change.getExclusionReason().isBlank()||change.getExclusionReason().strip().length()>500))
      throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_EXCLUSION_REASON_REQUIRED","Enter an exclusion reason of 1–500 characters.");
    outstanding(repository.document(entity,id));return repository.adjust(entity,id,UUID.fromString(user.subject()),change);
  }
  public ForecastAdjustmentRepository.AdjustmentState splits(CurrentUser user,UUID entity,UUID id,List<Split> lines){
    access.requireWrite(user,entity);var document=repository.document(entity,id);outstanding(document);validateSplits(lines,document.amountDue());
    return repository.splits(entity,id,UUID.fromString(user.subject()),lines);
  }
  public ForecastAdjustmentRepository.AdjustmentState conflict(CurrentUser user,UUID entity,UUID id,String resolution){
    access.requireWrite(user,entity);
    if(!Set.of("KEEP_SCHEDULE","DISCARD_SCHEDULE").contains(resolution==null?"":resolution))throw ForecastValidation.invalid("resolution","Choose keep or discard schedule.");
    outstanding(repository.document(entity,id));return repository.resolve(entity,id,UUID.fromString(user.subject()),resolution);
  }
  static void outstanding(ForecastAdjustmentRepository.DocumentState document){if(!document.outstanding())throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_DOCUMENT_NOT_OUTSTANDING","This document is no longer outstanding. Refresh its details.");}
  static void validateSplits(List<Split> lines,BigDecimal amount){
    if(lines==null||lines.size()>32767)throw ForecastValidation.invalid("lines","Provide a bounded split schedule, or an empty list to remove it.");
    BigDecimal sum=BigDecimal.ZERO;
    for(var line:lines){if(line==null||line.expectedDate()==null)throw ForecastValidation.invalid("lines","Every split needs a date and positive amount.");sum=sum.add(ForecastValidation.money("amount",line.amount(),true));}
    if(!lines.isEmpty()&&sum.compareTo(amount)!=0)throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_SPLIT_SUM_MISMATCH","Split amounts must add up to the outstanding balance.");
  }
}
